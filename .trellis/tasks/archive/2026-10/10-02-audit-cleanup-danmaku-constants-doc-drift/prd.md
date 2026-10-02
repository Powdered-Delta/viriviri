# 审计收尾：弹幕渲染常量去重 + 曲率文档漂移与死代码

来源：2026-10-02 看板审计（对应看板卡 k60 与 k28）。两条都是小改动，合并为一次收尾。

## 目标 1（k60）：弹幕渲染常量双份定义

`DanmakuOverlay.kt:20-23` 以私有常量重复定义了 `SCROLL_DURATION_MS` / `FIXED_DURATION_MS` /
`SCROLLING_LANE_COUNT` / `FIXED_LANE_COUNT`，与 `DanmakuRenderConfig`（`:14-20`）的同名配置构成
**两个事实来源**。`DanmakuRuntime` 走 `config.*`，overlay 走自己的常量 —— 改动其一即静默分叉：
泳道调度（`DanmakuCanvasRuntime.scrollingFreeAt` / `DanmakuLaneScheduler`）与实际渲染
（overlay 的 x/y 与 duration 计算）会不一致，表现出来的正是错位 / 重叠 / 速度异常 / 留影那一类症状。

修法：让 `DanmakuCanvasRuntime` 暴露它**已经持有**的 `config`，overlay 改从该 config 取值。
`DANMAKU_FRAME_INTERVAL_MS` 是渲染节拍（帧间隔），不是 config 项，保持为 overlay 局部常量。

顺带记录（本次不改）：`DanmakuRenderConfig.showArea` 只有定义与 `require`，**全仓库无任何消费点**；
因默认值 `1f`，当前无行为差异，属未接线的旋钮。

## 目标 2（k28）：文档漂移 + 死代码

`docs/ui-structure-and-terminology.md` 6.7 有两处与源码不符：

- 半径写作「1.5–20 m」，实际为 `PlaybackStageCurvature.MIN_RADIUS_METERS = 1.5` /
  `MAX_RADIUS_METERS = 8`（README 写的 1.5–8 m 才是对的）。
- 曲率编辑层写作「三层各自独立」，实机已推翻：`setPlaybackCurvature` 锁步写三个字段，
  三层共享**一套**曲率。

死代码：`ViriViriAppState.cyclePlaybackCurvatureEditLayer()`（`:895`）已无任何调用方，
其驱动的 `playbackCurvatureEditLayer` 字段（`:108`）随之成为孤儿。两者一并移除。

## 非目标

- **不做 k26 的持久化迁移**（三个曲率 key 合并为一个）。`curvatureFor` / `withCurvature` /
  `PlaybackStageCurvatureLayer` 同属那次合并的范围，本次**保留**，并在 k26 卡片记录状态。
- 不改变任何运行时行为：去重只改「取值来源」，不改数值 —— 两侧当前同为 12 / 3 / 6000 / 4000。
- 不改场景数据（`.metaspatial` / `Composition.glxf`）。

## 验收

- `DanmakuOverlay` 不再定义重复常量，改读 `DanmakuCanvasRuntime.config`。
- 文档 6.7 两行与源码一致；按 AGENTS.md 做全文标识符存在性校验。
- 死代码移除后无编译错误，172 单测仍全绿。
- `.\scripts\build-windows-debug.ps1` 通过。
