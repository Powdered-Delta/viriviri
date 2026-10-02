# 视频幕布曲面度调节

## 目标

为沉浸式视频幕布（`spatialized_video_panel` 的自定义 `TriangleMesh` 视频 quad）增加可调的圆柱曲率，让视频在大屏/巨幕模式下更有环抱感，并让贴合弹幕与字幕沿同一柱面 UV 采样。

## 背景

- 当前幕布是代码生成的平面四边形（见 `updateSpatialVideoContentQuad`），弹幕/字幕 UV 贴在同一平面。
- Workbench 的三块 UI 面板使用标准 `QuadShapeOptions`，**保持平面**；本任务只针对视频幕布，不弯曲 UI 面板。
- SDK 提供 `CylinderShapeOptions(radius, width, height)`，但架构文档 `docs/immersive-ui-low-code-architecture.md` 已指出：视频用的是自定义 TriangleMesh，不能假定标准 Cylinder panel 直接生效，需要独立验证（迁移到标准 media panel 曲面，或重写为可采样圆柱网格）。

## 非目标

- 不创建第二个播放器、MediaStage 或视频 Surface。
- 不弯曲 Workbench UI 面板（左/中/右保持 QuadShapeOptions 平面）。
- 不影响 2D PancakeActivity 的 TextureView 输出。

## 实施方向（待验证）

1. 引入 `StageGeometry` 抽象：Flat / Cylinder(radius)。
2. 将视频 quad 细分为 N×M 网格，按圆柱曲率弯曲顶点；UV 保持 0..1 线性映射。
3. 贴合弹幕（FLAT 模式）与字幕沿同一柱面排布；SPATIAL 弹幕不受影响。
4. 曲率作为播放偏好持久化（参考现有 `playbackStageScale` / SharedPreferences），默认平面（无穷大半径）。
5. 控制入口：先放 debug 面板验证，再决定是否进入 transport 设置菜单；可与右摇杆 stage scale 叠加。
6. 验证现有 aspect probe、stage scale、2D/沉浸式 Surface handoff 在曲面下仍正确。

## 验收

- 默认平面时行为与当前完全一致。
- 曲率可调且实时生效，视频/弹幕/字幕不撕裂、不拉伸异常。
- 不创建额外播放器或 Surface。
- 通过 `scripts/build-windows-debug.ps1`，Quest 实测巨幕/标准/曲面切换。
