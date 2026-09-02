# 右面板完善为 Context rail（选集/相关/弹幕）

## 目标

把 Workbench 右面板从 mode_panel 占位（标题 + 显示比例/画布尺寸/2D 入口 + debug aspect 控件）补全为
Context / Source rail，按 `docs/prototypes/workbench/README.md` 的设计提供选集、合集/播放列表、
相关视频与弹幕状态切换。

## 现状

- 右面板现仅为 mode_panel 占位内容。
- runtime 面板已设 0.7m 宽。
- debug aspect 控件后续隐藏或迁走。

## 待办

1. 选集（parts）列表。
2. 合集 / 播放列表。
3. 相关视频。
4. 弹幕状态切换。
5. debug aspect 控件后续隐藏或迁走。

## 非目标

- 不改变左面板 / 中心面板现有结构。
- 不引入第二个播放器或 Surface。

## 参考

- `docs/prototypes/workbench/README.md` 的 Context / Source rail 设计。
- 相关源码：`app/src/main/java/com/m0e_n00b/viriviri/RecommendationUi.kt`、
  `SpatialVideoSampleActivity.kt`、`ViriViriAppState.kt`。

## 验收

- 右面板展示完整的 Context rail（选集 / 相关 / 弹幕等），交互可用。
- debug aspect 控件不再占据右面板主区域。
