# 收起 Workbench 时重置为视频列表空态并隐藏输入法

## 目标

触发 Workbench 收起（WorkbenchEvent.Dismiss / dismissWorkbenchFromCenterContent / WorkbenchOuterDismiss）时：

1. 把浏览路由重置回「视频列表」空态（WORKBENCH_EMPTY 的空按钮态），而不是停留在
   SEARCH / SEARCH_RESULTS / 详情等上次路由——下次呼出 Workbench 无需现场切路由。
2. 强制隐藏应用输入法面板：`syncInputMethodPanelVisibility` 当前依据
   `appState.searchWorkspace.isKeyboardVisible && !isKeyboardDismissed` 显示，收起 Workbench 后
   该标志若仍为 true，键盘面板会残留在画面上（"Workbench 收起了但输入法还在"）。
   Dismiss 时应复位 `isKeyboardVisible` / `isKeyboardDismissed`（清空 composition/candidates 更佳）。

## 根因（2026-10-02 已核实，别重复验证）

复位**并非缺失，而是挂在错误的触发条件上**：

- `ViriViriAppState.openWorkbenchEmpty()` 已经一次性复位 `route = WORKBENCH_EMPTY`、
  `isKeyboardVisible = false`、`isKeyboardDismissed = true`、`isCandidatesExpanded = false`
  （目标 1 与 2 所需的复位逻辑已存在，不需要新写）。
- 但它只从 `SpatialVideoSampleActivity.applyPlaybackCanvasSlots()` 一处被调用，且被
  `PanelSlot.BROWSE !in visibleSlots && PanelSlot.TRANSPORT in visibleSlots && selected != null`
  三重条件守卫。
- 收起时 `dismissWorkbenchFromCenterContent()` 派发 `PlaybackCanvasEvent.Dismiss`，canvas 变为
  `QUIET_WATCH`；而 `PlaybackCanvasReducer.visibleSlots()` 对 `QUIET_WATCH` 只返回
  `MEDIA_STAGE`（`PlaybackCanvasContracts.kt:77`，单测 `PlaybackCanvasContractsTest.kt:111`
  断言 TRANSPORT 不在其中）。⇒ 该守卫在收起后**恒为 false**，复位不会发生。
- 复位实际落在**下次呼出**：呼出时 canvas 变 `PLAYBACK`，TRANSPORT 才进入 slots，条件成立，
  于是路由在被看见的瞬间才切换 —— 这正是「等再次点开才跳转」的来源。

## 有意保留的约束（用户 2026-10-02 确认）

`selected != null` 是**刻意为之，不是缺陷**：没有选中视频时**不得**复位 —— 此时没有可回退的
视频列表，把路由压回 `WORKBENCH_EMPTY` 是错的。本次修复只调整**触发时机**（提前到收起时），
不得放宽或删除这条守卫。

## 落点

state/reducer → host 回调 → UI 组件（见 AGENTS.md 分层：state 契约 → reducer/host → Compose UI → 测试）。

## 非目标

- 不改变视频选择后回播放的逻辑。
- 不改变 Workbench 呼出/显示的既有交互。

## 验收

- 收起 Workbench（且**已选中视频**）时**立即**复位：中心区回到视频列表空态，输入法/候选区一并
  隐藏、无残留；不再出现「下次呼出时才现场切路由」的可见跳转。
- **未选中视频**时收起**不复位**路由 —— 这是有意行为，不是缺陷（见上节约束）。
- 再次呼出 Workbench 时直接展示空态，不残留上次浏览路由或键盘。
