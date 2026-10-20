# PRD：工作台压暗层渲染在 UI 面板前方

- **任务**：`.trellis/tasks/10-07-workbench-backdrop-layer-order/`
- **状态**：planning
- **报告来源**：用户真机实测（2026-10-07），「幕布的暗色 layer 视觉上在视频列表前方」
- **已确认非回归**：`app/` 在 `af39a78..HEAD` 之间**逐字节相同**，`presentationPolicies` 与
  `PlaybackCanvasContracts.kt` 未改动 → 与 `10-07-immersive-ui-theme-runtime-assessment` 的 Phase 1 **无关**。

## 1. 现象

工作台呼出后，`StageBackdrop`（黑色 alpha 0.42 压暗层）**渲染在中心内容面板（视频列表）之前**。
按设计它应该压暗 MediaStage 的视频，而**不应覆盖** UI 面板。

## 2. 根因（已由 SDK 字节码确证）

用户不传 `rendering` 时，`UIPanelSettings` 用的 `UIPanelRenderOptions()` **默认是 compositor layer**：

```text
javap -c com.meta.spatial.toolkit.UIPanelRenderOptions
  no-arg ctor -> synthetic ctor(renderMode=null, mask=1, null)
  synthetic ctor: mask&1 -> new PanelRenderMode$Layer(...)      # ← 默认是 Layer
  applyTo(PanelConfigOptions):
     renderMode instanceof PanelRenderMode$Mesh  -> setLayerConfig(null)   # 纯网格，参与深度测试
     renderMode instanceof PanelRenderMode$Layer -> new LayerConfig(...); setLayerConfig(...)
```

因此**每一个不显式传 `rendering = UIPanelRenderOptions(PanelRenderMode.Mesh())` 的 UI 面板都是
compositor layer**：`controls_id` / `video_selector_panel` / `mr_panel` / `mode_panel` /
`center_content_panel` / `input_method_panel` / `stage_backdrop_panel` / `danmaku_overlay_panel` /
`grab_bar_panel`（视频另经 `MediaPanelRenderOptions`，也是 layer）。

而项目自己的注释已经写明了 layer 的绘制语义（`SpatialVideoSampleActivity.kt:2950`）：

> A compositor layer is drawn over scene geometry, so the video covers whatever it overlaps however
> far in front that content sits

**结论**：backdrop 与中心面板都是 layer，它们的先后**由 compositor 决定，而不是由世界 Z 决定**。
backdrop 位于 stage-local z = **+0.01**（`:175`），中心面板 z = **−0.52**（`WorkbenchLayoutConfig.centerLocalZ`），
中心面板明明近 53 cm —— 但世界 Z 对两个 layer 之间的排序不起作用。这就是"压暗层在列表前方"。

这也**证伪**了同文件 `:2943-2960` 的结论「The video is the ONLY compositor layer in the scene」：
该结论只检查了 `MediaPanelRenderOptions` 与 `QuadShapeOptions`，**没有检查 `UIPanelSettings` 的默认值**。
它同时解释了为什么早先几个实验"改了没用"——`enableLayer=false`、`MediaPanelRenderOptions(zIndex=…)`
都只动了视频那一个 layer，UI 面板之间的 layer 次序从未被触及。

## 3. 未确证项（不得当作已知）

| 问题 | 状态 |
| --- | --- |
| `LayerConfig.zIndex` 是否真的生效 | **未记录**。`VIDEO_LAYER_Z_INDEX = -1`（`:2960`）是标注为 EXPERIMENT 的尝试，其效果没有留下结论 |
| layer 之间的默认次序是否为创建顺序 | **未确证**。backdrop 由 `createStageBackdropPanel()`（`:1108`）运行时创建，中心面板由 GLXF 场景**异步** inflate（`loadGLXF` `:1002`），两者存在时序关系，理论上可能**间歇性**复现 |
| 压暗层是否真的在压暗视频 | 未知。视频是 layer，mesh 压暗层无法覆盖它 |
| layer 与 mesh 混合时的排序规则 | 未确证 |

## 4. 已决策方案：一次构建 + debug 开关轮换候选（当场判定）

用户 2026-10-07 决定：**不猜 `zIndex` 是否生效，改为一次构建里加入候选轮换开关**，
让一次真机会话就能判定哪个做法真正生效，避免反复打包。

在 `mode_panel.xml` 的调试区（既有 `danmaku_z_button` / `reset_curve_button` 同一区块）新增一个
"Backdrop order" 循环按钮，按下列候选依次切换，并**在 logcat 打印当前候选与效果自检**：

| 候选 | 做法 | 观察点 |
| --- | --- | --- |
| **0 = BASELINE** | 现状（backdrop 默认 layer，不写 zIndex） | 复现"压暗层盖住列表" |
| **1 = ZINDEX** | 在 `toPanelConfigOptions()` 之后写 `layerConfig?.zIndex`，令 视频 < backdrop < UI 面板 | 若候选 1 修好 → **证明 `LayerConfig.zIndex` 生效**（这是当前最缺的一条事实） |
| **2 = BACKDROP_MESH** | backdrop 传 `UIPanelRenderOptions(PanelRenderMode.Mesh())` | 世界 Z 排序恢复；同时观察**压暗是否还对视频生效**（视频是 layer，mesh 无法覆盖它 → 预期压暗失效，用于确证 layer 与 mesh 的关系） |
| **3 = ALL_UI_MESH** | 所有 `UIPanelSettings` 面板统一传 `Mesh()` | 用于确证 `:2950` 关于"layer 覆盖场景几何"的论断是否反向成立（预期视频 layer 压住全部 UI） |

要求：
- 开关**默认 = 0（BASELINE）**，即默认行为与当前构建**完全一致**，不引入未验证的视觉变化。
- 每个候选切换后**立即 reshape / 重建受影响面板**，无需重启。
- logcat 用固定 tag（建议复用 `WORKBENCH_TRACE_TAG` 或新增 `ViriViriBackdrop`），
  每行包含 `candidate=<n>` 与被写入的实际配置值（`layerConfig?.zIndex` / `renderMode`），
  以便把"我设了什么"和"我看到什么"对上。
- 候选仅存在于 **DEBUG** 构建（`BuildConfig.DEBUG`）。

**判定完成后**：把胜出候选固化为默认、删除其余候选与开关，并在
`docs/ui-structure-and-terminology.md` §5.7 + `docs/research/stage-video-mesh-curvature.md` 记录结论；
**同时修正 `SpatialVideoSampleActivity.kt:2943-2960` 那句被证伪的 "video is the ONLY compositor layer"**。

## 5. 非目标

- 不改 UI / Activity 主流程、不改播放器、不改单一视频 Surface 不变量。
- 不改 `app/scenes` 场景授权（压暗层是运行时动态内容）。
- 不顺手"修"其他面板的视觉，除非同一根因且同一验证轮次可覆盖。
- **默认构建不得出现未验证的视觉变化**（候选 1–3 只能通过 debug 开关激活）。

## 6. 验收

| # | 条件 | 方式 |
| --- | --- | --- |
| A1 | 默认（候选 0）行为与当前构建逐一致，无视觉回归 | **头显** |
| A2 | 一次设备会话内能在 0/1/2/3 之间切换并观察到差异 | **头显** |
| A3 | logcat 能读出当前候选与实际写入的配置值 | **头显** + `adb logcat` |
| A4 | 得出明确结论：哪个候选使压暗层不再覆盖 UI 面板；`LayerConfig.zIndex` 是否生效 | 判定记录 |
| A5 | 压暗层与视频的相对关系被明确记录（能否压暗 layer 视频） | 判定记录 |
| A6 | 单一 `ExoPlayer` / 单一活动视频 Surface 不变量仍成立 | 代码审查 |
| A7 | `.\scripts\build-windows-debug.ps1` 通过 | 命令 |

## 7. 关键文件

- `app/src/main/java/com/m0e_n00b/viriviri/SpatialVideoSampleActivity.kt`
  `:175`（backdrop base Z）、`:796`（zIndex 先例）、`:1426-1445`（backdrop/danmaku 注册）、
  `:1467-1477`（`stageOverlayPanelSettings`）、`:1506-1544`（创建）、`:2612-2645`（reshape）、
  `:2943-2960`（**待修正的错误结论**）
- `docs/research/stage-video-mesh-curvature.md`（Stage 3k 记录）
- `docs/ui-structure-and-terminology.md` §5.7 / §2.2（需同步）
