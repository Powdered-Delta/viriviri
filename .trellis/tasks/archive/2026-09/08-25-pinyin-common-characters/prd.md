# 拼音候选常用字排序修复

## 问题

内置 QWERTY 拼音输入法输入某些音节时，首选和候选以生僻字为主。
复现：输入 `qiu`，候选只有 `丘`，其余为生僻字；常用字 `求/球/秋/丘` 等排序错误。

## 根因

`DefaultOfflinePinyinLexicon`（`SearchInputMethod.kt`）的候选来源：

1. `PHRASES` 显式词组；
2. `PREFERRED_CHARACTERS` 手填的"音节 -> 常用字"映射；
3. `reverseIndex`：用 ICU `Transliterator.getInstance("Han-Latin")` 遍历
   `CJK_UNIFIED_IDEOGRAPHS (0x4E00..0x9FFF)` 反查拼音建立的索引。

`PREFERRED_CHARACTERS` 只覆盖了极少数字节（bi/li/ni/hao/...），没有 `qiu`，
于是回退到 `reverseIndex`。该索引按 **Unicode 码位顺序** 排列，把码位靠前的
生僻字排在前面，常用字（求/球/秋/丘 等）排不上或排在生僻字之后。

## 目标

- 所有常用音节都给出按使用频率排序的常用字候选，生僻字不应出现在前若干位。
- `qiu` 候选应以 求/球/秋/丘 等常用字开头。
- 保持纯 Kotlin、离线、可单测。

## 方向（择一或组合）

1. 扩充 `PREFERRED_CHARACTERS` 为一份覆盖普通话全部音节（含不带声调）的
   常用字频率表（按字频排序，每音节取前 N 个）。这是最直接、可测试的方案。
2. 替换/增强 ICU 反查：引入按字频排序的静态数据（资源文件或生成的 Kotlin 表），
   `reverseIndex` 只作为常用字表之外的回退，并限制数量。
3. 评估复用/引入一个离线拼音词典（注意许可证与体积），不引入网络。

## 非目标

- 不做在线词库/云输入。
- 不改输入法的 QWERTY 布局、Enter 两阶段语义和候选展开 UI。
- 不引入第二个播放器或 Surface。

## 验收

- 单测：`qiu`、`de`、`shi`、`wo`、`ni` 等高频音节的首个候选为常用字，
  前 N 个候选不含明显生僻字。
- `woshi`/`nihao` 连续词组候选仍按 PRD Phase 3 工作（phrase-before-character）。
- `:app:testDebugUnitTest` 与 `:spatial-workbench-compose:testDebugUnitTest` 通过。
- `scripts/build-windows-debug.ps1` 通过。
