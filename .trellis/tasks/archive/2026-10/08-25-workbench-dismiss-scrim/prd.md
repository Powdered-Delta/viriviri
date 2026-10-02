# Workbench 关闭手势改为外部区域点击

## 目标

把"关闭 Workbench"的点击监听从中心面板（CenterContentPanel）上移除，只在用户点击 Workbench 范围**之外**时才触发隐藏。

## 现状

- 中心面板 Compose 根（`CenterContentWorkspace` 的 Column）带有
  `.clickable(enabled = onDismissWorkbench != null) { onDismissWorkbench?.invoke() }`，
  即点击中心面板内的任意空白/非按钮区域都会 dismiss Workbench。
- `WorkbenchOuterDismiss` 场景几何体（`attachOuterDismissInput`）已经是
  Workbench 外部区域的唯一 dismiss owner，点击它会调 `dismissWorkbenchFromCenterContent()`。
- MediaStage 点击在 Workbench 可见时只 reveal transport，不 dismiss（已修正）。

## 待办

1. 移除中心面板根的 `onDismissWorkbench` clickable（点中心面板空白不应关闭）。
2. 中心面板不再向 `ImmersiveCenterContentPanel` / `RecommendationPanel` 传入 `onDismissWorkbench`。
3. 确认 `WorkbenchOuterDismiss` 几何体完整覆盖 Workbench 之外的可视区域，且不挡住
   左右 rail、transport、nav、输入法的 raycast。
4. 确认 `suppressOuterDismissUntilMs` 在打开 Search/Browse 后不会误触发首次点击 dismiss。
5. 保留 MediaStage 在 Workbench 可见时只 reveal transport 的行为。

## 非目标

- 不改变视频选择后回播放的逻辑。
- 不改变左右 rail / transport 自身的交互。
