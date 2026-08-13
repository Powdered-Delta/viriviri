# 显示沉浸式媒体解析进度

## Goal

让现有沉浸式 mode panel 在选择视频或点击 Retry 后清楚显示播放源解析进行中，
完成后恢复作者信息，失败后显示错误与已有 Retry。

## Scope

- 扩展纯 `immersiveMediaStatus` 投影，加入 `isResolvingPlayback`。
- viewer 中正在解析时 detail 固定为 `Loading video...`，优先级高于作者；
  viewer 错误优先于作者，保留现有标题。
- 现有 Spatial Activity 将 app-state resolving 状态传给同一个 mode-panel
  TextView 更新路径。
- 增加 JVM 测试覆盖 loading、error、author、空状态的显示优先级。
- 不增加 Android/Spatial UI 结构或任何播放器操作。

## Non-Goals

- 不新增 player、Surface、Entity、Spatial panel、加载动画、倒计时、自动重试或
  网络调用；不改媒体源/播放控制契约。

## Acceptance Criteria

- 新选片和 Retry 后，mode panel 显示当前标题和 `Loading video...`。
- 解析成功后 detail 显示作者；失败后 detail 显示错误且 Retry 可用。
- Browse 分页状态不污染当前媒体 detail。
- 自动化构建通过。
