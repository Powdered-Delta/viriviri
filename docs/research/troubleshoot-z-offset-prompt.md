# 排查 prompt：整套 Workbench UX 的 Z 轴（深度）位置偏移

## 现象
- 当前 debug 包（构建 SHA 2dccfd45，APK 时间 16:50）里，**整套沉浸式 Workbench UX 在深度方向（Z）整体偏移**，疑似被推到 Z≈0 或与相机/幕布不在原深度。
- 对照组：上一个"可用版本"里**弹幕随幕布放大（Scale 传播）是正常的**——所以 Scale 传播那两行不要回退、也不是首嫌。
- 高度怀疑是**多个 session 并行改同一批空间文件时引入的回归**（隔壁 session 在动 Workbench/面板/场景）。本任务是**定位**，先不要大面积重写。

## 已确认的事实（别重复验证）
- seg.so 弹幕数据正常（实测 HTTP 200/protobuf 正确）；弹幕无显示的根因已修（主线程网络 -> 后台线程 + 时间窗补全），与 Z 偏移无关。
- Scale 传播（`applyPlaybackStageScale` 给 `spatialized_video_panel`、`danmakuOverlayEntity`、`stageBackdropEntity` 设 `Scale`；两个 overlay 创建时按 `appliedStageScale` 初始化）在默认 scale=1.0 时为恒等、且 Scale 组件只缩放不平移，**理论上不改变 Z**。保留它。
- 关键空间量已集中到 `WorkbenchLayoutConfig.kt`：`STAGE_WORLD_Z = 2.0f`（幕布 authored 世界 Z）、`centerLocalZ=-0.52f`、`navLocalZ=-0.52f`、`transportLocalZ=-0.58f`、`keyboardLocalZ=-0.78f`、`railWorldZ = STAGE_WORLD_Z - R*cos(yaw)`、`centerBottomLocalY=-0.63f`。
- 本地坐标系（MediaStage 子物体）：**-Z 朝向用户、-Y 向下**；幕布/舞台父实体是 `Entity(R.id.spatialized_video_panel)`，世界 Pose 存在场景里（STAGE_WORLD_Z≈2.0）。

## 重点怀疑方向（按优先级）
1. **父实体 / TransformParent 被改或丢失**：center/rails/transport/nav/keyboard/backdrop/danmaku 都挂在 `spatialized_video_panel` 下用本地坐标。检查这些 `TransformParent(Entity(R.id.spatialized_video_panel))` 是否仍在、是否有面板被错误 reparent 到 null/root（世界原点 Z≈0）或相机。
2. **`Transform(Pose(...))` 本地 Z 被覆盖/写反符号**：重点看 `SpatialVideoSampleActivity.kt` 里用 `layout.centerLocalZ/transportLocalZ/keyboardLocalZ/navLocalZ` 的几处，以及 `railWorldZ`（世界系）用在哪个实体上——世界系 Z 被误用到本地系实体会直接深度错乱。
3. **`WorkbenchLayoutConfig` 字段被隔壁改成 0 或符号翻转**：核对 `centerLocalZ/navLocalZ/transportLocalZ/keyboardLocalZ/STAGE_WORLD_Z/railArcRadius/railYawDegrees` 的当前值与 git 历史（`git log -p` / `git diff`）。
4. **场景文件（.glxf/.metaspatial）被 regenerate/导出覆盖**：`spatialized_video_panel` 的 authored Transform（世界 Z≈2.0）若被重置到 Z=0，所有子物体跟着整体前移。静态场景属 Meta Spatial Editor/mse-agent 产物，**不要手改**，用 mse-agent 或重新导出核对。
5. **可见性/透明度系统误伤**：`PanelLayerAlphaSystem` 等改 colorScaleBias 不应动位置，但确认没有系统在写 Transform。
6. debug 面板新增（`debug_panel` 现在 `BuildConfig.DEBUG` 注册，shape 0.8x0.7m）只是多一个浮窗，不应影响其它面板层级；顺带确认它没被 parent 到舞台。

## 建议排查步骤
1. `git status` / `git diff` 先看哪些空间相关文件是未提交改动（重点：`SpatialVideoSampleActivity.kt`、`WorkbenchLayoutConfig.kt`、`app/scenes/*.glxf|*.metaspatial`、components xml）。
2. 用 `git log --oneline -n 20` 和 `git stash list` 找出"上一个可用版本"的提交，`git diff <good>..HEAD -- <上述文件>` 只看空间量（Pose/Vector3/TransformParent/Scale/PanelDimensions）。
3. 在 `vrReady`（`SpatialVideoSampleActivity` 约 616 行 `mrPanelPose`）处打印：`spatialized_video_panel` 的世界 Transform、以及 center/rail/keyboard 各自的 `getComponent<Transform>()` 与 `TransformParent`，对比期望值（幕布世界 Z≈2.0；子物体本地 Z 为 -0.5x）。
4. 二分定位：临时把 `WorkbenchLayoutConfig` 的 Z 字段逐个恢复成常量旧值（centerLocalZ=-0.52 等）、或临时注释掉新增的 `PanelDimensions` / 本地 Transform 覆盖，看深度是否恢复，锁定具体那一行。
5. 结论输出：指出**具体文件:行号**是哪个 Transform/Parent/字段导致整体 Z 偏移，并给最小修复；不要回退弹幕 Scale 传播、不要动弹幕流式逻辑。

## 边界
- 只读排查优先；需要改时改最小范围，改完跑 `.\scripts\build-windows-debug.ps1`，不要安装/启动 APK。
- 静态场景（.glxf/.metaspatial）不要手改，走 mse-agent/重新导出。
