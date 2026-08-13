# 添加沉浸式音量控制

## Goal

在现有沉浸式 transport 面板中提供受控的播放音量选择，复用唯一共享 Media3
Player，不创建新的空间或媒体输出对象。

## Scope

- 在既有 `controls_id` 添加音量命令，显示当前规范化音量百分比。
- 点击打开现有 Android panel 内锚定的固定单选菜单：`0%`、`25%`、`50%`、`75%`、`100%`。
- 选项只更新 existing shared player 的 `volume`；不 prepare/reload/seek/replace player
  或改变 video Surface。
- 通过 Media3 volume 变化回调与 panel 创建同步按钮标签/选中项。
- 对无效/外部非菜单音量显示安全 `100%` 标签但不静默回写 Player。
- 添加纯 JVM volume 规范化/格式化/固定选项测试，更新 Quest runbook。

## Non-Goals

- 不新增 panel/entity/Surface/player，不做系统音量控制、音频路由、渐变、静音记忆、
  连续滑杆、空间音频重构或 2D UI 改版。

## Acceptance Criteria

- transport 显示当前音量并可选固定档位。
- 选中档位后仅 existing Player volume 改变，媒体/position/Surface 不变。
- 外部 Player 音量回调刷新标签。
- `:app:testDebugUnitTest` 与 `:app:assembleDebug` 通过。
