# 播放舞台交接：曲率、开孔、缩放（2026-10-21）

## 1. 接手时先看这个结论

**当前实现未通过实机验收。用户要求本 session 停止继续改实现，整理后由新 session 修复。**
不要把提交标题里的 fix、210 项测试通过、APK 构建成功或公式相消，解释为视觉问题已修复。

用户最新反馈，指向上一轮交付的独立开孔版：

1. 视频纹理对应的裁切器没有响应曲率变化。
2. 弹幕对应的裁切几何会响应曲率，但伴随 Z 偏移。
3. 缩放仍未正确实现，不能认为视口和幕布组已经等比同步。

这是**实机外观事实**，尚未取得此版每个可见窗口与实体的完整对应关系。
“没有响应曲率”不能直接写成“callback 没执行”；“Z 偏移”不能直接写成“修正值没下发”。
当前没有闭合的最终渲染根因。

此前用户已澄清：共享载体恢复版的弯曲方向正确，但中心随曲率漂移。
早先“窗口在身后”的反馈不能继续作为最新版本方向错误的依据；最新独立版主窗口无曲率响应，
也不能凭此宣布它的方向已单独通过验收。

## 2. 版本与证据归属

| 项目 | 精确身份 | 能证明什么 |
| --- | --- | --- |
| 当前运行时代码提交 | `107bccb`：anchor video aperture at stage surface origin | 源码有独立动态开孔方案；实机已失败 |
| 本交接前 HEAD | `4a78409`：journal | 日志记录，不是另外一个运行时修复 |
| 最近产出 APK 腕部标识 | `DEV be917a7e` | build 时 HEAD；包含随后提交为 107bccb 的工作树代码 |
| 最近 APK SHA256 | `86CF0A5B64AC027E6573C966BFDC970FD0861FFD6451065C0061D30F905871EB` | 产物身份；本次反馈后没有重新核对头显 APK 哈希 |
| Windows 检查 | 210 JVM tests，assembleDebug 通过 | 编译和测试通过，不是视觉重合证明 |
| 已取证的旧 APK | `DEV 802069c7`，代码随后提交为 `d14f93f` | 共享载体恢复版；已用设备 sha256sum 核对 |
| 292 组日志、Inspector 快照 | **旧 DEV 802069c7** | 不属于最新独立开孔版，禁止混用 |

APK 路径：`app/build/outputs/apk/debug/app-debug.apk`。继续工作前重新核对产物身份。

## 3. 当前实际结构，以及弹幕为什么也有 mesh

```text
workbenchRootEntity（移动/抓取锚点）
└─ spatialized_video_panel（舞台根）
   ├─ videoSurfaceEntity / PanelSceneObject
   │  ├─ compositor video layer：视频像素和视频曲率
   │  └─ 圆柱轴原点 bounds mesh：107bccb 中不写颜色/深度
   ├─ PlaybackStageAperture / Named("video_aperture")
   │  └─ 新建普通 SceneObject：表面中心原点的完整视频开孔
   ├─ danmaku_overlay_panel / PanelSceneObject
   │  └─ SDK UI mesh + 透明 compositor layer（默认 Layer 模式）
   └─ stage_backdrop_panel / PanelSceneObject
      └─ SDK UI mesh + 压暗 layer，跟随工作台可见性
```

弹幕的几何来自当前 SDK UI panel 渲染路径，不是用户要求的第二块业务裁切窗口。
默认 `uiPanelRenderOptions()` 使用 `PanelRenderMode.Layer(ALPHA_BLEND)`，SDK 会生成场景几何来
配合 layer 与主场景缓冲的合成；调试候选也能将 UI 改为 Mesh 模式。这两种模式要分别确认。

**弹幕应只叠加文字及其透明区域，不应该控制视频能否看见，不应该产生额外的黑色视口。**
历史上“只有弹幕字形剪影内能看到视频”是故障，不是合理设计。应区分：

- 视频完整画面的开孔/显示边界；
- 弹幕 panel 的 SDK 几何及纹理 alpha；
- 工作台压暗层的半透明内容。

不能把三个对象都称为“裁切器”再给它们共用一套未经核对的原点假设。
也不能在没有验证当前 Layer 合成方式时，一概宣称所有 SDK 开孔都必须保留或都可以删除。
当前黑窗具体来自哪个对象，仍需用最终显示证据对应。

### 107bccb 的实现分工（这是代码事实，不是已通过的方案）

- `videoPanelConfig().sceneMeshCreator` 中更新 `PlaybackStageAperture.reshape()`，并返回轴载体 mesh。
- 视频轴载体继续 `baseLocalZ - effectiveRadius`；其 bounds mesh 使用轴原点，但材质为 OPAQUE/unlit，
  `setColorWrite(0)`、`DepthWrite.DISABLE`，意图是不再产生第二个视频孔。
- 独立开孔顶点为 `r*(cos(theta)-1)`，中心 Z=0；载体只使用已缩放的 video base。
- `PlaybackStagePanelRenderer` 完成三层 reshape/pose 后，再提交独立开孔 pose。
- 弹幕/压暗层仍通过各自 SDK panel 的 reshape 与轴原点补偿更新。
- 新开孔跟随视频 Visible，无独立输入/Surface；NoCollision；Activity 销毁时释放。

**新开孔与 SDK UI panel 的更新路径已经不对称。** 最新反馈正是“视频孔不动、弹幕孔会动”。
应首先查实际调用、对象身份、mesh 替换与最终显示，不要只继续测试纯数学生成器。
独立开孔只是本轮尝试，不是用户批准必须保留的架构；可以在证据支持下撤销/替换。

## 4. 已知错误实现与失败尝试

| 实现/尝试 | 问题或结果 | 后续必须记住 |
| --- | --- | --- |
| 旧自建曲面 `r*(1-cos(x/r))` | 旧居中曲面与视频所需相对卷曲方向相反 | 居中曲面方向与圆柱轴原点补偿是两件事 |
| 只改宽高，未整体派生半径/层间距 | 曾有整体相似缩放契约缺口 | s 必须贯穿宽高、有效半径和局部偏移；当前视觉缩放仍未通过 |
| “统一控制器 + 同批提交” | 提交路径统一了，但实机仍不同步/漂移 | 调用参数相同不等于最终网格、layer、显示位置相同 |
| 按固定 1920×1080 输出推算 FIT 黑边，再生成 crop | 曾将 4:3/竖屏内容错误裁切、拉伸 | 直接 decoder Surface 没有被证明含这种 FIT 黑边 |
| 弹幕宽度强行跟随视频比例缩放 | 竖屏下文字被横向挤瘦 | 用户倾向方案 A：弹幕保持全舞台，不随视频比例缩窄 |
| 原生视频 onClick 与几何按下沿共存 | 存在两个舞台主操作入口；诊断 LineTest 重新使旧路径可达 | NoCollision、零 clickButtons 之外，还要移除遗留 native listener |
| 打开碰撞探针的诊断版 | 用户报告扳机回归；没有取得足以闭合问题的稳定命中证据 | 不再靠恢复输入碰撞来取证；探针已撤回 |
| 恢复显式轴原点开孔，并断言 +r 与 -r 抵消 | 209 测试通过，但中心仍随曲率漂移 | 数组和 Pose 的相消测试不是最终画面验证 |
| 独立表面原点开孔（107bccb） | 210 测试通过；最新实机反馈主开孔不随曲率、弹幕孔漂移、缩放仍错 | 这是验收失败的新增结构，不能当成修复基线 |
| 用 zIndex 调整空间/裁切关系 | zIndex 只排序 compositor layers | 不能修曲率、位置、尺寸，也不会自动创建“幕布合成组” |
| 根据负的 native bounds 直接翻 API 补偿符号 | 混淆 API/原生坐标；没有最终显示依据 | 先标明坐标空间；不要根据症状再试正负号 |

已修正但不能丢失的源码改动：删除视频原生 onClick；所有视频/开孔路径保持 NoCollision；
移除 MR/VR 切换时重新发布视频 ISDK 尺寸的旧入口。最新回合没有单独完成扳机实机验收。

## 5. 必须保留的已验收效果

- **4:3 / 竖屏完整帧纹理映射已由用户验收通过。** 修复提交 `8b3fad5`。
- 物理视频 shape 使用有效视频比例；`videoTextureCrop = Crop.NONE`，完整 UV [0,1]。
- 不要为了回滚几何试验，把该纹理修复一并回滚。
- 保留既有播放器、SDK decoder Surface 和生命周期归属，不借本问题重建播放器/Surface。
- 弹幕方案 A：完整 16:9 舞台范围；视频比例变化不能挤压字体横向比例。
- 几何舞台主操作只有一个按下沿入口；松开扳机不能再次切换工作台。

## 6. 预期效果与验收条件

这是产品行为约束，不规定必须保留当前“独立开孔”或某个固定符号。

| 操作/场景 | 必须观察到的结果 |
| --- | --- |
| 平面 ↔ 曲面、改变半径 | 视频显示边界与对应可见开孔同步变化；曲面中央点留在原舞台位置，只允许弧边随曲率变化 |
| 改变整体倍率 s | 视频、开孔、弹幕容器、压暗层按同一基准同步等比变化，不出现旧尺寸视口 |
| 改变视频比例 | 16:9/4:3/竖屏的完整帧比例正确；视频范围内的开孔匹配其边界，弹幕保持全舞台 |
| 关闭工作台、没有弹幕 | 视频仍能完整独立显示，不依赖字形或其他 UI 的开孔 |
| 打开工作台 | 压暗层与 UI 正确覆盖；没有另一个错位黑窗或网格窗口决定视频是否可见 |
| Cozy 环境 / MR | 显示、遮挡关系符合舞台内容优先于环境的预期；没有错误裁掉环境的独立窗口 |
| 旋转、拖动舞台后调参 | 同样满足局部中央点固定与边界匹配，不能只在初始姿态下成立 |
| 扳机按下、保持、松开 | 一次按下最多一次舞台主操作；保持、松开不再次切换 |
| 松开摇杆并等待 | 最终稳定画面仍重合，不能用“提交暂态”解释持续错位 |

整体缩放契约：宽高 `W0*s, H0*s`；有效半径 `r0*s`；原始半径仍用于控制/保存。
如果使用圆柱轴原点，载体补偿要按 `(z0 + c(r0))*s` 绝对重算；现有 API 轴原点实现 c=-r0。
如果使用表面中心原点，不能再机械加入轴原点的一整个半径。用户说的“原始 Z + 修正值再乘倍率”
是要求正确的变换关系，不是要求所有类型 mesh 的 localZ 必须相同。
20/15 米例子不能擅自解释为更改控制/持久化范围；不得用 fromRadius 再归一化已缩放半径。

缩放保持哪个 Y 锚点还需与用户约定：当前围绕底部抓取条更新舞台高度，会带来中心 Y 变化。
这项既有锚点行为不能被拿来解释曲率导致的中央 Z 漂移；也不能在修 Z 时偷偷改成别的缩放锚点。

层级目标是逻辑上的“视频、弹幕、字幕、工作台覆盖内容”共同显示于环境之前。
当前各 panel 分别有 layerConfig；VIDEO=1、BACKDROP=2、其他 UI=3；字幕还没有独立舞台运行时层。
同一个 ECS 父节点不会自动生成离屏合成组；同为 zIndex 3 也不等于已有严格组内顺序。

## 7. 取证所得与边界

旧 DEV 802069c7 的 292 组日志：三层都有 scale/effectiveRadius，carrier 公式与 native 局部 mesh bounds
数值相符，误差约 0.000003m 以内。随后只读 Inspector 显示补偿保留在 ECS，舞台/锚点无 ECS Scale 覆盖。
这排除了**该版本该次复现中的参数漏传、持续 ECS 补偿丢失**；没有证明：

- 最新 107bccb 的独立 mesh creator 真正在操作时更新了开孔；
- 最终 native SceneObject 世界姿态、GPU 显示位置与 compositor 已重合；
- 用户看到的所有黑窗都已对应到确定对象；
- native 局部负 Z 能直接与 API submittedWorldZ 相加。

Kotlin Pose/Quaternion、原生 Z 反射、轴原点等核验保存在坐标文档和 research。
它们是锁定 SDK 0.13.2 的局部契约证据，不能代替完整渲染链验收；不要凭既有文档的“已验证”字样跳过边界检查。

证据位于 `.trellis/tasks/10-21-aperture-transform-drift/research/`：
`reproduction-analysis.md`、`reproduced-stage-20261021.log`、`settled-stage-inspector.json`。
Inspector 可在应用运行且可达时从现有 8011 端口接收数据；此前未发送修改命令。

## 8. 继续时的范围与入口

先建立“哪个可见边界属于哪个对象、哪条更新路径真正生效”的最小复现，再选改动。
优先直接读以下调用链，避免再次大范围重构后才确认对象：

1. `PlaybackStageController.kt`：参数归一化、有效几何、revision/readiness。
2. `SpatialVideoSampleActivity.kt`：videoPanelConfig、sceneMeshCreator、overlay settings、图层策略。
3. `PlaybackStagePanelRenderer.kt`：reshape、pose 提交，以及新增独立开孔回调。
4. `PlaybackStageAperture.kt` / `PlaybackStageApertureMesh.kt`：独立对象、mesh 替换、原点和写入开关。
5. `PlaybackStageGeometry.kt` / 对应测试：比例、尺寸、Crop.NONE。
6. `AnalogMediaStageTuningSystem.kt`：保留唯一按下沿，不能把调试恢复成第二输入入口。

以上代码均在 `app/src/main/java/com/m0e_n00b/viriviri/`；测试在对应 `app/src/test/` 包。
历史参考：`064219b` 的几何舞台输入、`3f13a1c` 的旧自建网格、`130967e` 的 SDK 网格恢复、
`8b3fad5` 的完整帧纹理、`a42f117` 的相似缩放、`28a6bb1` 的已撤回碰撞探针、`d14f93f` 的共享载体恢复。
这些提交用于找证据，不代表各自整体可以直接回滚/复用。

本轮教训：

- 同一 bug 多次验收失败后，必须承认未闭合根因；不能用下一种数学上等价的结构替代根因证据。
- 分清产品规格、当前代码、修复假设、单测结果、头显验收五类信息。
- “已统一参数”“已提交”“已在 ECS 保留”“最终渲染正确”是不同层级的证据。
- 测试不能只证明自己写的公式；必须覆盖真实入口、对象替换和更新生效，再做可见边界验收。
- 查旧代码时同时看验收记录；历史上“暂时认为正确”曾被后续实机推翻。
- 调试必须保持已解决的输入和纹理契约，不能为测渲染重新打开输入故障。
- 一个 coherent patch 覆盖必需边界后再做 Windows 验证；不把测试通过写成问题修复完成。

任务 `.trellis/tasks/10-21-aperture-transform-drift` 保持 in_progress，**不归档、不标记修复完成**。
遵守 AGENTS.md：只有当前回合用户明确要求时才安装/启动/写设备；此次只做文档交接。
工作区另有用户所有的 Penpot、diagram-design skill、原型提示等修改/未跟踪文件，禁止 git add -A 或覆盖。

## 9. 新 session 可直接使用的开场提示

> 阅读 docs/playback-stage-handoff-2026-10-21.md、AGENTS.md 和当前 Trellis task 后继续修复。
> 最新独立开孔版 107bccb 实机失败：视频开孔不响应曲率，弹幕几何响应但 Z 漂移，缩放仍不同步。
> 先确认可见窗口的实际对象和参数更新生效路径，找真实原因，不再凭正负号和公式相消改代码。
> 保留已验收的 4:3/竖屏完整帧映射、全舞台弹幕和唯一扳机按下沿；旧版本证据不能代替新版本验收。
