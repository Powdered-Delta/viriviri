# 修复 Compose 测试语音回调类型

## Goal

让 `InputConsoleStyleTest` 的语音回调符合 `CinemaInputConsoleActions.onVoice: () -> Unit` 合同，恢复 Compose unit-test 的 Kotlin 编译。

## Scope

- 将递增计数器的表达式 lambda 改为显式返回 `Unit` 的块 lambda。
- 令 palette alpha 断言匹配 Compose `Color` 的 8-bit alpha 量化。
- 将 Bilibili 映射单测从 Android framework `JSONObject` stub 改为纯 Kotlin DTO/mapping 入口。
- 保留现有两次回调和 `voiceCalls == 2` 断言。

## Non-goals

- 不改生产 callback contract、UI、主题、播放器、网络或 Quest 行为。
- 不通过 `unitTests.returnDefaultValues` 掩盖 Android JSON stub；不新增 JSON 解析库只为测试。

## Acceptance Criteria

- [x] `InputConsoleStyleTest` 可通过 Kotlin 编译和运行。
- [x] 语音回调测试仍验证两次调用。
- [x] Palette alpha 测试验证真实 Compose 量化结果。
- [x] 推荐映射测试不调用 Android `org.json` stub，仍覆盖充电/普通映射。
- [x] Windows Gradle 重新执行 core/Compose/app 验证任务：`:spatial-workbench-core:test`、`:spatial-workbench-compose:test`、`:spatial-workbench-compose:compileDebugKotlin`、`:app:testDebugUnitTest`、`:app:assembleDebug` 均通过。
