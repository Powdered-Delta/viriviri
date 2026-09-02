# 播放控制下拉菜单（PopupMenu）统一暗色主题

## 目标

把播放控制中的 android.widget.PopupMenu（音量/画质/倍速/显示比例/画布尺寸/调试比例等菜单，
由 controls.xml 按钮触发，走 View 体系）从系统默认 Material 浅色样式统一为应用暗色主题。

## 现状

- `SpatialVideoSampleActivity` 中多个控件菜单使用 `android.widget.PopupMenu`，目前走系统默认浅色样式。
- 与周围 Compose 面板的暗色 UI（InputConsoleStyle / palette）不一致。

## 方案（择一）

1. 为 PopupMenu 提供深色 `ContextThemeWrapper` / `popupMenuStyle` 覆盖（surface 深色背景 +
   normalText 文字 + highlightText 选中态）。
2. 迁移到 Compose `DropdownMenu`，复用 InputConsoleStyle / palette。

## 非目标

- 不改菜单项语义与触发位置。
- 不引入第二个播放器或 Surface。

## 验收

- 每个播放控制 PopupMenu 均为暗色主题，文字/选中态清晰可读。
- 需戴设备逐个菜单验证（音量/画质/倍速/显示比例/画布尺寸/调试比例）。
