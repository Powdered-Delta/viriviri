# 列表分页、封面与画面比例调查

## 当前问题

`BilibiliPlaybackProvider.loadRecommendations()` 固定请求
`fresh_idx=0&brush=0`；`searchVideos()` 固定发送 `page=1`。`ViriViriAppState`
只有一个列表和 `isLoading`，没有分页游标、追加加载状态或来源代次。
`Recommendation` 已经携带 `coverUrl`，但 `RecommendationUi` 的列表项仅显示文字。

PiliPlus 的同一 web 推荐 endpoint 使用 `fresh_idx` 和 `brush` 作为连续 feed
游标。公开请求确认 `fresh_idx=20&brush=1` 能返回后续 `data.item`。

## 分页边界

- 推荐页使用 `freshIdx = current page result count`，同时传入相同 `brush`；
  追加不足一页的新 BV 号时视为没有更多，避免无限重复。
- 搜索页使用 WBI 签名参数 `page` 和固定 `page_size=20`；不足页表示终止。
- 一个显式请求代次代表 feed refresh 或新的搜索词。旧代次/旧搜索词的成功或失败
  都不允许更新当前列表；同一代次只允许一个追加请求。
- UI 只在接近末尾时请求 `loadNextPage()`，状态层负责最终的幂等与去重。

## 封面边界

封面下载放在 app 状态持有的 `ThumbnailRepository`：仅接受 HTTP(S) `coverUrl`，
限制最大缓存数，使用 `HttpURLConnection` 和 `BitmapFactory` 解码。Compose 只读取
`thumbnailStateByUrl` 并渲染 placeholder/bitmap/error 状态，不发网络请求、不解析 API。

## Spatial 画面比例

当前舞台固定为 `16:9` mesh，媒体面板却使用 `PixelDisplayOptions(3840, 1080)`，
这是上游 SpatialVideo 立体样例的 `StereoMode.LeftRight` 配置。ViriViri 使用
`StereoMode.None`，但保留了 3.55:1 输出缓冲并将完整纹理 UV 映射到 16:9 mesh；
这是竖屏拉伸和部分横屏右边条状错误采样的主要嫌疑。

修复使用与固定舞台一致的 `1920x1080` 输出缓冲，且唯一共享 Media3 Player 使用
`C.VIDEO_SCALING_MODE_SCALE_TO_FIT`。该模式在同一现有 Surface 内保留视频比例，
让 pillarbox/letterbox 成为解码输出的一部分；不创建第二个 player 或视频 target。

Quest 验证必须分别检查标准 16:9 横屏、非标准横屏/右侧条带、9:16 竖屏，以及切换
来源后的 Surface/Player 连续性。
