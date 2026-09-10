# 弹幕渲染性能：PiliPlus (canvas_danmaku) 调研与可借鉴方向

> 对照对象：PiliPlus（Flutter）弹幕用自研 git 包 `canvas_danmaku` v0.2.6（单 Canvas / CustomPainter 绘制）。
> 我方实现：`DanmakuOverlay.kt`（单 Compose `Canvas` + `nativeCanvas.drawText`）、`DanmakuLaneScheduler.kt`、
> `DanmakuMerge.kt`、`DanmakuRenderMetrics.kt`、`BilibiliDanmaku.kt`。

## 1. 架构对照（我们已经做对的）
- **单画布绘制**：PiliPlus 用一个 `DanmakuScreen` 的 CustomPainter 画全部弹幕，不是每条弹幕一个 Widget；我们也是一个 Compose `Canvas` 里 drawText。一致，不要退化成每条弹幕一个 Composable。
- **去重合并**：PiliPlus mergeDanmaku 按内容聚合并带 count；我们 `DanmakuMerge.kt` 已实现（窗口 1s、阈值 5）。
- **跳过特殊弹幕**：PiliPlus mode=7（代码弹幕）可关；我们解析时只收 mode 1-6，天然规避最贵的一类。
- **整层透明度**：PiliPlus 用 AnimatedOpacity 包整层（单 alpha）；透明度应作用于整层而非每条 paint。

## 2. 关键差距（按收益排序）

### G1. 全量预处理 → 滚动窗口惰性准备（收益最大）
- PiliPlus：弹幕按 **6 分钟分段**按需拉取，进内存后按 **0.1s 分桶**（progress ~/ 100）；只有当前 0.1s 桶的弹幕在 position 回调里 addDanmaku 进引擎，文字排版/车道在入场时才算。
- 我们：`prepareDanmaku()` 对**整支视频**每条事件都 Paint.measureText 算字宽 + scheduleDanmakuLanes 全量排车道，生成两张全量 Map 常驻内存。热门长视频上万条时，启动即上万次 measureText + 大 Map。
- 借鉴：(1) 事件按 0.1s~0.5s 分桶存 Map<Long, List<Event>>，O(1) 取当前桶；(2) metrics（measureText）与车道分配惰性化，只对可见窗口+少量前瞻计算，过期回收；(3) 车道从全量预排改为入场分配、出场释放（见 G2）。

### G2. 每帧全表二分+遍历 → 活动集（active set）
- PiliPlus：引擎只维护当前在屏弹幕，position 回调只加入当前桶新弹幕，飞出/超时由引擎删除；每帧只画活动集。
- 我们：每帧对全量 scheduledEvents 做 binarySearchBy(startMs)，从 windowStart 走到当前时间，逐条判断过期/有无 metrics/有无车道。活动弹幕少时也在扫一大段。
- 借鉴：维护 active 列表——入场时算好 metrics/车道加入，出场（完全飞出或超 duration）移除，每帧只遍历 active。复杂度从 O(全量窗口扫描) 降到 O(在屏条数)。

### G3. 车道准入控制（密度上限）——防止热门视频爆量
- canvas_danmaku 核心：没有空车道就不添加这条弹幕（碰撞规避天然限制在屏数量）；另有 massiveMode（海量模式降密度）、weight 权重阈值（低权重直接丢弃）。
- 我们：scheduleDanmakuLanes 用 minBy { availability } 给**每条**弹幕都派车道；12 车道 6s 窗口装不下时仍塞进最不忙车道 → 热门时刻互相重叠，且在屏数量无上限。
- 借鉴：(1) 准入：若最早空闲车道 availableAt > startMs + 宽限，则丢弃本条（或降级），保证不重叠且在屏条数 ≤ 车道数；(2) 权重过滤：解析保留 bilibili weight 字段，加阈值，高密度时优先丢低权重；(3) massiveMode：单位时间弹幕超阈值时自动放宽丢弃/缩短时长。

### G4. 文字字形缓存：每条弹幕只排版/描一次（VR 高分辨率尤其值）
- Flutter 的 TextPainter 是 layout 一次、每帧 paint；canvas_danmaku 复用排版结果。
- 我们：每条活动弹幕每帧 drawText 两次（描边 outline + 填充 fill）。VR 面板分辨率高、30-72fps，几百条 × 每帧两次字形绘制是主要开销。
- 借鉴（Android 等价物）：弹幕入场时把描边+填充静态文字录制一次到 android.graphics.Picture（或绘成 hardware Bitmap），每帧只 drawPicture/drawBitmap 平移（x 随时间变、y 固定），出场回收。每帧成本从整形字形降到一次贴图平移。

### G5. 滚动速度模型：固定时长 vs 固定速度
- PiliPlus 提供 scrollFixedVelocity：按像素/秒滚动而非固定 6s 穿过。短弹幕更快离开、长弹幕稍慢，密度更均匀，且与面板宽度/分辨率解耦。
- 我们：SCROLL_DURATION_MS=6000 固定时长，x = width - elapsed*(width+textWidth)。Workbench 面板可缩放，固定时长在不同宽度下速度不一致。
- 借鉴：加 fixedVelocityPxPerSec 可调项，面板尺寸变化时速度观感一致。

### G6. 可调渲染参数集中化（对齐现有 tunable 约定）
PiliPlus DanmakuOptions 集中了一批用户可调项，建议收进一个 DanmakuRenderConfig（仿 WorkbenchLayoutConfig）：weightThreshold、showArea（滚动弹幕只占屏幕上部比例）、lineHeight、fontScale/strokeWidth（已有）、duration/staticDuration、fixedVelocity、hideScroll/hideTop/hideBottom/hideSpecial/hideColorful、massiveMode、opacity。

### G7. 帧节奏
- 我们用协程 delay(33) 读 player.currentPosition 驱动（~30fps，暂停时 100ms）。状态在 draw 作用域内读取只触发重绘不重组，方向正确。
- 可进一步：播放时用 withFrameNanos/Choreographer 对齐 vsync（VR 72Hz 更顺滑），并确保弹幕层是独立重绘层不拖累其它面板。优先级低于 G1-G4。

## 3. 建议落地顺序（垂直切片）
1. **G3 准入 + 权重过滤 + G6 配置对象**：纯 Kotlin 逻辑，JVM 可测（改 scheduleDanmakuLanes 返回接纳/丢弃，加阈值）。风险最低、立刻止住爆量。
2. **G1 分桶 + G2 活动集**：改数据装载与 DanmakuOverlay 绘制循环；配套 metrics/车道惰性化。
3. **G4 字形缓存（Picture/Bitmap）**：渲染层优化，活动集稳定后做，高分辨率 VR 收益最明显。
4. G5/G7：观感与顺滑度收尾。

## 4. 合规
canvas_danmaku 与 PiliPlus 为开源参考，仅借鉴算法思路与参数模型（分桶、活动集、准入、权重、固定速度、字形缓存）；我方为独立 Kotlin/Compose 实现，不复制其 Dart 代码。
