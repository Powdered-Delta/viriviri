# 重试沉浸式媒体解析失败

## Goal

在现有沉浸式 mode panel 中为当前选中视频的 Bilibili 播放解析失败提供明确、
可取消陈旧结果的 Retry 路径。

## Scope

- `ViriViriUiState` 显式记录当前播放解析是否进行中。
- 选片与 Retry 复用同一 app-state 私有解析函数；每次尝试递增现有
  `playbackRequestId`，旧尝试的成功/失败不得覆盖新选片或新重试。
- 成功时仅调用已有 `PlayerSession.setMediaSource`；失败时保留 selected 视频，
  停止 loading 并显示用户可读错误。
- 复用已有 `mode_panel` 添加 Retry 按钮；仅在 viewer 的播放解析失败且未进行
  解析时可见，解析中禁用/显示 `Retrying...`。
- Spatial Activity 只按 app state 更新已有 Android view 的可见性/可用性，并将
  点击转交给 `ViriViriAppState.retrySelectedVideo()`。
- 添加纯 JVM retry 可用性策略测试，并更新运行手册。

## Non-Goals

- 不新建 player、Surface、Entity 或 Spatial panel；不重建媒体舞台，不做自动
  无限重试，不把 ExoPlayer decoder error 当作 Bilibili 解析错误重复重载。
- 不改变 Browse、列表分页、Context scene 阻塞、2D UI 或播放源协议。

## Acceptance Criteria

- 解析失败的当前视频有可见 Retry；没有 selected 或列表分页错误时不显示。
- Retry 与新选片竞态安全，只有最新 attempt 可以设置 MediaSource 或错误。
- 解析中重复点击不会发起并行 Bilibili source 请求。
- 所有操作复用 process-wide player 和活跃 Surface，完整自动化构建通过。
