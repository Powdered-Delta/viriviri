# 限制媒体源解析尝试

## Goal

让 Bilibili 当前视频的播放源解析可取消、可超时，并保持现有 Loading / Error /
Retry 状态对最新选片的正确性。

## Scope

- `ViriViriAppState` 持有单一 `playbackResolutionJob`。
- 新选片和 Retry 先取消现有解析 job，再启动新的解析 attempt/request id。
- 使用固定 45 秒协程超时限制 Bilibili `createMediaSource`。
- 协程取消不是用户可见错误；超时显示稳定、可重试的解析超时错误。
- 只有最新 request id 的成功/失败/超时可调用已有 `PlayerSession.setMediaSource`
  或更新 `isResolvingPlayback`/错误状态。
- 新请求启动、取消、成功、失败和超时均保持现有唯一 player / active Surface 合约。
- 添加纯 JVM error outcome/timeout 文案测试并更新运行手册。

## Non-Goals

- 不更改 Bilibili endpoint 超时、HTTP client、播放器 decoder error 路径、重试次数、
  后台预取、列表加载、空间场景或 UI 结构。
- 不新增 player、Surface、Entity 或 Spatial panel。

## Acceptance Criteria

- 新选片/Retry 会取消上一解析 job，旧结果不能改写当前状态或 source。
- 超时不会永久保持 Loading，显示可读错误并恢复 Retry。
- 已取消的 attempt 不显示错误。
- 完整 unit/assemble 回归通过。
