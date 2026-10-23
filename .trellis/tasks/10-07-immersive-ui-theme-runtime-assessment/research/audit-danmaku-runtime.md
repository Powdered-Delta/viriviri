# Audit: current danmaku / overlay rendering implementation (runtime truth)

Scope: `app/src/main/java/com/m0e_n00b/viriviri/*` danmaku + overlay files,
`spatial-workbench-core` media-overlay contracts/validator, `app/src/main/components/*.xml`,
`app/scenes/*`. Read-only audit; no source file was modified. `temp/` (gitignored, see
`.gitignore:48,52`) was explicitly searched as well and contains no match for any of the five
contract identifiers — see §T5.

All line numbers are from the working tree as read during this audit.

---

## Verdict

Today the app renders danmaku on **exactly one** presentation surface: one Spatial panel entity
(`danmakuOverlayEntity`, created via `Entity.createPanelEntity(R.id.danmaku_overlay_panel, …)` at
`SpatialVideoSampleActivity.kt:1532-1540`) whose Compose content is a single `Canvas(Modifier.fillMaxSize())`
inside `DanmakuOverlay()` (`DanmakuOverlay.kt:37,62`). The presentation mechanism is a
`ComposeViewPanelRegistration` UI panel (`SpatialVideoSampleActivity.kt:1437-1445`) parented to the stage
root `R.id.spatialized_video_panel` by `TransformParent` (`:1536`), **not** a TextureView/SurfaceTexture and
**not** a media/compositor panel — only the video panel is a compositor layer (`:1191-1213`, `:2943-2960`).
Lane state that actually runs is **per-canvas instance state inside `DanmakuCanvasRuntime`**
(`DanmakuRuntime.kt:154-169`: three availability `LongArray`s + `seenIds` + `lastPositionMs` + pooled
active set), i.e. per-surface, and because exactly one canvas is composed today it is de facto single.
`DanmakuLaneScheduler.kt` (the pure `prepareDanmaku` / `scheduleDanmakuLanes` scheduler whose output is
stored in `ViriViriUiState.danmakuLaneAssignments`) is **not reachable from the running app**: its only
production caller is `ViriViriAppState.loadDanmaku()` (`ViriViriAppState.kt:974-994`), which has **no call
site** — only unit tests call it. Everything in `MediaOverlayContracts.kt` beyond `DanmakuEvent`,
`DanmakuLaneFamily`, `DanmakuEmissionDirection` and `OverlayStyleOverride` (groups, depth layers, lane-set
specs, occlusion domains, allocation requests, the allocator itself) is **contract-only**: zero references
under `app/`. There are no multiple danmaku planes, no depth layers, no group capacity/weight allocation and
no cross-group occlusion anywhere in runtime code; emission direction is parsed and carried but ignored by
both the scheduler and the renderer, so mode-6 (reverse) danmaku are drawn right-to-left like every other
scroller.

---

## Summary table

| Question | Finding | Status | Evidence (file:line) |
| --- | --- | --- | --- |
| 1. Danmaku render surfaces at runtime | Exactly **one** danmaku surface: entity `danmakuOverlayEntity` + one Compose `Canvas`. (A second *overlay* panel exists: `stageBackdropEntity`, non-danmaku dim layer.) | IMPLEMENTED | `SpatialVideoSampleActivity.kt:1530-1544`, `:1437-1445`, `DanmakuOverlay.kt:62` |
| 2. Presentation mechanism / attachment | Compose-in-UI-panel (`ComposeViewPanelRegistration` + `ComposeView{setContent{DanmakuOverlay()}}`), `Panel` component with `MeshCollision.NoCollision`, child of stage root via `TransformParent(Entity(R.id.spatialized_video_panel))`. No TextureView/SurfaceTexture/compositor layer for danmaku. | IMPLEMENTED | `SpatialVideoSampleActivity.kt:1437-1445`, `:1530-1540`, `:1546-1558` |
| 3. Lane/track allocation state | Live: per-canvas `DanmakuCanvasRuntime` arrays (`scrollingFreeAt`/`topFreeAt`/`bottomFreeAt`/`seenIds`/`lastPositionMs`/pool). `DanmakuLaneScheduler` state is per-invocation and unreachable at runtime. | PARTIAL | `DanmakuRuntime.kt:154-169`, `DanmakuLaneScheduler.kt:84-88`, `ViriViriAppState.kt:974-994` |
| 4. Multi-plane / depth / direction / capacity / weight / occlusion | See capability matrix in §T4 (1 ABSENT, 1 PARTIAL, rest ABSENT at runtime). | ABSENT / PARTIAL | §T4 |
| 5. `DanmakuSurfaceGroup` etc. referenced in `app/`? | **No.** All five identifiers: 0 matches under `app/`; matches only in `spatial-workbench-core` main+test and two docs. | CONTRACT-ONLY | §T5 |
| 6. Duplicated render constants (`DanmakuOverlay` vs `DanmakuRenderConfig`) | **No longer duplicated**: overlay reads `activeRuntime.config.*`; only the frame-cadence constant lives in the overlay. `DanmakuRenderConfig.showArea` has no consumer. | IMPLEMENTED (k60 fixed) | `DanmakuOverlay.kt:24-27`, `:68-79`, `DanmakuRenderConfig.kt:12-45` |
| 7. Danmaku canvas size | Panel **shape** (metres) = `scaledStageWidth()/scaledStageHeight()` (1.6×0.9 m × stage scale), re-reshaped on scale/curvature change; panel **display** = fixed `DpDisplayOptions(1280×720, dpi=800)`. Canvas px size therefore does not follow the video content quad; text size is a fixed 152 px constant. | PARTIAL | `SpatialVideoSampleActivity.kt:752-754`, `:1467-1477`, `:2612-2639`, `:2514-2515`, `DanmakuLaneScheduler.kt:12` |
| 8. Second player / second video surface | **One** `PlayerSession` / **one** `ExoPlayer` (`ExoPlayer.Builder` appears once). Two *mutually exclusive* output paths exist: SDK immersive compositor surface, and the 2D `TextureView` surface in `PancakeActivity`. `PlayerSession.attachSurface` clears the previous surface. | IMPLEMENTED | `ViriViriAppState.kt:176-224`, `:233`, `SpatialVideoSampleActivity.kt:364-366`, `RecommendationUi.kt:836-851`, `PancakeActivity.kt:33,63` |

---

## T1. Render surfaces (entities, registration, render targets)

### 1.1 Danmaku surface — exactly one

The only danmaku entity is created once, guarded by a null check, during `onVRReady()`:

```kotlin
// SpatialVideoSampleActivity.kt:1530-1544
private fun createDanmakuOverlayPanel() {
  if (danmakuOverlayEntity != null) return
  danmakuOverlayEntity =
      Entity.createPanelEntity(
              R.id.danmaku_overlay_panel,
              Transform(Pose(Vector3(0f, 0f, stageDanmakuBaseLocalZ))),
              TransformParent(Entity(R.id.spatialized_video_panel)),
              Visible(true),
          )
          // The overlay must render but never block the stage's controller/hand raycasts.
          .also { it.setComponent(Panel(R.id.danmaku_overlay_panel, MeshCollision.NoCollision)) }
```

Creation call site: `SpatialVideoSampleActivity.kt:1109` (`createDanmakuOverlayPanel()` right after
`createStageBackdropPanel()` at `:1108`). Field: `:208` (`private var danmakuOverlayEntity: Entity? = null`).
Panel registration id: `:1439`; id declared in `app/src/main/res/values/ids.xml:10`
(`<item type="id" name="danmaku_overlay_panel" />`).

The Compose render target is one `Canvas` filling that panel:

```kotlin
// DanmakuOverlay.kt:62
Canvas(modifier = Modifier.fillMaxSize()) {
```
…with the draw loop at `DanmakuOverlay.kt:63-95` and the composable itself at `:37`. The composable is
instantiated in exactly **one** place in the whole repo (`SpatialVideoSampleActivity.kt:1440`); a
whole-repo search for `DanmakuOverlay()` returns only that call site plus the declaration.

Therefore: **danmaku render surfaces = 1** (one Spatial entity + one Compose Canvas target).

### 1.2 The other overlay surface (not danmaku)

`stageBackdropEntity` is a second overlay panel (dim layer), same mechanism, created `Visible(false)`
and only enabled while workbench modules exist:

```kotlin
// SpatialVideoSampleActivity.kt:1506-1517 (excerpt)
stageBackdropEntity =
    Entity.createPanelEntity(
            R.id.stage_backdrop_panel,
            Transform(Pose(Vector3(0f, 0f, stageBackdropBaseLocalZ))),
            TransformParent(Entity(R.id.spatialized_video_panel)),
            // Disabled until a uniform Spatial material replaces compositor-dithered UI alpha.
            Visible(false),
        )
        .also { it.setComponent(Panel(R.id.stage_backdrop_panel, MeshCollision.NoCollision)) }
```
Visibility sync: `:1520-1523` via `spatialPanelVisibilityController.setVisible(PanelSlot.MEDIA_STAGE, …, modules.isNotEmpty())`.
Its Compose target is `StageBackdrop()` (`DanmakuOverlay.kt:29-34`, `SpatialVideoSampleActivity.kt:1429`).

So at the *overlay* level there are 2 panels (danmaku + backdrop); at the *danmaku* level there is 1.

### 1.3 No danmaku component schema, no danmaku scene node

- `app/src/main/components/spatial_video_sample_components.xml` (16 lines) declares only
  `SpatializedAudioPanel` (`:7`), `PanelLayerAlpha` (`:8-10`), `WristAttached` (`:11-15`). **No
  danmaku/overlay component exists.**
- `app/scenes/**` contains **no** danmaku/overlay node: a search over `app/scenes` for
  `danmaku|Danmaku|overlay|Overlay` returns no matches. The danmaku layer is purely runtime-created.

### 1.4 Visibility of the danmaku panel

`danmakuOverlayEntity` is never passed to `spatialPanelVisibilityController` and never receives
`Visible(false)` after creation: all later references are reshape/curvature only
(`:1742-1750`, `:2238`, `:2298-2305`, `:2541`, `:2577`). Source-visible conclusion: the danmaku panel is
always `Visible(true)` once created (`:1537`). Whether it is *on screen* in every route (Browse/Detail) was
not verified on device → NEEDS-HEADSET for the user-visible claim.

---

## T2. Presentation mechanism

`ComposeViewPanelRegistration` + `ComposeFeature`:

```kotlin
// SpatialVideoSampleActivity.kt:1437-1445
private fun danmakuOverlayPanelRegistration(): PanelRegistration =
    ComposeViewPanelRegistration(
        R.id.danmaku_overlay_panel,
        composeViewCreator = { _, context -> ComposeView(context).apply { setContent { DanmakuOverlay() } } },
        settingsCreator = {
          // Stage-scaled footprint: see [applyOverlayStageShape].
          stageOverlayPanelSettings(scaledStageWidth(), scaledStageHeight(), appliedDanmakuCurvature)
        },
    )
```
Registered in `registerPanels()` at `:486`; `ComposeFeature()` is installed at `:335`.

Attachment to the video stage: `TransformParent(Entity(R.id.spatialized_video_panel))`
(`:1536`) — the danmaku panel is a **child of the stage root entity**, not of `videoSurfaceEntity`
(the child that carries the actual video surface, `:1168-1185`). Panel type: ordinary UI panel
(`Panel(R.id.danmaku_overlay_panel, MeshCollision.NoCollision)`, `:1540`) with
`PanelInputOptions(0)` (`:1475`) — render-only, non-hittable (traced at `:1554`).

Explicit contrast with the video: the video uses `MediaPanelSettings` + `PanelSceneObject` and is
documented as the **only** compositor layer in the scene:

```kotlin
// SpatialVideoSampleActivity.kt:2943-2960 (excerpt of the EXPERIMENT comment)
 * The video is the ONLY compositor layer in the scene. `MediaPanelRenderOptions.applyTo` calls
 * `setLayerConfig` unconditionally, while `QuadShapeOptions.applyTo` and
 * `CylinderShapeOptions.applyTo` only set glass / shape type / width / height / cylinder radius,
 * leaving the rails, the transport and the danmaku overlay as ordinary scene geometry.
```
→ Danmaku = ordinary scene geometry UI panel with a Compose canvas. **No TextureView /
SurfaceTexture / Surface / ImageReader is used for danmaku anywhere** (the only
`TextureView`/`SurfaceTexture` hits in `app/src/main` are `RecommendationUi.kt:4,807-888`, the 2D video
output — see §T8).

---

## T3. Lane / track allocation state

### 3.1 Live state — inside each `DanmakuCanvasRuntime` (per canvas/surface)

```kotlin
// DanmakuRuntime.kt:154-169
internal class DanmakuCanvasRuntime(
    private val source: DanmakuStreamSource,
    val config: DanmakuRenderConfig = DanmakuRenderConfig.DEFAULT,
) {
  val active = ArrayList<ActiveDanmaku>()
  ...
  private val pool = ArrayDeque<ActiveDanmaku>()
  private val scrollingFreeAt = LongArray(config.scrollingLaneCount)
  private val topFreeAt = LongArray(config.fixedLaneCount)
  private val bottomFreeAt = LongArray(config.fixedLaneCount)
  private val seenIds = HashSet<String>()
  private var lastPositionMs = Long.MIN_VALUE
```

Fields held, and what they mean:

| Field | Type / size | Role | Evidence |
| --- | --- | --- | --- |
| `active` | `ArrayList<ActiveDanmaku>` | on-canvas active set (public, read by renderer) | `:158`, read at `DanmakuOverlay.kt:70` |
| `pool` | `ArrayDeque<ActiveDanmaku>` | recycled item instances | `:164`, `:228`, `:278` |
| `scrollingFreeAt` | `LongArray(scrollingLaneCount = 12)` | per-lane "occupied until" tick for scrollers | `:165`, `:227`, `:248-251` |
| `topFreeAt` | `LongArray(fixedLaneCount = 3)` | per-lane occupied-until for top-pinned | `:166`, `:253-257` |
| `bottomFreeAt` | `LongArray(fixedLaneCount = 3)` | per-lane occupied-until for bottom-pinned | `:167`, `:259-263` |
| `seenIds` | `HashSet<String>` | admission de-dup within the active window | `:168`, `:195`, `:206`, `:279` |
| `lastPositionMs` | `Long` | playback clock + seek detection | `:169`, `:176-184` |
| `config` | `DanmakuRenderConfig` | capacity/duration policy for this canvas | `:156` (note: `val`, made public for the renderer) |

Per-canvas instance is created per composition:

```kotlin
// DanmakuOverlay.kt:49-52
val runtime = remember(selectedVideoId) {
  val videoId = selectedVideoId ?: return@remember null
  DanmakuCanvasRuntime(ViriViriApplication.appState.danmakuStreamSource(videoId))
}
```
Registration with the shared source: `DanmakuRuntime.kt:161-163` (`source.registerCanvas(this)`), weak-ref
list at `:61` and `:67-70`, live canvas count for telemetry at `:72-77` / `:83`.

**Answer to "one global lane state, or per-surface/per-layer?": per-canvas (per-surface) state.** The
shared `DanmakuStreamSource` holds event data only (`segments`, `buckets`, `metricsCache` —
`:56-60`) plus the canvas weak-ref list; it holds no lane occupancy. Because only one canvas exists
today (§T1.1), the observable runtime behaviour is a single lane state.

### 3.2 `DanmakuLaneScheduler` state — present but not wired into the app runtime

`scheduleDanmakuLanes` allocates fresh arrays for one call and returns assignments:

```kotlin
// DanmakuLaneScheduler.kt:84-88
val scrollingAvailability = LongArray(config.scrollingLaneCount)
val topAvailability = LongArray(config.fixedLaneCount)
val bottomAvailability = LongArray(config.fixedLaneCount)
val assignments = LinkedHashMap<String, DanmakuLaneAssignment>()
val rejected = LinkedHashSet<String>()
```

Its only production entry point is `prepareDanmaku()` (`:23-56`), which is called **only** from
`ViriViriAppState.loadDanmaku()`:

```kotlin
// ViriViriAppState.kt:974-994 (excerpt)
private fun loadDanmaku(recommendation: Recommendation, requestId: Long) {
  danmakuLoadJob =
      scope.launch {
        val prepared =
            runCatching {
              withContext(Dispatchers.IO) { prepareDanmaku(provider.loadDanmaku(recommendation.videoId), danmakuMergeConfig) }
            }
        ...
                danmakuLaneAssignments = prepared.getOrNull()?.laneAssignments.orEmpty(),
                danmakuRenderMetrics = prepared.getOrNull()?.renderMetrics.orEmpty(),
```

A repo-wide search for `loadDanmaku(` returns exactly: the provider definition
(`BilibiliPlaybackProvider.kt:338`), the private function declaration (`ViriViriAppState.kt:974`) and the
provider call inside it (`:979`). **There is no call to `ViriViriAppState.loadDanmaku(...)` anywhere** —
the live comment at `:942-944` says so explicitly ("the full XML preload below is retained only as a
disabled legacy path reference"). Consequences:

- `danmakuLaneAssignments` / `danmakuRenderMetrics` / `danmakuEvents` in
  `ViriViriUiState` (`:104-106`) are written (and reset at `:925-927`) but **never read** by any
  composable or system; a repo-wide search finds no consumer.
- `mergeDanmakuEvents` (`DanmakuMerge.kt:39-66`) is reachable only through `prepareDanmaku`
  (`DanmakuLaneScheduler.kt:28`) and unit tests → dead in the shipping runtime. So the merge/`mergedCount`
  feature (and its `DanmakuMergeConfig`, `ViriViriAppState.kt:230`) does **not** affect what is drawn.
- Live in-app de-dup is instead `seenIds` + per-bucket admission in `DanmakuCanvasRuntime`.

Runtime admission (live path) for reference:

```kotlin
// DanmakuRuntime.kt:212-236 (excerpt)
private fun admit(event: DanmakuEvent, nowMs: Long) {
  if (config.weightThreshold > 0 &&
      event.weight != DanmakuEvent.WEIGHT_UNKNOWN &&
      event.weight < config.weightThreshold) {
    return
  }
  ...
  val chosen = pickLane(event, nowMs, duration) ?: return
  val entryGap = if (event.laneFamily == DanmakuLaneFamily.SCROLLING) config.scrollingDurationMs / 4L else 0L
  chosen.first[chosen.second] = nowMs + duration + entryGap
```
`pickLane` uses first-free then optional least-busy fallback (`:239-268`); `rejectWhenNoFreeLane`
defaults to `true` (`DanmakuRenderConfig.kt:30`).

---

## T4. Capability matrix — planes / depth / direction / capacity / weight / occlusion

| Capability | Runtime status | Contract status | Evidence |
| --- | --- | --- | --- |
| Multiple danmaku planes (fan-out of one event stream to N planes) | **ABSENT.** One panel, one canvas, one runtime; no fan-out/router exists. `registerCanvas` supports N canvases but nothing creates a second one. | `DanmakuSurfaceGroup` / `DanmakuLaneSetSpec` exist in core only | `SpatialVideoSampleActivity.kt:1530-1544`, `DanmakuRuntime.kt:61-70`, `MediaOverlayContracts.kt:98-120`; kanban k34 states "代码零实现" for `DanmakuPlaneSpec` |
| Depth layers for danmaku | **ABSENT as a danmaku feature.** A single local-Z value exists and is cycled only by the debug rail; it is a presentation offset, not a layer stack with per-layer capacity/style. | `DanmakuDepthLayer` (id, surfaceId, depthOffsetMeters, maxActiveItems, style) in core only | `SpatialVideoSampleActivity.kt:186-189`, `:2938`; `MediaOverlayContracts.kt:105-111` |
| Emission direction per lane family | **PARTIAL.** Parsed and carried (`RIGHT_TO_LEFT` for modes 1-3, `LEFT_TO_RIGHT` for mode 6, `null` for fixed), and part of the merge key — but **ignored by both the scheduler and the renderer**, which always draws SCROLLING right-to-left. | enum exists; `DanmakuLaneSetSpec.emissionDirection` used by validator/allocator | parsing `BilibiliDanmaku.kt:58-65`, carry `:73`, merge key `DanmakuMerge.kt:32,52`, renderer `DanmakuOverlay.kt:81-84`, scheduler `DanmakuLaneScheduler.kt:96-114` (no `emissionDirection` read) |
| Capacity per group / surface | **ABSENT at runtime** (no group concept; per-canvas capacity ≈ lane counts). Note the live runtime has no independent max-active cap beyond lane counts. | `OverlaySurfaceSpec.capacity`, `DanmakuDepthLayer.maxActiveItems`, enforcement `minOf(surface.capacity, layer.maxActiveItems)` | `MediaOverlayContracts.kt:84-91`, `:105-111`, `MediaOverlayValidator.kt:243-249` |
| Allocation weight between groups | **ABSENT.** Contract has `allocationWeight` and a least-loaded/weight selector. The *runtime* weight knob is an **event-level** density filter, and it is **disabled by default** (`weightThreshold = 0`). | `DanmakuSurfaceGroup.allocationWeight` + `selectGroup` | `MediaOverlayContracts.kt:113-120`, `MediaOverlayValidator.kt:271-283`; runtime: `DanmakuRenderConfig.kt:25`, `DanmakuRuntime.kt:213-217`, `DanmakuLaneScheduler.kt:120-124` |
| Cross-group occlusion | **ABSENT as danmaku logic.** No projected-overlap/occlusion check anywhere in runtime; the only related runtime facts are (a) the panel is non-collidable for input (`MeshCollision.NoCollision`), and (b) device-measured compositor ordering: the video compositor layer draws over scene geometry regardless of depth. | `DanmakuOcclusionPolicy`, `DanmakuOcclusionDomain`, `CrossLayerOcclusionResolver` — see §T5 (the policy enum is not even read by the allocator) | `SpatialVideoSampleActivity.kt:1540`, `:2943-2960` (esp. `:2950-2952`); `MediaOverlayContracts.kt:45-49`, `:122-126`, `:227-234` |

Additional runtime facts worth recording for an architect:

- Lane **families** are separated into three independent pools (scrolling / top-fixed / bottom-fixed) per
  canvas — `DanmakuRuntime.kt:165-167`, `:244-265`; fixed lanes are horizontally centred
  (`DanmakuOverlay.kt:83`).
- Fixed-lane vertical placement uses hard-coded screen fractions (`0.24` top band, `0.76 … ` bottom band):
  `DanmakuOverlay.kt:86-89`. Scrolling lanes are evenly spread over full height (`:79`). `DanmakuRenderConfig.showArea`
  (the intended "top band only" knob, `DanmakuRenderConfig.kt:31-32`) is **never consumed** — confirmed by
  repo-wide search. (Also recorded in kanban k60's comment.)
- Frame cadence is a fixed 33 ms overlay tick while playing, 100 ms when paused, derived from
  `player.currentPosition` (`DanmakuOverlay.kt:27`, `:40-47`).

---

## T5. Grep results per contract identifier (repo-wide, `temp/` included then re-checked separately)

Method: plain-text search over `*.kt` in the repository root, then an explicit second pass restricted to
`temp/` to make sure the vendored `PiliPlus` / SDK samples do not contain any of them. `temp/` is
gitignored (`.gitignore:48,52`) but *is* searchable (verified by finding 250+ `danmaku` hits there), so the
explicit second pass is meaningful.

| Identifier | Matches under `app/` | All matches | Verdict |
| --- | --- | --- | --- |
| `DanmakuSurfaceGroup` | **0** | core: `MediaOverlayContracts.kt:113`, `:238`; `MediaOverlayValidator.kt:24,33,37,80,232,239,252`; test: `MediaOverlayContractsTest.kt:20,190,232`; docs: `docs/immersive-ui-low-code-architecture.md:711,1128,1166`, `docs/Youtube-VR-UX.md:603` | CONTRACT-ONLY |
| `DanmakuDepthLayer` | **0** | core: `MediaOverlayContracts.kt:105`, `:118`; `MediaOverlayValidator.kt:111,235,242,278,286,288,290,294`; test: `MediaOverlayContractsTest.kt:31,224,228` | CONTRACT-ONLY |
| `DanmakuLaneSetSpec` | **0** | core: `MediaOverlayContracts.kt:98`, `:117`; `MediaOverlayValidator.kt:96,233,254,261`; test: `MediaOverlayContractsTest.kt:25,234` | CONTRACT-ONLY |
| `DanmakuOcclusionDomain` | **0** | core only: `MediaOverlayContracts.kt:122`; `MediaOverlayValidator.kt:25` | CONTRACT-ONLY |
| `DanmakuAllocationRequest` | **0** | core: `MediaOverlayContracts.kt:236`; `MediaOverlayValidator.kt:190,273,287`; test: `MediaOverlayContractsTest.kt:196` | CONTRACT-ONLY |
| `temp/` second pass (all five + `OverlaySurfaceSpec`, `DanmakuAllocator`) | — | **No matches found** | confirms the identifiers are not present in vendored samples either |

Two extra findings from the same search that sharpen "contract-only":

- The allocator/validator have **no consumer outside their own module + its unit tests**:
  `DanmakuAllocator.allocate` is called only from `MediaOverlayValidator` internals
  (`:191`) and from `MediaOverlayContractsTest.kt:67,86,87,101,137,149,164,166`. No `app/` file imports
  `DanmakuAllocator`, `MediaOverlayValidator`, `DanmakuSurfaceGroup`, `OverlaySurfaceSpec`,
  `ScheduledDanmakuAssignment` or `DanmakuAllocationResult`.
- `DanmakuOcclusionPolicy` is referenced only at its own definition and as a default value
  (`MediaOverlayContracts.kt:45-49`, `:119`) — the allocator never reads it, so even the contract's
  occlusion policy is inert.
- The app's total consumption of `spatialworkbench.core` *media-overlay* types is exactly four:
  `DanmakuEvent` (`BilibiliDanmaku.kt:4`, `BilibiliDanmakuProto.kt:3`, `DanmakuRuntime.kt:5`,
  `ViriViriAppState.kt:23`, `BilibiliPlaybackProvider.kt:11`, `DanmakuLaneScheduler.kt:4`),
  `DanmakuLaneFamily` (`BilibiliDanmaku.kt:5`, `DanmakuMerge.kt:5`, `DanmakuOverlay.kt:17`,
  `DanmakuRuntime.kt:6`, `DanmakuLaneScheduler.kt:5`), `DanmakuEmissionDirection`
  (`BilibiliDanmaku.kt:3`, `DanmakuMerge.kt:3`) and `OverlayStyleOverride` (`BilibiliDanmaku.kt:6`).
  Everything else the app imports from `spatialworkbench.core` is theme/panel/transient-message/playback
  state, not media overlay.

---

## T6. `DanmakuRenderConfig` vs `DanmakuOverlay` — duplicated constants (kanban k60)

**Current state: not duplicated.** The overlay holds exactly one constant, the frame cadence, and its
docstring records the earlier bug:

```kotlin
// DanmakuOverlay.kt:20-27
/**
 * Overlay-only render cadence (ms).
 *
 * Lane counts and durations are deliberately NOT declared here: the renderer reads them from the
 * canvas runtime's [DanmakuRenderConfig]. Keeping a second copy in the overlay let the renderer
 * silently diverge from the scheduler that admitted items into those lanes.
 */
private const val DANMAKU_FRAME_INTERVAL_MS = 33L
```

It reads the runtime's config object instead of local copies:

```kotlin
// DanmakuOverlay.kt:68-79 (excerpt)
val config = activeRuntime.config
...
val duration =
    if (item.family == DanmakuLaneFamily.SCROLLING) config.scrollingDurationMs
    else config.fixedDurationMs
...
val y = (item.lane + 1) * size.height / (config.scrollingLaneCount + 1)
```
and `DanmakuOverlay.kt:86-89` uses `config.fixedLaneCount`. The single source of those values:

```kotlin
// DanmakuRenderConfig.kt:12-33 (excerpt)
data class DanmakuRenderConfig(
    val scrollingLaneCount: Int = 12,
    val fixedLaneCount: Int = 3,
    val scrollingDurationMs: Long = 6_000L,
    val fixedDurationMs: Long = 4_000L,
    val weightThreshold: Int = 0,
    val rejectWhenNoFreeLane: Boolean = true,
    val showArea: Float = 1f,
) {
```
`DanmakuCanvasRuntime` exposes it as `val config` (`DanmakuRuntime.kt:156`); kanban card **k60**
("弹幕渲染常量双份定义") was closed on 2026-10-02 with commit `16976b37f7bdff699b61ff8bc0d6d77b5d4f06fc` and its comment documents exactly
this fix (`private val config` → `val config`, delete the overlay duplicates). Two residual notes, both
from source:

1. `DanmakuRenderConfig.showArea` (`:32`) has **no consumer** — only the definition and its `require`
   (`:39`). The overlay's fixed-lane bands (`DanmakuOverlay.kt:86-89`) are hard-coded fractions and do not
   consult it. Status: unwired knob, no behaviour difference at the default `1f`.
2. `DANMAKU_TEXT_SIZE_PX = 152f` / `DANMAKU_OUTLINE_WIDTH_PX = 16f` live once, in
   `DanmakuLaneScheduler.kt:12-13`, and are the shared source for the overlay paints
   (`DanmakuOverlay.kt:53,56,58,75,76`) and for measurement in the runtime
   (`DanmakuRuntime.kt:138,143`) and in the dead `prepareDanmaku` path (`DanmakuLaneScheduler.kt:30,37`).
   They are **not** duplicated, but they are also **not** part of `DanmakuRenderConfig` — they are
   compile-time constants, which matters for §T7 (no runtime font/DPR adjustment exists).

No other duplicated danmaku tunable was found (`DanmakuMergeConfig` defaults exist once,
`DanmakuMerge.kt:8-21`).

---

## T7. How the danmaku canvas gets its size

Two independent size channels are in play; only the first follows the stage.

**(a) Physical size (metres) — follows stage scale and per-layer curvature.**

```kotlin
// SpatialVideoSampleActivity.kt:742-754
  /**
   * Stage footprint in metres with the shared stage scale already BAKED IN.
   * ...
   */
  private fun scaledStageWidth(): Float = MR_SCREEN_WIDTH * currentStageScale()

  private fun scaledStageHeight(): Float = MR_SCREEN_HEIGHT * currentStageScale()
```
with `MR_SCREEN_WIDTH = 16.0f/10.0f`, `MR_SCREEN_HEIGHT = 9.0f/10.0f` (`:2962-2963`),
`currentStageScale() = appliedStageScale ?: PlaybackCanvasSize.STANDARD.scale` (`:740`).

The registration and every later reshape use those values, with the danmaku layer's **own** curvature:

```kotlin
// SpatialVideoSampleActivity.kt:1437-1445 (settingsCreator)  →  :1467-1477
  private fun stageOverlayPanelSettings(
      width: Float,
      height: Float,
      curvature: PlaybackStageCurvature,
  ) =
      UIPanelSettings(
          shape = overlayShapeOptions(width, height, curvature),
          display = DpDisplayOptions(width = 1280f, height = 720f, dpi = 800),
          input = PanelInputOptions(0),
          style = PanelStyleOptions(themeResourceId = R.style.PanelAppThemeTransparent),
      )
```
`overlayShapeOptions` maps Flat→`QuadShapeOptions(width,height)`, Cylinder→`CylinderShapeOptions(radius,width,height)`
(`:959-968`). Reshape path (scale and curvature changes):

```kotlin
// SpatialVideoSampleActivity.kt:2236-2239
  private fun applyOverlayStageShape() {
    reshapeStageOverlay(stageBackdropEntity, appliedBackdropCurvature, stageBackdropBaseLocalZ)
    reshapeStageOverlay(danmakuOverlayEntity, appliedDanmakuCurvature, stageDanmakuBaseLocalZ)
  }
```
```kotlin
// SpatialVideoSampleActivity.kt:2629-2637 (inside reshapeStageOverlay)
    overlay.reshape(
        stageOverlayPanelSettings(
                scaledStageWidth(),
                scaledStageHeight(),
                curvature,
            )
            .toPanelConfigOptions()
    )
    applyStageLayerCurvatureOffset(overlayEntity, curvature, baseLocalZ, UI_PANEL_CURVATURE_OFFSET_SCALE)
```
Trigger points: `applyPlaybackStageScale` → `applyOverlayStageShape()` (`:2213-2223`);
`applyPlaybackDanmakuCurvature` → `rebuildStageGeometry()` (`:2258-2262`); creation-time replay through
`StageOverlayReshapeSystem` (`:2558-2579`, `StageOverlayReshapeSystem.kt:14-18`).

**(b) Panel display (dp/px) — fixed, does not follow the video/screen.**

`DpDisplayOptions(width = 1280f, height = 720f, dpi = 800)` (`:1474`) is a literal; it is identical for the
backdrop (`:1433`) and never recomputed from `scaledStageWidth()`/video size. The video panel, by contrast,
uses `PixelDisplayOptions(IMMERSIVE_VIDEO_OUTPUT_WIDTH, IMMERSIVE_VIDEO_OUTPUT_HEIGHT)` = 1920×1080
(`:1196-1199`, `:2520-2525`, `ImmersiveVideoOutputConfig.kt:5-6`). So the danmaku canvas resolution and the
video canvas resolution are unrelated numbers.

The Compose canvas itself fills whatever pixel buffer the panel provides — `Canvas(Modifier.fillMaxSize())`
(`DanmakuOverlay.kt:62`) — and every position is computed from `DrawScope.size` in that space
(`:79`, `:82-83`, `:87-89`). Text glyphs, however, use the fixed pixel constant 152f
(`DanmakuLaneScheduler.kt:12`) applied to `Paint.textSize` (`DanmakuOverlay.kt:53-58,75-76`), and metrics
are measured with the same 152 px paint (`DanmakuRuntime.kt:137-145`). Because glyph size and canvas size
are both px, the text/canvas ratio is fixed by constants; the *physical* glyph size therefore scales only
through the panel's metre size (a) — it cannot be tuned at runtime, and a change to `dpi` would silently
re-scale every danmaku relative to the lanes.

**Known / provable mismatch:** the danmaku (and backdrop) overlay is reshaped to the **full scaled stage
footprint**, while the video panel is reshaped to the **aspect-fitted content quad**:

```kotlin
// SpatialVideoSampleActivity.kt:2510-2515 (video)
  private fun reshapeSpatialVideoPanel(content: SpatialVideoContentQuad) {
    val panel = spatialVideoPanelSceneObject ?: return
    // [content] already carries the shared stage scale (it is derived from the scaled stage
    // footprint), so the shape dimensions below bake scale + curvature together.
    val shapeWidth = content.halfWidth * 2f
    val shapeHeight = content.halfHeight * 2f
```
vs.
```kotlin
// SpatialVideoSampleActivity.kt:2538-2541 (overlays)
    // Each overlay keeps its OWN curvature, so the danmaku plane can bend on a different radius
    // than the backdrop (and than the video) while sharing the same scaled stage footprint.
    reshapeStageOverlay(stageBackdropEntity, appliedBackdropCurvature, stageBackdropBaseLocalZ)
    reshapeStageOverlay(danmakuOverlayEntity, appliedDanmakuCurvature, stageDanmakuBaseLocalZ)
```
`spatialVideoContentQuadForAspect` letterboxes/pillarboxes the content against the 16:10 stage
(`SpatialVideoContentQuad.kt:47-51`): `SpatialVideoContentQuad(fullStage.halfWidth, stageWidth / displayAspectRatio / 2f)`
or `(stageHeight * displayAspectRatio / 2f, fullStage.halfHeight)`. So whenever the video aspect ratio
differs from 16:10, danmaku lanes, centring and scroll span are computed over a rectangle larger than the
visible video content. This is source-provable (no device needed) and is distinct from the historical
kanban items k13 ("弹幕 overlay canvas 尺寸不随视频/屏幕缩放变化") and k15 ("首次启动弹幕 canvas 与幕布尺寸
不一致"), both of which describe the earlier entity-`Scale` era; the current code bakes scale into the shape
(`:742-751` documents the k17 root cause and the fix).

**UNVERIFIED:** the dp→px conversion of `DpDisplayOptions(dpi = 800)` (i.e. the exact pixel dimensions of
the canvas buffer, e.g. 1280×720 dp × density). That conversion is implemented inside the Spatial SDK
(`meta-spatial-sdk-toolkit` 0.13.2, `com/meta/spatial/toolkit/DpDisplayOptions.class` — inspection of the AAR
only confirms the `dpi`/`DEFAULT_DPI` members, not the density formula). Compose's `DrawScope.size` being in
pixels is a Compose API fact, not a repo fact. **No numeric claim about text-to-panel ratio is made here.**

**NEEDS-HEADSET:** whether the fixed 152 px text renders at a comfortable physical size at each stage-scale
preset, and whether danmaku visibly falls outside the letterboxed video area (a screenshot/logcat run would
decide it). Kanban k13/k14 (速度过快/残影) and k15 record on-device symptoms in this area.

---

## T8. Single player / single video output surface

**One player.** `ExoPlayer.Builder(...).build()` occurs exactly once in the repository:

```kotlin
// ViriViriAppState.kt:176-182
class PlayerSession(context: Context) {
  val player = ExoPlayer.Builder(context).build()
  private var surface: Surface? = null

  init {
    player.setVideoScalingMode(IMMERSIVE_VIDEO_SCALING_MODE)
  }
```
and is instantiated once, application-scoped:

```kotlin
// ViriViriAppState.kt:233
  val playerSession = PlayerSession(context)
```
Both activities reach it through the same accessor: `SpatialVideoSampleActivity.kt:148-149`
(`val player: ExoPlayer get() = ViriViriApplication.appState.playerSession.player`) and
`RecommendationUi.kt:802` / `DanmakuOverlay.kt:42`. Grep for `ExoPlayer` in `app/src/main` returns only
`SpatialVideoSampleActivity.kt:47,148`, `ViriViriAppState.kt:7,177` and one log tag (`:330`).

**Output surface is singular by construction.** `PlayerSession` keeps one `surface` field and detaches the
previous one on every attach:

```kotlin
// ViriViriAppState.kt:199-223 (excerpt)
  fun beginOutputHandoff() {
    surface?.let(player::clearVideoSurface)
    surface = null
  }
  fun attachImmersiveSurface(newSurface: Surface) = attachSurface(newSurface)
  /** Rebinds the SDK-owned output after an explicit 2D-to-immersive route handoff. */
  fun reattachImmersiveSurface(newSurface: Surface) = attachSurface(newSurface, force = true)
  fun attach2dSurface(newSurface: Surface) = attachSurface(newSurface)
  ...
  private fun attachSurface(newSurface: Surface, force: Boolean = false) {
    if (!newSurface.isValid || (!force && surface === newSurface)) return
    if (surface !== newSurface) surface?.let(player::clearVideoSurface)
    surface = newSurface
    player.setVideoSurface(newSurface)
  }
```

There are exactly **two code paths that can own the video output**, and they belong to two different
activities:

1. **Immersive (VR) path — the SDK/compositor media panel.** The app does not construct the Surface; it
   supplies attach/reattach callbacks to `ImmersiveMediaStageHost`:
   ```kotlin
   // SpatialVideoSampleActivity.kt:362-367
   immersiveMediaStageHost =
       ImmersiveMediaStageHost(
           attachVideoOutput = ViriViriApplication.appState.playerSession::attachImmersiveSurface,
           onEffect = { effect -> Log.d(TAG, "MediaStage effect=$effect") },
           reattachVideoOutput = ViriViriApplication.appState.playerSession::reattachImmersiveSurface,
       )
   ```
   The video panel is a `PanelSceneObject` bound to `videoSurfaceEntity` (`:1168-1185`, `:1202-1213`),
   display `PixelDisplayOptions(1920,1080)` (`:1196-1199`), and is the only compositor layer
   (`:2943-2960`).

2. **2D ("pancake") path — a `TextureView` `SurfaceTexture`.** `PlayerOutput` builds
   `AndroidView { AspectRatioTextureView(...) }` and attaches a `Surface` from the TextureView:
   ```kotlin
   // RecommendationUi.kt:836-840 (excerpt)
   surfaceTextureListener = object : TextureView.SurfaceTextureListener {
     override fun onSurfaceTextureAvailable(texture: android.graphics.SurfaceTexture, width: Int, height: Int) {
       output.refreshVideoOutput()
       renderSurface = android.view.Surface(texture).also(session::attach2dSurface)
     }
   ```
   Reached from `PancakeActivity` (`PancakeActivity.kt:63`, `RecommendationContent(state, appState, showPlayer = true)`
   → `RecommendationUi.kt:144` → `:802`). `PancakeActivity` is the manifest's 2D entry
   (`AndroidManifest.xml:79-90`, `com.oculus.intent.category.2D`), while the VR entry is
   `SpatialVideoSampleActivity` (`:97-110`, `com.oculus.intent.category.VR` + `LAUNCHER`). The handoff is
   explicit and clears the immersive surface first (`PancakeActivity.kt:33`
   `beginOutputHandoff()`; debug button `SpatialVideoSampleActivity.kt:1656-1658`; return path
   `EXTRA_REATTACH_IMMERSIVE_OUTPUT`, `:352-353` + `ViriViriAppState.kt:207`).

**Conclusion for Q8:** single `PlayerSession` / single `ExoPlayer`; a second *video output target* exists in
code (the 2D TextureView) but is a separate activity's presentation of the same player, mutually exclusive
with the immersive surface by `attachSurface`'s clear-previous semantics. Whether the two are ever live
simultaneously in practice, and whether the immersive surface is always the compositor's (rather than a
second internal target), cannot be proven from this repo → NEEDS-HEADSET / SDK-internal.

**Danmaku is not affected by either video-surface path:** the danmaku canvas never touches a
`Surface`/`SurfaceTexture`; it is a second, independent Compose UI panel child of the stage root (§T2).

---

## T9. Dead / contract-only inventory (what an architect must not mistake for runtime behaviour)

| Symbol | Where defined | Reachable from app runtime? | Note |
| --- | --- | --- | --- |
| `prepareDanmaku`, `scheduleDanmakuLanes`, `DanmakuLaneAssignment`, `PreparedDanmaku`, `DanmakuAdmissionResult`, `crossingFreeMs` | `DanmakuLaneScheduler.kt:7-173` | **No** — only via `ViriViriAppState.loadDanmaku()` (`:974`), which is never called; and via unit tests `DanmakuLaneSchedulerTest.kt` | Its `trackWidthPx = 1280f` literal (`:48`) is therefore never used in production either |
| `mergeDanmakuEvents`, `DanmakuMergeConfig`, `MergedDanmakuEvent.mergedCount` | `DanmakuMerge.kt:8-66` | **No** (only `prepareDanmaku` at `DanmakuLaneScheduler.kt:28`) | `ViriViriAppState.danmakuMergeConfig` (`:230`) is injected but only reaches the dead path |
| `ViriViriUiState.danmakuEvents` / `danmakuLaneAssignments` / `danmakuRenderMetrics` | `ViriViriAppState.kt:104-106` | Written/reset (`:925-927`, `:988-990`), **never read** | No composable/system consumer found |
| `DanmakuStreamSource.debugSummary()` | `DanmakuRuntime.kt:80-88` | Yes — debug rail TextView only | `SpatialVideoSampleActivity.kt:1703-1707` |
| All `MediaOverlayContracts.kt` group/layer/occlusion/allocation types + `MediaOverlayValidator` + `DanmakuAllocator` | core module | **No** (see §T5) | exercised only by `MediaOverlayContractsTest.kt` |
| `DanmakuRenderConfig.showArea` | `DanmakuRenderConfig.kt:31-32` | **No consumer** | also recorded in kanban k60 |
| `CurvedStageMesh.kt` | app main | **No caller** (documented in `docs/research/troubleshoot-stage-scale-prompt.md:43-44`) | related to the video mesh, not the danmaku panel |

Live danmaku path (for contrast), all of it reachable today:
`DanmakuOverlay()` → `DanmakuCanvasRuntime.advance()` → `DanmakuStreamSource.ensureSegmentLoaded/eventsInBucket`
→ `BilibiliPlaybackProvider.loadDanmakuSegment` → `BilibiliDanmakuProto.parse`, with metrics from
`DanmakuStreamSource.metricsFor` (`DanmakuRuntime.kt:90-94`).

---

## T10. Consolidated UNVERIFIED / NEEDS-HEADSET list

1. **UNVERIFIED** — dp→px conversion for `DpDisplayOptions(dpi = 800)` (`SpatialVideoSampleActivity.kt:1474`);
   the resulting canvas pixel dimensions and therefore the physical text size. SDK-internal.
2. **UNVERIFIED (source-level)** — whether `MediaOverlayValidator`/`DanmakuAllocator` are consumed by any
   *non-Kotlin* build step (e.g. a codegen or tool that reads the core module). Repo-wide Kotlin search says
   no; no other language build file referencing them was found.
3. **UNVERIFIED** — that `DanmakuOverlay` is merely *visible* (not composited away) in Browse/Detail routes;
   the panel is created `Visible(true)` and has no later visibility sync, but nothing in source proves the
   compositor keeps an ordinary UI panel above/behind the video layer. Device evidence in comments
   (`:2946-2952`) says the video compositor layer wins regardless of depth for scene geometry — that
   predicts danmaku *behind* the video when they overlap. **NEEDS-HEADSET** to confirm.
4. **NEEDS-HEADSET** — lane/font appearance under each stage-scale preset, danmaku extent relative to the
   letterboxed video quad (§T7), and the 33 ms cadence's smoothness (kanban k14 records "速度过快且有明显残影").
5. **NEEDS-HEADSET** — the 2D↔immersive output handoff exclusivity in practice (single player is proven;
   simultaneous live output targets are not).
6. **Not re-verified here** — historical kanban cards k13/k15/k17 describe an earlier entity-`Scale`
   implementation; the working tree now bakes scale into geometry (`SpatialVideoSampleActivity.kt:742-754`,
   `:2612-2639`). `docs/research/troubleshoot-stage-scale-prompt.md` matches that superseded implementation
   (e.g. it cites `applyPlaybackStageScale()` as setting `Scale` on the two overlays at "L1720" and
   `MR_SCREEN_*` at "L2169"); treat that document as history, not as current source truth.
