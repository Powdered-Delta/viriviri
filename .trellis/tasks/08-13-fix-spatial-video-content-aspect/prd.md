# 修复空间视频内容网格比例

## Goal

让现有固定 16:9 Spatial 舞台中的实际视频内容 quad 根据 Media3 `VideoSize`
保持原始显示比例，解决竖屏仍被拉扁的问题。

## Scope

- 添加纯 Kotlin contain 布局计算：固定舞台内按视频宽高和像素宽高比返回
  居中内容 quad 尺寸。
- 保存现有 `spatialized_video_panel` 自定义 `TriangleMesh` 的引用。
- 在现有共享 Player listener 收到 `onVideoSizeChanged` 时，仅更新该 mesh 的
  前景视频 quad 顶点；阴影、现有 panel、Entity、Transform、Surface、播放器和
  transport 父子关系不变。
- 无效/未知 `VideoSize` 时保持完整 16:9 内容 quad，避免播放切换期间闪烁或
  生成非法 mesh。
- 添加纯 JVM 数学测试，覆盖 16:9、9:16、超宽、非方形像素和无效尺寸。
- 更新 Quest 运行手册和验证清单。

## Non-Goals

- 不创建或重建 Spatial panel/entity/player/Surface，不修改 Scene 固定层级或
  重新定位 transport，不改变播放器媒体源/seek/播放状态。
- 不实现裁切 fill、视频缩略预览、动态舞台尺寸、旋转检测、字幕/弹幕或 Context。

## Acceptance Criteria

- 9:16 视频在固定 16:9 舞台内以窄居中内容 quad 显示，不横向拉伸。
- 16:9 内容 quad 保持满舞台；超宽内容 quad 降低高度且居中。
- 变化只作用于现有 `TriangleMesh` 的前景四顶点；唯一 Player 和活跃视频
  Surface 合约不变。
- 纯计算测试、app unit tests、完整 debug assembly 通过。
