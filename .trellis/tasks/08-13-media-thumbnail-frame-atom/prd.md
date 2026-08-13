# 抽取媒体缩略图框架原子组件

## Goal

把推荐列表中固定比例缩略图容器和右下角 overlay 位置抽取为可复用 Compose 原子组件，保持下载、`Bitmap` 和列表状态在 app 层。

## Requirements

- `:spatial-workbench-compose` 提供稳定、可配置尺寸的 `MediaThumbnailFrame`。
- atom 提供主内容 slot 与右下角 overlay slot，不依赖 Bilibili、Android `Bitmap`、Media3、Surface、Activity 或 Meta SDK。
- atom 保持当前 `128.dp x 72.dp` 推荐列表几何和 `ContentScale.Crop` 行为由 app 内容 slot 决定。
- app 的 Ready/Loading/Failed 缩略图渲染、`ContentAccessBadge` 和具体文字迁移到 frame slots。
- 角标位置保持右下角，普通内容不显示角标。

## Non-goals

- 不移动网络下载、缓存、`ThumbnailState` 或 Android `Bitmap`。
- 不改变 Bilibili 映射、播放器、Surface、Spatial scene、Quest 安装或启动。
- 不引入圆角卡片、额外的视觉主题或第二个视频输出。

## Acceptance Criteria

- [x] shared Compose frame 有可测试的固定尺寸/overlay contract。
- [x] app 不再私有拥有 thumbnail Box/frame/overlay 布局。
- [x] 下载、Bitmap 和状态仍只在 app 层。
- [x] Compose/app tests 覆盖尺寸、overlay 和原有状态分支。
- [x] Windows Gradle 验证通过：`:spatial-workbench-core:test`、`:spatial-workbench-compose:test`、`:spatial-workbench-compose:compileDebugKotlin`、`:app:testDebugUnitTest`、`:app:assembleDebug`。
