# 沉浸式幕布 scale 烘焙 + 每层独立曲率
## 目标

分阶段落地（每阶段可独立验收）：

1. 修复 k17：幕布启动时不加载持久化 scale。
2. 接入 k10：视频幕布可调圆柱曲率（已就位的 `CurvedStageMesh.kt` 未接线）。
3. 解耦：视频 / 弹幕层 / backdrop 各自独立曲率与姿态，与视频解耦；并把单弹幕平面
   泛化为「平面列表」接口，为后续多层平行弹幕 + 左右分组 + fanout 分派铺路。

## 解耦原则

- scale 共享：一个 `playbackStageScale` 同步缩放视频、弹幕、backdrop（沿用现状，k17 预期）。
- 曲率/姿态每层独立：视频 mesh、弹幕层、backdrop 各有自己的曲率，改一个不影响其他，
  默认全 `Flat`、姿态平行（现状行为）。

## 阶段划分（每阶段验收一次）

- 阶段 1：数据/偏好层 —— `PlaybackStageCurvature` + 三组曲率持久化 + `ViriViriAppState` 三字段与 setter + 单测。
- 阶段 2：k17 修复 —— scale 烘焙进视频 mesh / 弹幕 / backdrop shape + grab bar 数学 + 统一重建入口，移除实体 `Scale`。
- 阶段 3：曲面 —— 视频曲率接入 `CurvedStageMeshBuilder` + 弹幕/backdrop 各自独立曲率。
- 阶段 4：多平面基底 —— `DanmakuPlaneSpec` + 平面列表化 + debug 曲率 UI。

## 根因（已核实，别重复验证）

幕布是手动 `PanelSceneObject`（`createVideoPanel()` L781），其 `sceneMeshCreator`（L796）
顶点写死 `MR_SCREEN_WIDTH/HEIGHT`（未乘 scale）；实体 `Scale` 对手动 mesh 顶点不可靠。
弹幕/backdrop 是标准 Panel，吃实体 `Scale`，所以只有幕布启动时不缩放。

## 关键文件

- `app/src/main/java/com/m0e_n00b/viriviri/SpatialVideoSampleActivity.kt`
- `app/src/main/java/com/m0e_n00b/viriviri/CurvedStageMesh.kt`（未接入，几何生成器）
- `app/src/main/java/com/m0e_n00b/viriviri/AppPreferences.kt`
- `app/src/main/java/com/m0e_n00b/viriviri/ViriViriAppState.kt`
- `app/src/main/java/com/m0e_n00b/viriviri/DanmakuRuntime.kt`（多 canvas 已就绪）
- `app/src/main/java/com/m0e_n00b/viriviri/DanmakuOverlay.kt`

## 后续（补丁 B，本次仅预留接口）

- 多平面组：`DanmakuPlaneSpec` 列表创建 N 个 `danmaku_plane_N` 面板（三层平行 / 左右分组）。
- fanout 分派器：每个弹幕事件按策略路由到唯一平面，替换「每平面吃全部」复制语义。

## 验收 / 边界

- 跑 `.\scripts\build-windows-debug.ps1`（含单测）编译 + 测试全绿。
- 设备验证需用户显式要求才安装/启动 APK。
- 不回退抓取载体替换、grab bar 淡入淡出等已完成功能；不改 `.metaspatial`/`Composition.glxf`。
