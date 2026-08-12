# 显示沉浸式当前媒体状态

## Goal

让沉浸式场景的既有 `mode_panel` 显示当前选中视频的可读标题、作者及最近
播放解析错误，使 Browse 选片与播放失败在头显中有直接反馈。

## Scope

- 重用既有 `mode_panel`，不改变其注册、实体、锚点、尺寸或父子关系。
- 将原固定 `Video Mode` 文本替换为可更新的当前标题，单行截断。
- 增加紧凑副标题/错误行：正常时显示作者；播放解析失败时显示安全截断的错误。
- Activity 订阅现有 `ViriViriAppState.state`，只更新已创建的 Android TextView；
  无状态时使用稳定占位。
- 添加纯 Kotlin 文本投影/截断 helper 和 JVM 测试。
- 维持已有 `DEV <hash>` 标签和 2D 切换功能。

## Non-Goals

- 不创建新的 Spatial panel/entity/Surface/player，不更改媒体源、播放控制、
  Browse reducer 或 Context 场景层级。
- 不实现创作者详情、字幕、弹幕、通知、Toast 或 2D 重构。

## Acceptance Criteria

- 选择视频后 mode panel 显示对应标题和作者。
- 播放解析失败时 mode panel 显示精简错误而保留当前标题。
- 长文本不会改变面板几何或遮挡 2D 按钮/hash。
- observer 在 Activity 销毁时取消，自动化构建通过。
