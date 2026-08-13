# 修复 2D 路由与沉浸画面冻结

## Goal

修复 Quest 上从沉浸式 ViriViri 路由至 2D panel 后，推荐列表被输入台挤出可视区域，以及返回沉浸式后视频画面停留在切换前最后一帧、后续换片无法呈现的问题。

## Observed Behavior

- MediaRoom 环境已在头显中可见。
- 从沉浸式点击 2D 后，2D panel 实际只显示九宫格输入界面，推荐列表不可见。
- 返回沉浸式后，视频 panel 停留在路由前的一帧；选择新视频也不能更新该画面。

## Root Cause

- `RecommendationList` 将固定高度的 `SearchInputPanel` 与未约束的 `LazyColumn` 放在普通 `Column` 中。2D activity 的 `800dp x 550dp` panel 高度不足时，列表被放置在屏幕外。
- 离开 immersive route 时 `PlayerSession.beginOutputHandoff()` 清除当前 player Surface。返回时 `ImmersiveMediaStageHost` 仍缓存同一 SDK-owned Surface，并将重复 attach 视为 no-op，因此没有重新执行 `player.setVideoSurface(surface)`。视频 decoder 没有活跃输出，SDK panel 显示旧帧。

## Scope

- 让推荐列表在 2D panel 中占用输入区域后的剩余可用高度并可滚动。
- 在 2D -> immersive return handoff 后，明确重新绑定当前 SDK-owned immersive Surface，不创建第二个 ExoPlayer 或第二个 video Surface。
- 保留 Surface 资源所有权：应用不释放 SDK-owned Spatial Surface；TextureView 创建的 Surface 仍由 2D host 释放。
- 添加 JVM 测试，覆盖 host 对同一 SDK Surface 的显式 reattach 行为和推荐列表约束布局的回归意图。

## Non-goals

- 不改变 Meta Spatial Editor 场景、MediaRoom 实体或其 parentage。
- 不实现 Android Toast、重新设计搜索输入法，或改变 Bilibili API/播放解析。
- 不自动安装、启动或控制 Quest 设备。

## Acceptance Criteria

- [x] 2D panel 默认以可浏览的 recommendation list 为主；搜索输入台由 Search icon 展开，列表使用 `weight(1f)` 占输入台后的剩余高度并保持滚动和分页。
- [x] 返回沉浸式时，即使 SDK 提供的是同一 Surface 对象，也会把该 Surface 重新附着到唯一的 `ExoPlayer`。
- [x] 普通重复的 immersive Surface 回调仍为 no-op；强制重附着仅由一次性的 2D-return intent signal 触发。
- [x] 不引入第二个 player 或同时活跃的 video Surface。
- [x] Windows JDK 17: `:app:testDebugUnitTest :app:assembleDebug` passed in 48s with `:app:export` in the build graph; changed Kotlin files have no LSP diagnostics.
- [ ] Quest manual validation: install this new APK, confirm 2D list is visible, return to immersive, and verify a new video selection updates the immersive panel.
