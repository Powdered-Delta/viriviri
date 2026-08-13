# 实现沉浸式短暂消息 Toast 组件

## Goal

提供不依赖 Android 系统 Toast 的共享短暂消息基础设施，使 2D 和沉浸面板能用同一份纯状态、主题 token 和 Compose host 呈现短暂反馈。

## Requirements

- core 定义 `TransientMessage`、`INFO`/`SUCCESS`/`WARNING`/`ERROR` severity、默认停留时长、可选单一 action 和纯 reducer。
- reducer 只管理当前消息与 FIFO 队列；不创建计时器、协程、Activity、Surface、播放器或网络请求。
- Compose 提供 `TransientMessageHost`，由 `LaunchedEffect` 在当前消息的停留时间到达后向宿主回调；host 可由任意 overlay slot 放置。
- Compose 样式从 `CinemaPalette` 解析语义色 token，不硬编码颜色；文本与 action 保持可读。
- app 在 recommendation/search 加载失败和媒体解析失败时投递 ERROR toast；原有 inline error 仍保留为可恢复状态。
- 关闭或 action 只通过 callback 通知 app，不在 shared component 内执行业务操作。

## Non-goals

- 不使用 `Toast.makeText`、Android Snackbar 或新的 Spatial panel/entity。
- 不影响 PlayerSession、ExoPlayer、视频 Surface、媒体 source、Quest scene 或 hand tracking。
- 不实现多 action、持久通知、系统通知、网络重试或 account/login 提示。

## Acceptance Criteria

- [x] core reducer 覆盖 enqueue、dismiss、advance、action 和 FIFO 队列。
- [x] Compose host 使用 palette token，且超时/action/dismiss 都通过 callback 外发。
- [x] app 能显示加载/搜索/解析失败的 ERROR toast，保留 inline error。
- [x] core/Compose/app unit tests 覆盖状态、样式与 app 投递。
- [x] Windows Gradle 验证通过：`:spatial-workbench-core:test`、`:spatial-workbench-compose:test`、`:spatial-workbench-compose:compileDebugKotlin`、`:app:testDebugUnitTest`、`:app:assembleDebug`，`BUILD SUCCESSFUL in 58s`。
