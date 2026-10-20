# 排查任务：cozy(VR) 与透视(MR) 下"监听器行为不同"

> 交接文档。写于 2026-10-20，由上一轮 session 在连续 9 小时后产出。
> 目标读者：接手的新 session（无需读旧对话即可开工）。
> 项目：`D:\Workspace\unity\viriviri`（Meta Spatial SDK 0.13.2 / Quest 2 / Horizon OS = Android 14, SDK 34）

---

## 1. 要解决的问题（按优先级）

### P1（用户已确认的硬事实）

**同一次摇杆操作，在 cozy（VR、环境可见）与透视（MR、passthrough）下表现不同。**
用户原话：**"两种模式监听器确认行为不同"** —— 这不是视觉参照物的问题，是**监听器层面**确实不同。
上一轮已排除"应用侧输入逻辑分支"的可能（见 §3.1），所以差异只可能出现在：
**面板/场景对象的输入监听器注册与路由**，或 **ISDK 在模式切换时对交互属性的重算**。

### P2（已定位一半）

**视频在 cozy 下被画成"横向窄带 + 左右黑边"，上下被环境几何覆盖。**
上一轮用诊断开关确认了元凶：**视频被画了两遍**。

- 合成层（`MediaPanelRenderOptions` 强制 `LayerConfig`）：弧面落在舞台平面
- 手写网格（`createVideoSceneMesh`，`SceneMaterial(texture, TRANSLUCENT, "spatial/reflect")`）：挂在**被曲率 offset 移动过的实体**上，半径 r 时整块位于舞台前方 r 米

验证方式与结果：
```
.\gradlew.bat :app:assembleDebug "-Pviriviri.noVideoMesh=true"   # 关掉手写网格，视频只由合成层画
# → 窄带 + 黑边消失，视频正常
```
**但这是诊断开关，默认 `false`，干净包仍带 P2 bug。** 收尾方案见 §6。

---

## 2. 关键文件与坐标

| 作用 | 位置 |
| --- | --- |
| 主 Activity（约 3500 行，本任务几乎全部改动都在这里） | `app/src/main/java/com/m0e_n00b/viriviri/SpatialVideoSampleActivity.kt` |
| 模式切换 | `setMrMode(isMrMode: Boolean)`，约 `:3316` |
| 视频面板 InputListener 注册 | `:1530` 起（`onClick` → `onStagePrimaryAction()`，`onInput` 返回 `false`） |
| **另一处 `addListener(...)`，上一轮没读完** | `:2232` ← **优先读这里** |
| 视频面板创建 | `createVideoPanel()`，约 `:1452` |
| 视频面板 reshape | `reshapeSpatialVideoPanel()`，约 `:2900` |
| 手写网格构建 | `createVideoSceneMesh()`，约 `:1077` |
| 曲率 offset 补偿 | `applyStageLayerCurvatureOffset()`，约 `:3050` |
| 曲率应用（已加幂等重放） | `applyPlaybackVideoCurvature()`，约 `:2612` |
| 模拟输入系统（无模式分支） | `AnalogMediaStageTuningSystem.kt` / `AnalogWorkbenchSummonSystem.kt` |
| 注入驱动（默认构建不生效） | `DebugInputBridge.kt` |

SDK 字节码位置（需要重新解包时）：
```
%USERPROFILE%\.gradle\caches\modules-2\files-2.1\com.meta.spatial\meta-spatial-sdk\0.13.2\...\*.aar
%USERPROFILE%\.gradle\caches\modules-2\files-2.1\com.meta.spatial\meta-spatial-sdk-toolkit\0.13.2\...\*.aar
```

---

## 3. 已确认的事实（可直接采信，避免重复劳动）

### 3.1 应用侧输入逻辑**没有**模式分支

```
grep 'inMrMode|isMrMode|passthrough' 于 AnalogMediaStageTuningSystem.kt / AnalogWorkbenchSummonSystem.kt
→ 零命中
grep 'inMrMode' 于全项目
→ 只出现在 SpatialAudioSystem.kt:80（音频）与 Activity 的赋值处
```
两条链路完全相同：
```
摇杆 → onScaleDelta / onCurvatureDelta → adjustPlaybackStageScale / adjustPlaybackCurvature
```

### 3.2 `setMrMode()` 的全部副作用（这是唯一可能造成差异的地方）

```kotlin
environmentGLXF?.setComponent(Visible(!isMrMode))
skydome?.setComponent(Visible(!isMrMode))
panel?.updateIsdkComponentProperties(videoPanelEntity)   // ← 最可疑
scene.enablePassthrough(isMrMode)
avatarSystem.setShowControllers(!isMrMode)
avatarSystem.setShowHands(!isMrMode)
inMrMode = isMrMode
```

### 3.3 视频被画两遍（P2 根因，已验证）

`createVideoSceneMesh` 的材质区间由 `videoMeshMaterialIndexRanges(surfaceIndices.size / 2)` 切分：
- material 0：视频纹理（**这一半是多余的**，合成层已经在画）
- material 1：底部投影

代码注释自己写的是 "drop shadow + hole punch"，即内容四边形本该是**被挖掉的洞**，实际却真的在绘制。

### 3.4 面板显示层（输入相关，很重要）

| displayId | 尺寸 | touch | 在 `mViewports` | 面板 | 注册方式 |
| --- | --- | --- | --- | --- | --- |
| 300 | 1725×495 | **NONE** | ❌ | `controls_id` | LayoutXML |
| 295–299 | 各异 | **NONE** | ❌ | center/danmaku/backdrop/left rail | ComposeView |
| 301 | 1950×480 | **VIRTUAL** | ✅ | `mr_panel` | IntentPanel |
| 302 | 1805×2320 | **VIRTUAL** | ✅ | `video_selector_panel` | IntentPanel |

推断：**View 类面板在系统输入层没有 viewport**，因此 SDK 的反射注入无法产生真实 MotionEvent。
（实测：`onInput` 返回 `true`，但 `View.isPressed` 仍为 `false`，`isPlaying` 不变。）

### 3.5 环境几何遮挡：**上一轮的结论不可信，需重算**

做过射线遮挡测试（从眼睛到舞台矩形 15×9 采样点，对 MediaRoom mesh 求交）：
- 平面：0/117 被挡
- 弧面（含边缘 16.6 cm 凸起）：0/135 被挡

**但这次测试用的是旧舞台位姿假设**（eye `(0,1.6,0)`、舞台 `(0,1.79,2.0)`），而调试面板显示 **Stage Y 已被调到 0.63 m**，舞台还可能被抓取移动过。
→ 若要重做，**先读 `ViriViriTarget` 日志里的 `centre=(cx,cy,cz)` 与 `half=(hx,hy)`**，用真实位姿再算。

MediaRoom 网格参考（glTF 内嵌 base64，`MediaRoom.gltf` mesh[2]）：
```
6248 verts / 6112 tris, bbox min (-3.39,-0.20,-2.38) max (3.39,3.51,5.43)
```

---

## 4. 建议的排查顺序（我未完成的下一步）

1. **读 `:2232` 的 `addListener(...)`** —— 上一轮只读到注册位置，没读内容。这是最可能藏着"模式相关监听器"的地方。
2. **看 `updateIsdkComponentProperties` 在做什么** —— `com.meta.spatial.isdk.updateIsdkComponentProperties`，用 `javap -c` 看它改了哪些 ISDK 组件属性（可交互性？grab 状态？碰撞？）。`setMrMode` 每次切模式都调它，是"监听器行为不同"的头号嫌疑。
3. **在 `setMrMode` 前后各打一条日志**，打印：场景对象数量、视频面板的 input listener 数量/ID、`IsdkGrabbable`/碰撞组件是否存在、面板 `PanelConfigOptions` 的 `clickButtons`。切换模式对比两次输出。
4. **验证 `scene.enablePassthrough()` 对合成的影响** —— 环境混合模式 `OPAQUE`(VR) vs `ALPHA_BLEND`(MR) 会改变圆柱层的叠合方式；用 Metro/文档确认 `enablePassthrough` 是否还改了别的（如 `ReferenceSpace`、layer 排序）。

---

## 5. 设备与命令

```
设备: 192.168.0.234:5555 (WiFi adb，易掉线；掉了就 adb connect 重连)
已 root: Magisk，`su -c id` → uid=0(root)（上一轮已放行 shell）
```

**构建**（PowerShell 里 `-P` 参数**必须加引号**，否则会在点号处被拆成两个参数）：
```powershell
# 默认构建 = 人工验证包（注入关闭）
.\scripts\build-windows-debug.ps1

# 诊断：关掉手写视频网格（验证 P2）
.\gradlew.bat :app:assembleDebug "-Pviriviri.noVideoMesh=true"

# 自动化：启用输入注入（仅自己用，绝不给人工验证）
.\gradlew.bat :app:assembleDebug "-Pviriviri.inputInjection=true"
```

**截图**（单眼 crop 是用户给的经验值）：
```powershell
adb -s 192.168.0.234:5555 exec-out screencap -p > full.png   # 3664x1920 立体帧
# 单眼区域: crop W=1600 H=900 X=2017 Y=510
```

**日志 tag**：`ViriViriAspect`（曲率/reshape）、`ViriViriCurve`（恢复的曲率）、`ViriViriBackdrop`（层序）、`ViriViriInjection`（注入）、`ViriViriTarget`（瞄准/舞台位姿）、`ViriViriWorkbench`（工作台）

**注入驱动用法**（仅在 `-Pviriviri.inputInjection=true` 的包里可用）：
```powershell
adb shell am broadcast -a com.m0e_n00b.viriviri.DEBUG_CLICK `
    --ei panel 2131361921 --ei view 2131362173 --ei mask 49
# panel=controls_id  view=play_pause_button  mask=A|TriggerL|TriggerR
# 需要真实右手柄活跃；无活跃手柄时驱动会拒绝注入（防止抢 hover）
```

---

## 6. P2 的收尾方案（已落 B；A 仅作诊断开关）

- **A（仅诊断）**：`-Pviriviri.noVideoMesh=true` 暂时省略裁切/阴影网格。它确认了旧网格重复绘制视频的故障，但同时会移除 cutter 的 hole-punch 和曲率顶点修正，因此不能作为最终层同步方案。
- **B（已落地 2026-10-20）**：默认启用 `createVideoSceneMesh`，但材质索引只保留正面 hole-punch 与底部投影；删除重复绘制视频纹理的反向三角形材质。Compositor 是视频像素唯一 owner，裁切网格仍跟随同一内容矩形，并在每次 reshape 时将 `-surfaceAnchorOffsetMeters()` 的反向修正烘焙进顶点。
- 视频、幕布、弹幕的 shape 全部使用同一个 aspect-fitted `SpatialVideoContentQuad`（不是视频用裁切矩形、幕布用未裁切舞台框）；所有 carrier 的局部 Z 都由 `curvature.entityLocalZForSurface(baseLocalZ)` 绝对重算，半径改变不累积、不漏应用。
- reshape 之前旧流程只在状态边沿更新位置，且 overlay/full-stage 尺寸与视频/content-quad 尺寸分开计算。现由同一 geometry 快照驱动 reshape，并在视频 panel reshape 后重申 carrier correction。

---

## 7. 硬约束（务必遵守）

1. **不要安装 / 启动 APK，除非用户在当前轮明确要求。** 只读检查（`device list`、`logcat`、`dumpsys`、`screencap`）不受限。
2. **人工验证包不得接管输入。** 注入由构建期开关 `INPUT_INJECTION_ENABLED` 控制，默认 `false`；
   只有 `-Pviriviri.inputInjection=true` 的包才有注入路径。**不要把带注入的包留给用户手测。**
3. **静态场景摆位属于 Meta Spatial Editor**（`app/scenes`），不要为了修布局在 Kotlin 里硬编码实体。
4. 一个用户交互只能有一个 owner（不要同时让 stage click、outer-dismiss、panel callback 都去切同一个状态）。
5. `git commit` 用 `-F` 时第一行必须是 subject；**不要 `--amend`，不要 push**。
6. 改动完要同步 `docs/ui-structure-and-terminology.md`（改面板/槽位/状态机/布局常量时强制）。

---

## 8. 仓库现状（接手时先确认）

- `HEAD = 027d69a2`，**大量未提交改动**，上一轮全部工作都没提交。
- 已修改（未提交）：
  - `SpatialVideoSampleActivity.kt`（曲率 offset 幂等重放、层序启动应用、导航 pitch、注入接入、`NO_VIDEO_MESH` gate）
  - `WorkbenchLayoutConfig.kt`（`navPitchDegrees: +8f → -40f`）
  - `DebugInputBridge.kt`（新增）
  - `app/build.gradle.kts`（`INPUT_INJECTION_ENABLED` / `NO_VIDEO_MESH` 两个 buildConfigField）
  - 10-15 那批组件解耦：`DanmakuOverlay.kt`、`ImmersiveCenterContentPanel.kt`、`ImmersiveInputMethodPanel.kt`、`ImmersiveLeftPanel.kt`、`MoviePanel.kt`、`RecommendationUi.kt`、`AppComponentCatalogue.kt`(新)、`LibraryComponents.kt`(新)
  - `docs/ui-structure-and-terminology.md`
- 测试：**core 128 / app 186，全绿**；`git diff --check` 干净。

### 上一轮已验证修好的（别回退）

| 问题 | 修复 |
| --- | --- |
| 曲率 offset 在实体就绪前被跳过且永不重试 → 视频朝观众飘 | `applyPlaybackVideoCurvature` 改为幂等重放 offset |
| 视频层压在 UI 上 / 环境遮挡 | 层序 `video=1 < backdrop=2 < UI=3`，且改为**启动时**真正写入（原先只在调试按钮时写） |
| 弹幕不刷新 | 宿主 `setContent` 补 `collectAsState()`（重构把观测移走却没补上） |
| 顶部导航 search 点不到 | `navPitchDegrees: +8f → -40f`（原来"后仰、法线朝上"） |
| 注入抢走真实手柄 hover | 用真实右手柄实体作 source；无活跃手柄时拒绝注入 |

### 已知的坑（别重复踩）

- `Quaternion(x,y,z)` **吃角度**（内部 `Math.toRadians(x*0.5)`），不是弧度。
- `PanelSceneObject.getRootView()` / `display()` 在 Kotlin 里是**属性**（`rootView` / `display`）。
- `registerReceiver` 在 SDK 34 上必须传 `Context.RECEIVER_EXPORTED`。
- `Player 的 isPressed` 是**上升沿**，`isDown` 才是电平。
- 350 MB 的历史 APK 是**累积残留**（clean 构建只有 ~168 MB）。
- `logcat -d` 只返回缓冲尾部，**不要**用"某 tag 0 条"推断"代码没跑"。

---

## 9. 一句话起点

> 从 `SpatialVideoSampleActivity.kt:2232` 的 `addListener(...)` 和 `updateIsdkComponentProperties` 入手，
> 找出 cozy 与透视下**输入监听器注册/路由**的差异；同时决定 P2 用方案 A 还是 B 收尾。
