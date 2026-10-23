# PRD：从舞台侧关闭工作台（幕布 toggle + 虚空点击）

- **任务**：`.trellis/tasks/10-07-stage-tap-cannot-close-workbench/`
- **状态**：in_progress（R1 已实现并真机通过；R2 为本轮新增需求）
- **报告来源**：用户真机实测（2026-10-07），「播放中点击幕布打开视频列表无法触发关闭」
- **已确认非回归**：`app/` 在 `d2ead83f83ba5398b99923f910b5caaf207c8768..HEAD` 之间**逐字节相同** → 与 Phase 1 无关。

---

## R2（本轮新增，用户 2026-10-07 澄清）：虚空点击关闭

用户原话：

> 我想要的是在"视频列表展开时，点击任何没有实体的**虚空**（也就是扳机点击**没有指向任何
> （可点击 & 不可点击的）对象**时），触发关闭视频列表、展示幕布的行为。

### R2.1 与 R1（幕布 toggle）的区别

| | R1 幕布 toggle（已实现并实测通过） | R2 虚空点击（本轮） |
| --- | --- | --- |
| 判定 | **几何**命中舞台平面矩形（2.1×1.2 m） | 射线 **`rightEntity == null`**（什么都没打中） |
| 覆盖 | 仅舞台那块矩形 | 所有方向的真空 |
| 依赖 | `StageRayTargeting`（自建几何判定） | `PointerInfoSystem.rightEntity` / `leftEntity`（`lineSegmentIntersect`，5 m 线段） |

两者**不是同一集合**，且互补：某些方向几何命中舞台但射线打到了别的实体；另一些方向射线打空但不在舞台矩形内。

### R2.2 可达性风险（**必须先由实测确认**，不得当作已知）

舞台**没有命中几何**（`videoSurface` 为 `MeshCollision.NoCollision`，这正是当初需要
`StageRayTargeting` 的原因），所以"指向舞台"在射线意义上**本来就常为 `null`**。
但后方那块板会吸掉大部分前方射线：

```text
WorkbenchOuterDismiss: scale [9, 6, 0.025], translation [0, 1.25, 2.2]
→ x ∈ [−4.5, 4.5], y ∈ [−1.75, 4.25], z = 2.2
```

自眼睛（≈1.6 m）朝前，射线要在 z=2.2 处**离开该矩形**才算打空 —— 约向下 >50°、向上 >50°、
或左右 >64°。此外：

- 若 `MediaRoom` 环境网格**有碰撞**，则指地板/墙壁是"命中不可点击对象" → 按用户规格**不关**，
  虚空只剩更窄的方向；
- 若环境**无碰撞**，则地板/墙壁方向就是真空 → 手势自然可达。

**`MediaRoom` 的碰撞设置在源码中无法判定**（由 GLXF / glTF 决定）→ 列为实测项。

### R2.2b 已确证的真正元凶：中心面板挡住了舞台（用户反馈「幕布很难点到」）

用户 2026-10-07 反馈：**"主要是幕布很难点到"**。源码已确证其机制：

| 实体 | 尺寸 / 位置 | 是否可命中 | 证据 |
| --- | --- | --- | --- |
| 舞台视频表面 | 1.6×0.9 m（×scale），z=0 | **不可命中** | `:1569` `videoSurface.setComponent(Hittable(MeshCollision.NoCollision))` |
| 弹幕层 | 同尺寸，z=−0.01（**在舞台之前**） | **不可命中** | `:1834` `Panel(..., MeshCollision.NoCollision)` |
| 压暗层 | 同尺寸，z=+0.01 | **不可命中** | `:1811` 同上 |
| **中心面板** | **1.5×1.2 m**，z=−0.52（在舞台之前 52 cm） | **可命中** | `centerContentPanelRegistration` 的 `UIPanelSettings` **未传 `input`** → 默认 `PanelInputOptions` → 保留 raycast collider |
| 左右 rail | 0.7×0.9 m，yaw ±45° | **可命中** | 同上（默认 input） |

**结论**：中心面板 1.5×1.2 m 几乎完全覆盖舞台 1.6×0.9 m，且它是**可命中**的。
所以工作台展开时瞄准"幕布"，射线实际命中的是**中心面板** → R1 的
`occludedByInteractiveEntity` 守卫（默认 ON）判定被面板遮挡 → **toggle 被抑制** → 这就是"很难点到"。

**同时解释了为什么"打开"没问题**：工作台收起时面板被 `Visible(false)`（k82 已确证：**会移除命中测试**），
射线直接打到后方那块无 `Panel` 组件的板 → 守卫放行 → 打开正常。

**因此**：
- 关闭**不应**依赖命中舞台——结构上被中心面板挡死。**R2 虚空点击正是为此**（且已在实现中）。
- **遮挡守卫必须保持 ON**。若关掉它，瞄准中心面板时将同时触发"面板点击 + 工作台 toggle"，
  正是 R2.3.2 要避免的双重动作。**不建议**为了让幕布可点而关掉守卫。
- 若要真正让"幕布"可点（可选，非本轮）：需要缩中心面板、或把面板从守卫中排除、
  或改命中几何——均涉及产品/场景取舍，**不在本轮擅自改**。

诊断行会把这条链路摆到台面上：工作台展开时瞄舞台应打出一行
`hitEntity=<中心面板实体>` + `occludedByPanel=true` + `outcome=NONE`。

### R2.3 需求

- R2.3.1 工作台**可见**时，任一手控制器的扳机**上升沿**，若该手射线 `entity == null`
  → 关闭工作台（与 R1 共用同一个关闭入口，不新增第二个状态转换 owner）。
- R2.3.2 命中**任何**实体（可点击或不可点击）→ **不**触发虚空关闭（用户的明确规格）。
- R2.3.3 保留 R1 的幕布 toggle（已实测通过；且覆盖"板吸收射线但指向舞台"的情形）。
- R2.3.4 **诊断**：每次扳机按下（工作台可见时）打印一行，含
  `side` / `hitEntity`（或 `null`）/ `toggleFired` / `voidFired` / 是否被板收起，
  使一次实测即可判定 R2.2 的两个未知。

### R2.3.5 已确认的范围（用户 2026-10-07）

| # | 问题 | 决定 |
| --- | --- | --- |
| 1 | 作用范围 | ✅ **任意工作台可见状态**（视频列表 / 搜索 / BROWSE / CONTEXT 都算），不限于"视频列表展开时"。理由：语义上"点虚空就收起"应当一致 |
| 2 | R1 幕布 toggle | ✅ **保留**。它与虚空点击互补：覆盖"射线被后方板吸收、但指向舞台"的情形 |
| 3 | R2.2 的可达性未知 | ⏳ **由实测 + 诊断行判定**（V5）。若证明那块 9×6 m 板是虚空手势不可达的原因，再单独决策是否缩板——**不在本轮擅自改场景** |

### R2.4 非目标

- 不改 `WorkbenchOuterDismiss` 的场景授权与几何（`app/scenes` 不动）。
  若实测证明那块板是虚空手势不可达的原因，**再单独决策**是否缩板或放行——不在本轮擅自改。
- 不做"点击中心面板空白关闭"（那是另一条路径，`isBlankSpaceDismissable`，且仅 `WORKBENCH_EMPTY`）。
- 不因虚空关闭而放宽 k82 的松手抑制（虚空判定发生在**按下**，不是松手）。

### R2.5 验收

| # | 条件 | 方式 |
| --- | --- | --- |
| V1 | 工作台可见时，指向**真空**按扳机 → 收起、露出幕布 | **头显** |
| V2 | 指向面板/列表项按扳机 → 面板正常响应，**不**顺带关闭 | **头显** |
| V3 | 指向不可点击对象（若环境有碰撞）→ **不**关闭（符合 R2.3.2） | **头显** |
| V4 | R1 的幕布 toggle 仍工作（长按不翻转；长按 >500 ms 松手不被关） | **头显** |
| V5 | 诊断行能读出 `hitEntity` 是 `null` 还是某个实体 | **头显** + `adb logcat` |
| V6 | 单一 `ExoPlayer` / 单一视频 Surface 不变量仍成立 | 代码审查 |

---

## R3（本轮新增，用户 2026-10-07 澄清）：让那块板彻底不可命中

用户原话：

> 那块板我能指向。也能判定。也能通过点击关闭。但是指向他会有个**目标点指示器**，
> 比较反直觉，所以干脆改成 **void(null)**。

### R3.1 事实更正（推翻 R2.2b 的"点不到"推断）

R2.2b 曾推断"幕布很难点到 = 中心面板挡住 + 守卫抑制"。**用户实测表明该推断不适用于当前构建**：
那块板**可以**被指向、可以被判定、并且**点击确实能关闭工作台**。
真正的问题是**指向它会出现目标点指示器**——对一块本应"什么都没有"的区域而言反直觉。

因此 R3 的目标不是"让关闭可用"，而是**消除指示器**，并把关闭职责一并交给 void 路径。

### R3.2 已确认的技术事实

| 事实 | 证据 |
| --- | --- |
| 目标点指示器**不是 app 画的**，是 SDK/ISDK 对**可命中实体**的内置命中标记 | 全仓 grep `indicator\|reticle\|crosshair\|aimDot\|targetDot` 在 app 下**无代码命中** |
| 那块板的可命中性**只**来自 Kotlin | `attachOuterDismissInput()` `:669` `entity.setComponent(Hittable())`；导出制品 `Composition.glxf` 中 `WorkbenchOuterDismiss` 节点**不含任何组件**（`Visibility: visible` 是默认值被省略） |
| k82 的 workaround 现在**过时** | k82 当年把它从 `Visible(false)` 改为"引擎可见 + 材质 alpha 0"，目的正是**让它可命中**。现在我们要的恰恰是不可命中 |

### R3.3 需求

- R3.3.1 **移除** `entity.setComponent(Hittable())`，使该板不再可命中 → 不再出现目标点指示器。
- R3.3.2 **清理随之变成死代码的输入监听**：不可命中后 `onClick` 永不触发，
  不得留下"永不运行但看似仍在工作"的监听。删除监听体与 `attachOuterDismissInput()` 调用点
  （`:490`），或收敛为有注释的 no-op —— 但必须诚实，不能是残留。
- R3.3.3 把该板设为 **`Visible(false)`**。这在现在既安全又更优：它本来就是"移出命中测试"的机制，
  同时**关掉 k82 遗留的未决风险**（alpha-0 的 `Blend` 材质可能写深度、遮挡工作台后方的环境几何）。
  材质保持原样（k82 的 `Blend` 改动变得不必要但无害）——在注释里说明。
- R3.3.4 **不改 `app/scenes`**：场景节点、transform、材质全部保持原样。
- R3.3.5 **保留 R1 与 R2**：void 路径成为"点击工作台之外/空旷处"关闭的**唯一 owner**。

### R3.4 必须诚实处理的耦合风险

板不可命中后，"点工作台之外关闭"**完全**依赖射线在该区域打到 **nothing**。
若 `MediaRoom`（或后方其它东西）**可命中**，射线会落在它上面 → `entity != null` → **void 不触发**
→ 我们会**失去"点外面关闭"**，而不是修好指示器。

**无法静态判定 → 必须由诊断行确认**：在旧的板区域按扳机，诊断行应显示
`hitEntity=null`（void 接管）或**吸收它的那个实体**。

### R3.5 顺带记录（本轮不实施）

板不可命中后，k82 的 `suppressOuterDismissUntilMs` 守卫**基本失去作用**——它当年存在的理由
（松手那一击打到板）已不存在，因为 R1 与 R2 都作用于**按下上升沿**，不再有"松手点击"路径。
**本轮保留该守卫**（无害），移除属越界；仅记录。

### R3.6 验收

| # | 条件 | 方式 |
| --- | --- | --- |
| W1 | 指向原来那块板的方向**不再出现目标点指示器** | **头显** |
| W2 | 在该区域按扳机 → 工作台**关闭**（void 路径接管） | **头显** |
| W3 | 诊断行显示该区域是 `hitEntity=null`，或**明确指出是哪个实体吸收了射线** | **头显** + `adb logcat` |
| W4 | 指向面板时**仍**出现指示器（面板本就该可交互） | **头显** |
| W5 | 幕布后方的环境几何**不再消失**（若此前有该现象） | **头显** |
| W6 | R1（幕布 toggle）与 R2（虚空关闭）仍工作 | **头显** |
| W7 | `app/scenes` 零改动 | `git status` |

---

## 1. 现象（R1 原始报告）

播放中点击幕布 → 工作台/视频列表**打开**（符合预期）。
再次点击幕布 → **不关闭**，无任何反应。

## 2. 根因：五处**各自有意**的设计叠加，使"再点幕布关闭"从来不存在

这不是一个 bug 落在某一行，而是五个独立决定的合成结果。逐条附证据：

| # | 决定 | 位置 | 效果 |
| --- | --- | --- | --- |
| 1 | 舞台主操作在 `PLAYBACK` 态是**空操作** | `PlaybackCanvasContracts.kt` `PrimaryStageAction`：`QUIET_WATCH/BROWSE/CONTEXT -> PLAYBACK`，`PLAYBACK -> PLAYBACK` | 即使事件送达，也不会切回 `QUIET_WATCH` |
| 2 | 幕布可见时**明确不关闭**工作台 | `SpatialVideoSampleActivity.kt:2000-2003`：`if (workbench visible) { showTransportOverlay() }`，注释写明 *"Workbench dismissal belongs exclusively to WorkbenchOuterDismiss. A MediaStage action must not cancel a just-opened panel route."* | 幕布操作被降级为"只显示 transport" |
| 3 | 幕布可见时**舞台输入整体关闭** | `:1927-1928` `isStageInputEnabled() = !workbench.visible` | `AnalogMediaStageTuningSystem` 不运行 → **连 `onStagePrimaryAction()` 都不会被调用**，所以第 2 条的分支实际上连"只显示 transport"都做不到 |
| 4 | 中心面板空白**仅在 `WORKBENCH_EMPTY` 路由可收起** | `CenterContentWorkspace.isBlankSpaceDismissable`；`docs/ui-structure-and-terminology.md` §5.3 | 视频列表（`RECOMMENDATIONS`）等**内容路由**下点空白是惰性的（有意防误触） |
| 5 | `IdleTimeout`（唯一"自动收起"路径）**在生产代码中从不派发** | 全仓 grep：`IdleTimeout` 只出现在 `ImmersivePlaybackCanvasHostTest.kt:39,45` | 没有自动收起兜底 |

**合成结果**：唯一能关掉工作台的是 `WorkbenchOuterDismiss`（场景里 z=2.2 的 9×6 m 透明命中层）
以及 `WORKBENCH_EMPTY` 下的中心面板空白。**幕布本身从来不是关闭入口**，
而且它在工作台可见时连输入都不接收。

补充证据（k82 的既有结论，必须保留）：`WorkbenchOuterDismiss` 曾因 `Visible(false)` 同时退出命中测试而
完全不可点；修复后点工作台**之外**才可收起。同时 k82 记录了一个真实回归：*按下*幕布召唤、
*松手*那一击会穿透幕布打到后方 `WorkbenchOuterDismiss`，导致刚打开就被收起——
因此有 `suppressOuterDismissUntilMs`（`:2913` = **500 ms**）这个守卫。
**任何"让幕布可关闭"的改动都不能重新引入这个回归。**

## 3. 已决策：选项 A（幕布改为 toggle）

用户 2026-10-07 决定：**幕布 = toggle（关↔开）**。范围**仅限 `PLAYBACK` 态**（即
`QUIET_WATCH -> PLAYBACK` 打开、`PLAYBACK -> QUIET_WATCH` 关闭）；
`BROWSE` / `CONTEXT` 下点幕布仍按现状切到 `PLAYBACK`，本次**不改**（避免超出报告范围）。

### 3.1 实现要点（三处必须同时改，缺一不可）

**(i) reducer 增加关闭转移** — `spatial-workbench-core/PlaybackCanvasContracts.kt`

```kotlin
PlaybackCanvasEvent.PrimaryStageAction ->
    when (state.canvas) {
      QUIET_WATCH -> PLAYBACK        // 打开
      PLAYBACK    -> QUIET_WATCH     // 关闭（新增；原为 no-op）
      BROWSE, CONTEXT -> PLAYBACK    // 维持现状
    }
```

**(ii) 触发语义与"每帧重新布防"的真相** — `AnalogMediaStageTuningSystem.kt`

> ⚠️ **本节原始判断有误，已于实现阶段用 SDK 字节码更正（保留下方更正记录）。**

**更正后的事实**（`javap -c com.meta.spatial.toolkit.Controller`）：

| 方法 | 字节码 | 语义 |
| --- | --- | --- |
| `isDown(bits)` | `buttonState & bits` | **电平**（按住期间持续为真） |
| `isPressed(bits)` | `buttonState & changedButtons & bits` | **上升沿**（仅按下那一帧为真） |
| `isReleased(bits)` | `~buttonState & changedButtons & bits` | **下降沿** |

原实现 `if (controller.isPressed(TriggerR) || isPressed(TriggerL)) onStageAction()` **本来就是边沿触发**，
每次按压只调用一次——**不是**每帧。所以：

1. 原判断"电平触发会让 toggle 每帧翻转"**不成立**；但**改为由 `isDown` 自行推导边沿**仍是必要的，
   因为需要**同一个信号**同时给出边沿与"按住"两件事（下一点）。
2. **k82 文档声称的"按住期间每帧重新布防"当年从未真正生效**——布防实际只在按下那一帧发生一次，
   真正的防线是"从按下边沿起算的 500 ms 固定窗口"，而这**正是
   `docs/research/workbench-dismiss-hit-layer.md` §10 自己警告过的、会因长按压而复发的形态**。
   即：长按幕布超过 500 ms 再松手，松手那一击会打到后方 `WorkbenchOuterDismiss` → **潜在复发窗口**。
3. 因此实现新增了**真正的**每帧布防回调 `onStageTriggerHeld`（由 `isDown` 驱动，按住期间每帧调用），
   把上述潜在窗口一并堵上。这才使 k82 的不变量**首次真正成立**。

**实现形态**（两件事都从同一个 `isDown` 信号导出，故不可能互相矛盾）：

```kotlin
val triggerHeld = isDown(TriggerR) || isDown(TriggerL)
val risingEdge  = triggerHeld && !isTriggerHeld(isRight)
setTriggerHeld(isRight, triggerHeld)      // 每帧、且在 targeting 判定之前
if (targetsStage && triggerHeld) {
  onStageTriggerHeld()                     // 每帧：只重新布防 500 ms
  if (risingEdge && actionEnabled && !occluded) onStageAction()   // 边沿：toggle
}
```

**操作前提**：`stageInputEnabled` 与 `stagePrimaryActionEnabled` 两个门控必须同时参与
**targeting 的解析**（`targetingEnabled = analogEnabled || actionEnabled`）。
原实现用单一 `enabled` 同时置空 `frame` 与 `targetsStage`，这正是"工作台能开、不能关"的直接原因。

**(iii) 拆分门控：摇杆仍然关闭，触发动作放行** — 同文件 `:57-74` 与 `SpatialVideoSampleActivity.kt:1927`

现状 `stageInputEnabled = !workbench.visible` **同时**门控摇杆与触发。原因写在 `:34-36`：
"a stick or trigger that still drove the stage underneath made the panels unusable"。
所以**不能**直接翻转它（会把摇杆一并打开，重新弄坏面板操作）。必须拆成两个门控：

| 门控 | 工作台隐藏 | 工作台可见 |
| --- | --- | --- |
| 摇杆缩放/曲率（analog） | 开 | **保持关闭** |
| 触发主操作（toggle） | 开 | **放行** |

### 3.2 必须保留 k82 的不变量（最大风险点）

k82 记录的真实回归：*按下*幕布召唤 → *松手*那一击穿透幕布（幕布无命中几何）打到后方
`WorkbenchOuterDismiss` → 刚打开就被收起。防线是 `suppressOuterDismissUntilMs`
（`:2913` = 500 ms）。

> **更正（见 §3.1(ii)）**：本文原先写"由 `onStagePrimaryAction()` **在按住期间每帧重新布防**（`:1999`）"——
> 这与事实不符。`onStagePrimaryAction()` 由 `isPressed`（**上升沿**）触发，所以布防**只发生一次**；
> k82 依赖的"每帧"从未存在。**被实现修正后，这一不变量才首次真正成立。**

因此设计为两个回调，且**必须同时存在**：

- `onStageAction()` —— **仅上升沿**，执行 toggle
- `onStageTriggerHeld()` —— **由 `isDown` 驱动、按住期间每帧调用**，只做 `suppressOuterDismissUntilMs` 重新布防

这样 toggle 每次按压只发生一次，而 500 ms 窗口在整个按压期间被持续刷新，
**长按 >500 ms 再松手也不再复发**（这是原实现遗留的潜在窗口）。

### 3.3 第二个风险：触发落在面板上会造成"双击"

面板用 `clickButtons` 响应触发（`video_selector_panel` 明确用
`PanelInputOptions(ButtonA or ButtonTriggerL or ButtonTriggerR)`）。而 `StageRayTargeting` 是
**纯几何**判定，不关心前方有没有面板。若射线穿过面板打到后方舞台平面，
就会**同时**触发面板点击**和**工作台 toggle。

**设计**：toggle 只在几何命中舞台**且同一射线未被交互面板吸收**时才放行
（用 `pointerInfo.rightEntity` / `leftEntity`：命中面板实体则不放行）。
该项**无法静态确证**，列为头显验证项；并在 debug 开关里提供"放行/抑制"两态以便一次实测判定。

## 4. 非目标

- 不改 `WorkbenchOuterDismiss` 的命中层机制与场景授权（k82 已验证有效）。
- 不破坏：**幕布按下时的松手一击绝不能被当成"点工作台外部"**（k82 回归）。
- **不改 `BROWSE` / `CONTEXT` 下点幕布的行为**（维持切到 `PLAYBACK`）。

### 4.1 实现后需确认的行为变化（DEBUG 入口）

`AnalogWorkbenchSummonSystem`（DEBUG 按 A）用 `isPressed`（上升沿）调用 `onSummon()` →
`onStagePrimaryAction()`。由于后者现在执行 **toggle**，**按 A 变成了"切换"而不是"召唤"**：
工作台已打开时按 A 会**关闭**它。

PRD 未规定此项。当前判断是"与幕布 toggle 语义一致，可接受"，但该系统的**设计目的是逃生通道**
（坏曲率让幕布点不到时用 A 进工作台），因此需确认：
若希望 A 保持**幂等召唤**（只在未打开时打开），改法是让 `AnalogWorkbenchSummonSystem` 改调一个
"仅打开"的入口，而不是 `onStagePrimaryAction()`。**待确认。**
- **不因本次改动而打开摇杆**（`AnalogMediaStageTuningSystem` 的 analog 部分保持 `!visible`）。
- 不改单一播放器 / 单一视频 Surface 不变量。
- 不改 `PanelSlot` / 可见性 owner 语义（那是 `10-07-immersive-ui-theme-runtime-assessment` 的 Phase 2）。

## 5. 验收（选定选项后细化）

| # | 条件 | 方式 |
| --- | --- | --- |
| A1 | 播放中点幕布 → 工作台打开（不回退） | **头显** |
| A2 | 再点幕布 → 工作台**关闭**；再点 → 再打开（可连续往返） | **头显** |
| A3 | **按住触发不放不会来回翻转**（边沿触发生效） | **头显** |
| A4 | **k82 回归不重现**：按住扳机指向幕布 → 松手后 `WORKBENCH_EMPTY` **保留**；长按 >500 ms 再松手也不关 | **头显** |
| A5 | 点工作台**之外**仍然收起（k82 已修复行为不退化） | **头显** |
| A6 | 工作台可见时**摇杆不会缩放/弯曲舞台** | **头显** |
| A7 | 点面板上的可点元素（列表项 / 按钮）**不会顺带关闭工作台** | **头显** |
| A8 | reducer 的 toggle 转移有 JVM 单测 | `:spatial-workbench-core:test` |

## 6. 关键文件

- `SpatialVideoSampleActivity.kt`：`:1988-2010`（`onStagePrimaryAction`）、
  `:1927-1928`（`isStageInputEnabled`）、`:636-670`（`attachOuterDismissInput` + 抑制守卫）、
  `:2052-2058`（`openSearchCanvas` 也重新布防）、`:2913`（500 ms 窗口）
- `spatial-workbench-core`：`PlaybackCanvasContracts.kt`（`PrimaryStageAction` / `IdleTimeout`）
- `RecommendationUi.kt` / `ImmersiveCenterContentPanel.kt`（`isBlankSpaceDismissable`）
- `docs/ui-structure-and-terminology.md` §5.3（收起归属）、§4.2（画布状态机）
- `docs/research/workbench-dismiss-hit-layer.md`（k82 根因链与不变量）
