# 分离空间视频前景与黑色底板

## Goal

将现有 Spatial 视频舞台拆为同一个动态媒体网格中的固定半透明黑色底板和
按媒体尺寸自适应的前景视频 quad，修复竖屏内容被拉伸的问题；同时在已有
mode panel 显示当前 debug APK 的 Git hash。

## Scope

- Gradle 生成 `BuildConfig.GIT_SHA`，现有 `mode_panel` 在 debug 版本显示
  `DEV <short-hash>`，release 隐藏该标识。
- 现有自定义 `TriangleMesh` 增加全舞台、双面的半透明黑色底板材质范围。
- 前景媒体 quad 使用现有纹理和材质，按 `VideoSize` contain 规则居中。
- 保存 `SceneMesh` 引用；每次现有 `TriangleMesh.updateGeometry()` 后调用
  `SceneMesh.updateWithTriangleMesh()` 将更新提交到当前渲染场景。
- 黑色底板位于前景内容之后；现有阴影、舞台 footprint、输入区域、transport
  parent 和所有固定 Spatial entity/panel 关系保持不变。
- 增加/更新纯 JVM 几何和 debug 标签测试；构建后创建命名验证 APK 与 sidecar。

## Non-Goals

- 不新增 Entity、Spatial panel、Surface、ExoPlayer、媒体源、场景锚点或 fixed
  Transform；不部署该 APK 到 Quest。
- 不做播放器 UI 改版、弹幕/字幕、Context rail、动态舞台尺寸、fill/crop 模式或
  2D 产品界面重构。

## Acceptance Criteria

- Debug mode panel 显示构建时的 Git short hash，便于设备侧确认版本。
- 9:16 内容在固定黑色 16:9 底板上按原比例居中，不拉伸。
- 16:9 保持填满前景区域，超宽在黑色底板内上下留空。
- 每次 VideoSize 变更真正提交到 SceneMesh，且无 player/Surface/panel/entity
  生命周期操作。
- 完整自动化构建通过；APK 被命名/哈希/归档但不使用 ADB 安装。
