# Audit — Docs, Trellis Specs, and Task History

Scope: `docs/**`, `.trellis/spec/**`, `.trellis/tasks/**`, and the project kanban board
(`.dsh-kanban.json`). Method: direct file reading plus whole-word searches over `app/src`,
`spatial-workbench-core/src`, `spatial-workbench-compose/src`, `app/scenes`, and
`app/src/main/components`. **`temp/**` (vendored third-party samples) was excluded from every search.**

This file was consolidated by the main session after the delegated audit over-ran; every statement
below is from direct reading of the cited file, not from a sub-agent summary. Sections marked
**[UNVERIFIED]** were not independently re-read and must not be treated as established.

Purpose: (1) separate what the project's own docs *claim* from what the source *does*, and
(2) inventory already-planned work so the new architecture task does not duplicate it.

---

## Verdict

The project is unusually honest about its own state. `docs/ui-structure-and-terminology.md` already
carries a three-way `[实现] / [契约] / [未接线]` tagging system and a §7 "naming drift and leftovers"
list that independently reaches several of the conclusions in
`docs/immersive-theme-runtime-design.md`. The gap is **not** that the team believes unimplemented
things are done — it is that:

- the doc's own `[契约]` tag was still **too generous** for the theme geometry (see A2), and
- one concrete statement (`immersive-ui-low-code-architecture.md:10`) is now **factually stale**, and
- at least one `[实现]` tag is **wrong** (see A5).

---

## A. What the docs claim vs. what the source does

### A1. `docs/immersive-ui-low-code-architecture.md` — the document polices itself

The document states its own limitation at the top:

> `:7` — 本文描述的是目标架构和已确认的设计边界，**不代表功能已经实现**。

So the whole document is explicitly a target statement. Its structural claims (space workbench,
layout modes, content navigation, panel shell, overlay pipeline, theme system) are therefore
**target/design**, not evidence of implementation. This is the correct reading, and it matches the
AGENTS.md warning that many items are unimplemented.

Its concrete, checkable targets that are **not** implemented (each independently confirmed against
source in the main design doc):

| Target claim | Doc location | Source reality |
| --- | --- | --- |
| theme JSON with per-slot anchor + `geometry` + `zOrder` + `overflow` | `:959-995` | no parser, no reader — `SlotPlacement` geometry has zero consumers |
| `group: "defaultCinemaPlaybackCanvas"` referenceable composition | `:1014-1032` | `DefaultCinemaPlaybackCanvasGroup` exists in core as a fixture; no resolver, no renderer |
| theme actions (`openRoute`/`setPlaybackSpeed`/`switchTo2D`/…) callable from theme JSON | `:1057-1068` | `ThemeAction` sealed interface exists; **no dispatcher**; `appState` methods are unreachable from a theme |
| slot offset/rotation/size applied to Spatial entities | `:920-957` | §2.1 of the main design doc: zero consumers |
| Multi-surface / multi-layer danmaku with shared lane state + occlusion domain | `:708-734` | `DanmakuSurfaceGroup` etc. exist in core + validator + 225-line test; `app/` has **zero** references |
| module split `:spatial-workbench-meta`, `:spatial-workbench-media`, `:demo-cinema`, `:demo-cockpit` | `:1076-1094` | `settings.gradle.kts` includes only `:app`, `:spatial-workbench-core`, `:spatial-workbench-compose` |
| Phase 3–6 implementation plan | `:1123-1156` | not started; Phase 3 partially exists as core contracts only |

### A2. `docs/ui-structure-and-terminology.md` — the `[契约]` tag understates the gap

This doc is accurate and useful. Its tagging legend is at `:23-25`:

```text
[实现]   —— 已在 runtime 生效。
[契约]   —— 仅存在于 spatial-workbench-core 的类型/枚举，尚未被 runtime 使用。
[未接线] —— runtime 已声明该名称，但没有对应实体或行为。
```

Its §6.3 table (`:639-645`) tags the theme geometry as follows:

| Term | Code | Tag at `:641-645` |
| --- | --- | --- |
| 主题布局 | `ThemeLayout` / `SlotPlacement` | **[契约]** |
| 主题画布 | `WorkbenchCanvas`（core） | **[契约]** |
| 组件树 | `ComponentNode` / `ComponentKind` / `ComponentGroup` | **[契约]** |
| 系统工具模块 | `SystemToolbarModule` / `ToolbarModule` | **[契约]** |
| 画中画坞 / PiP dock | `PipDock` / `ContentExclusion` | **[契约]** |
| 呈现策略 | `PanelPresentationPolicy` | **[契约]**（由主题驱动） |

**Correction needed**: `PanelPresentationPolicy` is tagged `[契约]`, but it **is** read at runtime —
`PlaybackCanvasContracts.kt:82-86` reads `theme.presentationPolicies` inside
`PlaybackCanvasReducer.visibleSlots`, which is reached from the live
`ImmersivePlaybackCanvasHost` (`SpatialVideoSampleActivity.kt:372-373`). It is therefore
**[实现]** (partially), and it is the **only** theme field with a runtime consumer.

Conversely, `SlotPlacement`'s `[契约]` tag is correct but still reads as "defined, awaiting a
consumer". The stronger fact is that its fields have **no consumer at all** — a whole-repo search of
`app/src/main` for `offsetMeters|placement(|SpatialAnchor|Meters3|ThemeLayout|sizeMeters` returns
zero matches.

`PanelSlot`'s own wiring table at `:647-659` is accurate, including the three `[契约]` members:

```text
FOCUS            [契约]
SHORTS_DETAILS   [契约]
SHORTS_COMMENTS  [契约]
```

### A3. `[契约]` / `[未接线]` full inventory

`[未接线]` (2 occurrences):

| Location | Item |
| --- | --- |
| `:206` | `PLAYBACK_CONFIG -> (无实体) -> SYSTEM_TOOLBAR` |
| `:652` | `SYSTEM_TOOLBAR <- mr_panel（NAVIGATION）、PLAYBACK_CONFIG [未接线]` |

`[契约]` (5 occurrences):

| Location | Item |
| --- | --- |
| `:656` | `PanelSlot.FOCUS` |
| `:657` | `PanelSlot.SHORTS_DETAILS` |
| `:658` | `PanelSlot.SHORTS_COMMENTS` |
| `:641` | `ThemeLayout` / `SlotPlacement` |
| `:642` | `WorkbenchCanvas`（core） |
| `:643` | `ComponentNode` / `ComponentKind` / `ComponentGroup` |
| `:644` | `SystemToolbarModule` / `ToolbarModule` |
| `:645` | `PipDock` / `ContentExclusion` |
| `:672` | `SpatialDepthRelation`（`ROOT`/`FRONT_OF_PARENT`/`BEHIND_PARENT`） |
| `:673` | `CanvasOverflowPolicy`（`CLIP`/`VISIBLE`） |
| `:684` | `PlaybackBrowseOrigin` / `BrowseContinuation` / `BrowseOriginResolver` |
| `:717` | `InteractionColors`（hover/pressed/focus/disabled） |
| `:720` | `SpatialTheme` + `CinemaTheme.create()` |
| `:721` | `ThemeValidator` / `MediaOverlayValidator` |
| `:735` | `DanmakuMode`（`OFF`/`FLAT`/`SPATIAL`） |

> Note: lines `:640-645`, `:672-673` etc. are inside multi-row tables; a single row can carry one tag.

### A4. The doc's §7 self-declared drift list

`:743-767` lists 8 items, explicitly "只在本文记录，本次不改动". They are accurate and several map
directly onto the new design:

| §7 item | Content | Relation to the new design |
| --- | --- | --- |
| 1 | Three "canvas" concepts share a name across layers (`WorkbenchCanvas` / `PlaybackCanvas` / `WorkbenchContent`) | §6.2 owner table; D3 |
| 2 | `PanelSlot` members are not 1:1 with entities (`CONTEXT` serves both rails; `BROWSE` carries the centre; `ACTION_SHEET` carries the keyboard) | §2.6 row 54b |
| 3 | `rightRailHeight = 0.58f` exists but `modePanelRegistration()` uses `leftRailHeight` (0.90 m); the constant is unconsumed | Phase 2 will fix by construction (per-panel placement) |
| 4 | `NORMAL` unconditionally adds both rails while `PlaybackCanvasReducer` does not request `CONTEXT`; app reducer wins | §2.6 rows 53/54; D2 |
| 5 | `PLAYBACK_CONFIG`, `SHORTS_ACTIONS`, `WorkbenchPresentation.SHORTS`, `WorkbenchContent.FOCUS` have no entity or behaviour | §2.1 rows 10a/10b (stronger: no app-layer producer at all) |
| 6 | Unused resources: `window_2d.xml`, `debug.xml`, `ids.xml` `debug_panel` + `workbench_root` | housekeeping; Phase 3.3a |
| 7 | `PanelSlot.FOCUS` / `SHORTS_DETAILS` / `SHORTS_COMMENTS` referenced only by the `CinemaTheme` fixture | A3 above |
| 8 | `ImmersiveLayoutMode` and `WorkbenchPresentation` both express landscape/portrait; core also has `FOCUS`/`EDIT` | schema design must not add a third |

### A5. One `[实现]` tag is wrong

`docs/ui-structure-and-terminology.md:731` tags the danmaku lane scheduler as `[实现]`:

```text
| 泳道调度 | `DanmakuLaneAssignment` / `prepareDanmaku()` / `scheduleDanmakuLanes()`（`DanmakuLaneScheduler.kt`） | 实现 |
```

But `DanmakuLaneScheduler` is **not reachable at runtime**: its only production caller
`ViriViriAppState.loadDanmaku()` has zero call sites, and `mergeDanmakuEvents` is likewise
unreachable. The live lane state is inside `DanmakuCanvasRuntime`. This tag should be
**[未接线]**. Confirmed in `research/audit-danmaku-runtime.md`.

### A6. One concrete doc statement is now stale

`docs/immersive-ui-low-code-architecture.md:10`:

> 当前项目没有可用的 `mse-agent`。在新增固定场景对象前必须先安装 Meta Spatial Editor；
> 不得将新的固定空间布局硬编码为 Kotlin `Entity.create()`。

`mse-agent` **is** installed and wired:

```text
D:\Program Files\Meta Spatial Editor\v16\Resources\mse-agent.exe
```

and `app/build.gradle.kts` already points the scene exporter at the same version directory:

```kotlin
spatial {
  scenes {
    cliPath.set("D:\\Program Files\\Meta Spatial Editor\\v16\\Resources\\CLI.exe")
  }
}
```

with `tasks.named("preBuild") { dependsOn("export") }`. The companion rule in the same sentence
("do not hardcode fixed spatial layout as Kotlin `Entity.create()`") is still valid — and is in fact
currently violated in 14 places (see `research/audit-spatial-scene-and-grab.md` §7).

Consequently, any plan gated on "install Meta Spatial Editor first" is gated on nothing.
**The real prerequisite is authoring the semantic anchor nodes, not installing the tool.**

---

## B. Trellis spec inventory and binding conventions

`python ./.trellis/scripts/get_context.py --mode packages` reports a single-repo project with two
spec layers: `backend` and `frontend`.

```text
.trellis/spec/backend/
  database-guidelines.md  directory-structure.md  error-handling.md
  index.md  logging-guidelines.md  quality-guidelines.md
.trellis/spec/frontend/
  bilibili-recommendation-playback.md  component-guidelines.md
  directory-structure.md  hook-guidelines.md  index.md
  media3-surface-handoff.md  quality-guidelines.md
  quest-spatial-panel-debugging.md  state-management.md  type-safety.md
.trellis/spec/guides/
  code-reuse-thinking-guide.md  cross-layer-thinking-guide.md
  index.md  opencode-provider-management.md
```

Specs a theme-runtime task must follow (registered in this task's `implement.jsonl` / `check.jsonl`):

| Spec | Binding constraint it carries |
| --- | --- |
| `frontend/state-management.md` | one owner per state concern; no second source of truth |
| `frontend/component-guidelines.md` | Compose component + panel-content conventions |
| `frontend/type-safety.md` | sealed interface / enum contract style for pure Kotlin |
| `frontend/quality-guidelines.md` | quality gate for the pure-Kotlin test layer |
| `frontend/media3-surface-handoff.md` | **single ExoPlayer + single active video Surface** invariant |
| `frontend/quest-spatial-panel-debugging.md` | existing panel visibility / hit-test / reshape pitfalls |
| `frontend/directory-structure.md` | core / compose / app module boundaries |
| `guides/cross-layer-thinking-guide.md` | core contract → app adapter separation |
| `guides/code-reuse-thinking-guide.md` | reuse existing contracts rather than duplicating models |

Additional binding conventions come from `AGENTS.md` (not from `.trellis/spec/`):

1. **Static spatial placement → Meta Spatial Editor / mse-agent.** Kotlin owns dynamic state, input,
   lifecycle and controlled transform behaviour only.
2. **One owner per user interaction.** Stage click listeners, outer-dismiss geometry, panel
   callbacks and hover/touch fallbacks must not independently perform the same show/hide transition.
3. **Every panel change must document** owner, parent/anchor, visibility state, input/hit-test
   ownership, relative depth, and video-surface ownership (normally none).
4. **Verification cadence**: pure reducer → targeted JVM test; shared Compose →
   `:spatial-workbench-compose:testDebugUnitTest`; app/resources/panel-registration/scene/cross-module
   → `.\scripts\build-windows-debug.ps1`.
5. **Terminology doc sync duty.** A change is not complete until the matching section of
   `docs/ui-structure-and-terminology.md` is updated; every backticked identifier must resolve by
   whole-word search; each entry must be tagged `[实现]`/`[契约]`/`[未接线]`.
6. `.gitattributes` sets `* text=auto eol=lf` for `.md`/`.kt`/`.json` — new docs must be LF.

---

## C. Existing tests / fixtures for theme parsing, validation and layout

Under `spatial-workbench-core/src/test/kotlin/com/m0e_n00b/spatialworkbench/core/`:

| File | Lines | What it asserts |
| --- | --- | --- |
| `ThemeValidatorTest.kt` | 124 | `CinemaTheme` is valid; resolves `MEDIA_STAGE` for WATCH and FOCUS; rejects invalid size/cylinder, missing slot reference, duplicate toolbar id, duplicate component id, invalid palette contrast |
| `PlaybackCanvasContractsTest.kt` | 119 | `PlaybackCanvasReducer` transitions; `CinemaTheme` canvas recipes keep ON_DEMAND rails out of QUIET/PLAYBACK; `visibleSlots` includes PERSISTENT |
| `MediaStageContractsTest.kt` | 192 | `MediaStageTargetRegistry` validation (video cannot carry overlay surface; overlay needs surface id + anchor mode); reducer attach/detach/overlay/seek/stage-change effects |
| `MediaOverlayContractsTest.kt` | 237 | `MediaOverlayValidator` over `DanmakuSurfaceGroup` / `DanmakuDepthLayer` / lane sets / occlusion domains / allocation; `DanmakuAllocator.allocate` |
| `WorkbenchContractsTest.kt` | 167 | palette contrast, interactions colours, browse-origin resolver, search composition; `CinemaTheme` shorts rails + front transport |
| `TransientMessageContractsTest.kt` | 62 | transient message state |
| `ContentAccessContractsTest.kt` | 13 | content access enum/badge |

**There is no test for theme parsing, schema versioning, or migration — because no parser or version
field exists.** `CinemaTheme.create()` is the only fixture, and it is Kotlin code, not a file.
This is the concrete starting point for Phase 1.

---

## D. Existing-task and kanban overlap map

Active Trellis tasks:

| Task | Status | Overlap with this refactor |
| --- | --- | --- |
| `09-02-workbench-lift-and-grab` | in_progress | **Direct predecessor.** Introduced `GrabPilotSystem`, the ISDK grab switch, `VideoStageGrabPersistenceSystem` and `workbench_stage_y` persistence. New Phase 3 generalises its single group into declared groups. |
| `09-02-context-rail-right-panel` | planning | Right-rail content (选集/相关/详情). Depends on `panels.mode_panel` placement parameters; content work is orthogonal. |
| `10-06-workbench-dismiss-scope-fix` | planning | Outer-dismiss hit layer; established the **`Visible(false)` removes hit testing** invariant, which the new `hitTest` field must respect. Does not change visibility ownership. |
| `07-29-stage-b-cross-activity-handoff` | in_progress | 2D↔immersive output handoff; carries the single-Surface invariant the theme runtime must not break. |

Kanban board (`.dsh-kanban.json`) — cards that overlap and must be coordinated rather than duplicated:

| Card | Title | Overlap / disposition |
| --- | --- | --- |
| `k34` | 弹幕多平面：`DanmakuPlaneSpec` + fanout 分派 | **Overlaps Phase 4.** Card notes "09-10 阶段 4 预留了接口但代码零实现（grep `DanmakuPlaneSpec` 与 `danmaku_plane_` 均无匹配；`DanmakuRuntime` 的多 canvas 已就绪）". Recommend Phase 4 supersedes it as schema-driven, and the card be linked/closed rather than run in parallel. |
| `k18` | Grab bar 重构：贴幕布+gap+hover，独立可抓拖整组 | Contains the **root-cause finding that `IsdkPanelGrabHandle` native colliders only exist on edges/corners (no centre)**. This constrains Phase 3's handle design and must be read before choosing handle geometry. |
| `k30` | Quest 验收：workbench grab 双模式 + 高度持久化 | Phase 3's headset acceptance list; also documents that only Y is persisted. |
| `k82` | `WorkbenchOuterDismiss` 命中层是否生效 | **Done** (moved to 已完成). Source of the `Visible(false)` ⇒ removes hit testing invariant, plus the "`app/scenes` is the authorisation source, `assets/scenes` is the build product" and mse-agent operating procedure. |
| `k26` | 合并曲率三字段与三持久化 key 冗余 | Fold into Phase 2 (same files). |
| `k12` `k13` `k15` `k16` `k17` | 弹幕 performance / canvas size / scale sync batches | Phase 4 prerequisites or parallel work; `k13`/`k15`/`k17` describe the superseded entity-`Scale` era per `research/audit-danmaku-runtime.md`. |
| `k10` | 右面板完善为 Context rail（选集/相关/弹幕） | Same as Trellis `09-02-context-rail-right-panel`. |
| `k24` `k38` `k46` | Trellis write CRLF / line-ending normalisation | Relevant to edit hygiene only; new `.md` must be LF. |
| `k5` `k20` `k22` `k28` `k60` | Workbench dismiss / popup theme / doc drift / danmaku constants | Housekeeping; `k60` (duplicated danmaku constants) is already fixed. |

**No existing task or card declares a theme schema, a schema version, or a theme parser.** The
closest are `k34` (danmaku planes, no schema) and the `docs/immersive-ui-low-code-architecture.md`
Phase 1 heading (`:1109-1114`, "定义 `PanelSlot`、`ImmersiveLayoutMode`、`SpatialTheme` 和模块注册表"),
which is a design heading with no task behind it. **Phase 1 of the new design does not duplicate any
existing task.**

---

## E. Items requiring a product decision (carried from the design doc §10)

| # | Decision | Affected by existing work |
| --- | --- | --- |
| D1 | Author semantic scene anchors via mse-agent, or keep Kotlin-built panels | `k82` establishes that `app/scenes` is the authorisation source |
| D2 | Collapse the two visibility sources into one | §7 item 4 of the terminology doc already flags this mismatch |
| D3 | Merge `SearchWorkspaceRoute` with `WorkbenchContent`, or split their duties | §7 item 1; `research/audit-state-ownership-and-persistence.md` shows `WorkbenchContent` has zero external readers |
| D4 | `DanmakuDepthLayer` single vs. multiple `surfaceId` | `k34` |
| D5 | Allow runtime panel creation, or restrict to register-once + reshape | `spatial-sdk-known-issues.md` (dynamic Compose panels may lose touch input) |
| D6 | First-phase scope | this task's PRD §8 |

---

## F. [UNVERIFIED]

- Whether the archived Trellis tasks (`07-29-stage-b-cross-activity-handoff`, and the ~68 entries in
  `.trellis/tasks/archive/`) contain design decisions that constrain the theme runtime. Only the four
  active tasks and the kanban board were read.
- The full contents of `.trellis/spec/frontend/*.md` — the file list and their stated subjects were
  read, but individual guideline bodies were not re-read in this pass; the implement/check sub-agents
  receive them via `implement.jsonl` / `check.jsonl`.
- `docs/infrastructure-roadmap.md`, `docs/quest-runtime-notes.md`, `docs/Youtube-VR-UX.md`,
  `docs/prototypes/workbench/README.md` and `docs/research/**` beyond the files cited elsewhere in this
  task's research set.
