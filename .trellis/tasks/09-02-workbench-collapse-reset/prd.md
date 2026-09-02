# 收起 Workbench 时重置为视频列表空态并隐藏输入法

## 目标

触发 Workbench 收起（WorkbenchEvent.Dismiss / dismissWorkbenchFromCenterContent / WorkbenchOuterDismiss）时：

1. 把浏览路由重置回「视频列表」空态（WORKBENCH_EMPTY 的空按钮态），而不是停留在
   SEARCH / SEARCH_RESULTS / 详情等上次路由——下次呼出 Workbench 无需现场切路由。
2. 强制隐藏应用输入法面板：`syncInputMethodPanelVisibility` 当前依据
   `appState.searchWorkspace.isKeyboardVisible && !isKeyboardDismissed` 显示，收起 Workbench 后
   该标志若仍为 true，键盘面板会残留在画面上（"Workbench 收起了但输入法还在"）。
   Dismiss 时应复位 `isKeyboardVisible` / `isKeyboardDismissed`（清空 composition/candidates 更佳）。

## 落点

state/reducer → host 回调 → UI 组件（见 AGENTS.md 分层：state 契约 → reducer/host → Compose UI → 测试）。

## 非目标

- 不改变视频选择后回播放的逻辑。
- 不改变 Workbench 呼出/显示的既有交互。

## 验收

- 收起 Workbench 后中心区回到视频列表空态，输入法/候选区一并隐藏、无残留。
- 再次呼出 Workbench 时直接展示空态，不残留上次浏览路由或键盘。
