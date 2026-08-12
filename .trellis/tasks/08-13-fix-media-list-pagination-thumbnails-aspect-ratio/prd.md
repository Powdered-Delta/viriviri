# 修复媒体列表分页、缩略图与播放画面比例

## Goal

修复当前沉浸式 Browse 共用视频列表只显示第一页、没有封面，以及
Spatial 视频舞台将竖屏视频拉伸并偶发横屏右侧条状拉伸的问题。

## Scope

- 推荐 feed 按连续 `fresh_idx` / `brush` 追加页；搜索按已签名的 `page`
  参数追加页。
- 状态层持有当前列表来源、下一页、追加加载状态和已加载结果；追加结果按
  `videoId` 去重，过期/并发响应不得覆盖当前来源或重复追加。
- 现有 Compose 列表接近末尾时请求下一页，并显示加载中/无更多内容状态。
- 将封面请求放在 app 状态控制的缩略图仓库中，采用有大小上限的内存缓存、
  加载占位和失败占位；Compose 不直接拼接或请求 Bilibili URL。
- Spatial 视频输出缓冲改为与固定 16:9 舞台一致的比例，现有共享 Media3
  Player 明确使用 `SCALE_TO_FIT`，保留竖屏、超宽、非标准横屏画面比例。
- 增加纯 JVM 分页/去重/封面 URL 规范化/比例策略测试，并更新运行手册。

## Non-Goals

- 不新增 ExoPlayer、视频 Surface、Spatial panel/entity、固定 scene transform，
  不重建 MediaStage，不做 2D UX 重设计。
- 不实现离线封面持久化、无限滚动预取、视频预览、质量选择、弹幕/字幕或
  独立 Context rail。
- 不重新调整场景中现有 panel 的 parent/anchor；Context 的场景阻塞不在本任务内。

## Acceptance Criteria

- 推荐与搜索都可从第一页加载到至少下一页；不会因连续滚动发出重复追加请求。
- 每个有合法 `coverUrl` 的列表项都有加载中、成功或失败的稳定视觉状态。
- 竖屏视频在固定 16:9 Spatial 舞台内保留比例并出现左右留边，而不是横向拉伸。
- 标准及非标准横屏视频不再出现右侧被横向拉伸的条带；若源/解码器仍产生
  画面异常，日志和现场检查项能区分源视频问题与输出缓冲问题。
- 不改变唯一 Player/活跃视频 Surface 合约；所有自动化测试和 debug assembly 通过。
