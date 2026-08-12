# Extensible Media Overlay Pipelines

## Goal

Document and stage the architecture for ViriViri media overlays. Danmaku and CC
captions share the rendering substrate, player clock, overlay-surface registry,
Flat/Spatial render modes, lifecycle, and performance budget, while remaining
independent content pipelines with separate sources, transforms, scheduling, and
presentation semantics.

## Core Model

```text
MediaOverlayEngine
├── PlayerClock
├── OverlaySurfaceRegistry
├── OverlayRenderer
│   ├── FlatOverlayRenderer
│   └── SpatialOverlayRenderer
├── DanmakuPipeline
│   ├── DanmakuSource
│   ├── DanmakuTransformPlugin[]
│   ├── DanmakuScheduler
│   └── DanmakuGroupAllocator
└── CaptionPipeline
    ├── CaptionSource
    ├── CaptionTransformPlugin[]
    ├── CaptionScheduler
    └── BilingualCaptionComposer
```

The pipelines share infrastructure only. Danmaku is not a caption subtype and
captions are not randomly distributed like danmaku.

## Shared Overlay Substrate

- The process keeps one `ExoPlayer` and one active video Surface.
- Overlay rendering is transparent UI/scene content over the existing
  `MEDIA_STAGE`; it never creates a player or video Surface.
- `OverlaySurface` is a renderer target with enabled state, capacity, supported
  content kinds, anchor binding, local pose/depth, and surface-level basic style.
- Flat and Spatial renderers consume a common timed overlay representation but
  may use different projection implementations.
- `PlayerClock` owns media-time semantics. Pause freezes active motion; seek,
  video replacement, and terminal surface invalidation clear active assignments
  and discard events outside the new scheduling window.
- Registry and renderer lifecycle must be idempotent. Disabling a surface removes
  its active items immediately; future events are reallocated. If no target is
  available, events are dropped rather than queued indefinitely.

```kotlin
enum class OverlayKind { DANMAKU, CAPTION }

enum class OverlayAnchorMode { STAGE_LOCKED, GAZE_LOCKED }

data class OverlaySurfaceSpec(
    val id: String,
    val enabled: Boolean,
    val supportedKinds: Set<OverlayKind>,
    val anchorMode: OverlayAnchorMode,
    val capacity: Int,
    val basicStyle: OverlayBasicStyle,
)
```

`STAGE_LOCKED` follows MediaStage transform/shape/geometry. `GAZE_LOCKED` follows
a stable head/view anchor and is appropriate for captions when the stage moves or
shrinks. Spatial adapter code binds IDs to Meta entities; core contracts do not
hold `Entity`, `Surface`, Activity, or renderer instances.

## Common Timed Item And Style

```kotlin
sealed interface OverlayContent {
    data class Danmaku(val text: String, val mergedCount: Int? = null) : OverlayContent
    data class Caption(
        val original: String,
        val translated: String? = null,
        val sourceLanguage: String? = null,
        val targetLanguage: String? = null,
    ) : OverlayContent
}

data class TimedOverlayItem(
    val id: String,
    val startMs: Long,
    val endMs: Long,
    val content: OverlayContent,
    val styleSnapshot: ResolvedOverlayStyle,
    val placement: OverlayPlacement,
)
```

The assignment stores a style snapshot and target. A theme/user style change
only affects future items; visible items do not jump layers, reverse direction,
or change writing mode.

## Danmaku Groups And Spatial Layers

A single surface is not the allocation unit for Spatial danmaku. A
`DanmakuSurfaceGroup` is a set of parallel surfaces sharing one normalized
projection coordinate space and one desktop-style lane occupancy model.

```text
DanmakuOcclusionDomain
├── cockpit-left-group
│   ├── layer 0 -> surface L0
│   ├── layer 1 -> surface L1
│   ├── layer 2 -> surface L2
│   └── layer 3 -> surface L3
└── cockpit-right-group
    ├── layer 0 -> surface R0
    ├── layer 1 -> surface R1
    ├── layer 2 -> surface R2
    └── layer 3 -> surface R3
```

```kotlin
data class DanmakuSurfaceGroup(
    val id: String,
    val allocationWeight: Float,
    val sharedCoordinateSpace: DanmakuCoordinateSpace,
    val laneSets: List<DanmakuLaneSetSpec>,
    val layers: List<DanmakuDepthLayer>,
    val occlusionPolicy: DanmakuOcclusionPolicy,
)

data class DanmakuDepthLayer(
    val id: String,
    val surfaceId: String,
    val depthOffsetMeters: Float,
    val maxActiveItems: Int,
    val basicStyle: DanmakuBasicStyle,
)

data class DanmakuLaneSetSpec(
    val id: String,
    val laneFamily: DanmakuLaneFamily,
    val emissionDirection: DanmakuEmissionDirection?,
    val laneCount: Int,
)
```

The shared coordinate space is normalized/projection space, not shared physical
meter coordinates. A Meta adapter maps each layer's local target pose and depth
to world space. Layers in one group are parallel to the stage by contract.

For cockpit left/right groups:

1. Select a group using enabled capacity, active load, available lane ratio,
   allocation weight, and stable event hash.
2. Within the group, select a desktop lane using text width, direction, speed,
   start time, and no-follow/追尾 prediction.
3. Select a depth layer that passes cross-layer viewer-projection occlusion,
   capacity, and load checks.
4. Select the physical surface and persist `group + lane set + lane + layer +
   surface` in the assignment.

The two groups do not share lane occupancy. They may skip cross-group occlusion
only when the theme declares their viewer-projected regions disjoint. If head
movement or layout makes their projections overlap, they must belong to the same
`DanmakuOcclusionDomain` and participate in global projection checks.

## Viewer-Projection Occlusion

Local surface normals are insufficient for oblique side surfaces. Two items can
be separate in L1/L4 local coordinates but overlap head-on when their projected
ends meet in the viewer's field of view.

`SPATIAL` allocation must therefore use a reference viewer pose and projected
bounds:

```kotlin
interface ViewerProjectionResolver {
    fun projectedBounds(
        assignment: ScheduledDanmakuAssignment,
        viewerPose: ViewerPose,
        atPlaybackMs: Long,
    ): ProjectedBounds
}

interface CrossLayerOcclusionResolver {
    fun conflicts(
        candidate: ScheduledDanmakuAssignment,
        scheduled: List<ScheduledDanmakuAssignment>,
        viewerPose: ViewerPose,
        sampleTimesMs: LongArray,
    ): Boolean
}
```

Check item entry, closest approach, and exit times. Apply a head-motion tolerance
margin. Do not reassign visible items every frame; retain the assignment until
end. A large viewer relocation, seek, stage/theme switch, or pause-resume policy
may clear active items and reschedule from the new reference pose.

```kotlin
enum class DanmakuOcclusionPolicy {
    AVOID_PROJECTED_OVERLAP,
    ALLOW_BRIEF_OVERLAP,
    FRONT_LAYER_WINS,
}
```

Default is `AVOID_PROJECTED_OVERLAP`. If every layer conflicts, try another lane;
if all lanes conflict, drop the event rather than stack unreadable text.

## Emission And Text Layout

Emission direction and text writing direction are independent configuration:

```kotlin
enum class DanmakuLaneFamily { SCROLLING, TOP_FIXED, BOTTOM_FIXED }

enum class DanmakuEmissionDirection {
    LEFT_TO_RIGHT,
    RIGHT_TO_LEFT,
    TOP_TO_BOTTOM,
    BOTTOM_TO_TOP,
}

enum class TextWritingMode { HORIZONTAL_TB, VERTICAL_RL, VERTICAL_LR }

enum class TextDirection { AUTO, LTR, RTL }

data class OverlayTextLayoutStyle(
    val writingMode: TextWritingMode = TextWritingMode.HORIZONTAL_TB,
    val direction: TextDirection = TextDirection.AUTO,
    val maxLines: Int = 1,
    val lineSpacingRatio: Float = 1f,
    val overflow: TextOverflowPolicy = TextOverflowPolicy.CLIP,
)
```

`SCROLLING` requires an emission direction. Fixed top/bottom lanes normally do
not. Direction is relative to the surface local tangent/axis and is mapped to
world/viewer projection by the renderer; sources never write world coordinates.

Basic style resolution is:

```text
user preference -> theme/group default -> surface basicStyle -> event override
```

Surface-level style is required for depth compensation, such as larger glyphs on
far layers and smaller glyphs on near layers. Scheduler code must not estimate
bounds from raw string length:

```kotlin
interface OverlayMetricsResolver {
    fun measure(
        content: OverlayContent,
        textLayout: OverlayTextLayoutStyle,
        style: ResolvedOverlayStyle,
        surface: OverlaySurfaceSpec,
    ): OverlayMeasuredBounds
}
```

Measured glyph bounds feed track collision, cross-layer projection, capacity,
entry/exit duration, bidi shaping, vertical writing, and bilingual wrapping.

## Danmaku Pipeline And Plugins

```kotlin
interface DanmakuSource {
    suspend fun loadWindow(video: VideoIdentity, window: TimeWindow): List<DanmakuEvent>
}

interface DanmakuTransformPlugin {
    val id: String
    val version: String
    val order: Int
    fun transform(events: List<DanmakuEvent>): List<DanmakuEvent>
}
```

Plugins run before scheduling, are ordered explicitly, declare version and
idempotency expectations, and fail in isolation. `DuplicateMergePlugin` is an
optional compatibility plugin. Its initial documented behavior matches the
PiliPlus reference: within a loaded six-minute segment, merge equal normalized
content, increment `mergedCount`, then bucket by approximately 100ms. Future
versions may add configurable window, case/whitespace normalization, color/
position/user distinction, and source ID retention without changing source or
renderer contracts.

Bilibili source adapters remain separate:

- Initial web adapter: `bvid -> cid -> /x/v1/dm/list.so?oid=cid`, handle deflate
  response and XML decoding, with bounded window/cache and no auth requirement
  assumption.
- Segmented mobile adapter: `DmSegMobile` protobuf with six-minute segment
  requests, when the required protocol/client metadata is available.
- Mock source: deterministic local events for unit and Quest tests.

A source failure disables only the danmaku source and leaves video playback and
captions running. XML and Proto details must not leak into scheduler/renderer.

## Caption / CC Pipeline

Captions use the shared clock, target registry, renderer, metrics and lifecycle,
but never the danmaku group allocator:

```kotlin
data class CaptionCue(
    val id: String,
    val startMs: Long,
    val endMs: Long,
    val originalText: String,
    val sourceLanguage: String?,
    val translatedText: String? = null,
    val targetLanguage: String? = null,
)

enum class CaptionDisplayMode { ORIGINAL_ONLY, TRANSLATED_ONLY, BILINGUAL }
```

CC target selection is singular and user-controlled:

- `STAGE_LOCKED`: follows MediaStage geometry.
- `GAZE_LOCKED`: follows the stable viewer/gaze anchor.

Bilingual text is one cue with original/translation lines and one schedule; the
lines must not be independently dispatched. Missing/failed translation keeps the
original cue visible.

Caption transforms are separate from danmaku transforms. Sources may include
Bilibili CC, embedded/local VTT/SRT, and generated captions. Normalization,
language selection, translation, bilingual composition, line breaking and
redaction are independently testable plugins.

## Optional LLM Translation

LLM translation is an optional `CaptionTransformPlugin`, never a theme action or
renderer responsibility. Settings may expose provider, model, endpoint, target
language, auto-translate, current-cue/future-window policy, concurrency and
failure fallback. Secrets live in platform secure storage and never in theme
JSON, core contracts, logs, Bilibili headers, or player metadata.

Only the minimum cue text and language metadata may be sent. Do not send cookies,
play URLs, account IDs, full video metadata, or raw danmaku unless a future
explicit feature contract permits it. Cache by:

```text
cueId + sourceTextHash + targetLanguage + provider + model
```

Apply bounded concurrency, timeout, cancellation on seek/video change, cache
limits, and original-text fallback for errors, rate limits, offline mode, or
missing credentials. Translation never blocks video playback or the base caption
cue.

## Missing / Resolved Edge Cases

- **Seek**: clear active assignments and drop stale events; request the new
  window, never replay an old backlog.
- **Pause**: freeze motion and retain active items until the configured pause
  policy; do not advance media-time scheduling.
- **Surface disable**: immediately release active items and reallocate future
  events only.
- **Theme/layout change**: preserve media/player; clear/recompute overlay
  assignments under the new topology.
- **No capacity**: drop the event with a metric, never unboundedly queue it.
- **Head movement**: use assignment-time reference pose plus tolerance margin;
  do not per-frame reassign.
- **Writing mode changes**: affect new assignments; retain visible snapshots.
- **Bilingual missing translation**: show original only.
- **Plugin/source errors**: isolate to that pipeline and expose diagnostics.
- **Single Surface invariant**: overlay targets are not video output Surfaces.

## Staged Implementation

### Stage 0: Documentation and contracts

Define core-neutral overlay kinds, timed content, styles, surface registry,
DanmakuSurfaceGroup, OcclusionDomain, lane sets, assignments, viewer projection,
caption cues, anchor modes, plugin contracts, and settings/privacy contracts.
Add deterministic validator tests and mock fixtures.

### Stage 1: Shared clock and Flat renderer

Implement a platform-neutral scheduler state machine and Compose Flat renderer
using mock danmaku/caption sources. Verify pause, seek, item cleanup, surface
enable/disable, style snapshots, bilingual cues, and one target for captions.

### Stage 2: Real sources and transforms

Add Bilibili XML/deflate source, optional segmented Proto source, CC/VTT source,
`DuplicateMergePlugin`, caption normalization, mock translator, bounded cache and
failure fallback. No LLM provider is required to complete this stage.

### Stage 3: App 2D integration

Overlay Flat renderer above the existing 2D video view without adding a video
Surface. Connect current `cid`/video identity, player clock and lifecycle. Use
real source only when enabled; keep mock switch for regression. Validate app unit
and device behavior.

### Stage 4: Spatial multi-surface adapter

Bind `OverlaySurfaceRegistry` IDs to Meta entities, implement parallel surface
layers, group allocation, assignment-time viewer pose, projection occlusion,
local emission direction, and per-surface style compensation. Integrate cockpit
left/right groups and cinema stage group without modifying player ownership.

### Stage 5: CC gaze/stage anchor and LLM provider

Add GAZE/STAGE anchor binding, bilingual caption settings, secure LLM provider
adapter, translation cache/prefetch/cancellation, privacy controls and Quest
regression. LLM remains optional and cannot block base captions.

## Acceptance And Device Checks

- Core contracts validate topology, lane direction, capacity, styles, writing
  modes, cue ranges, plugin ordering and privacy-safe translation settings.
- Unit tests reproduce stable hash allocation, group/load balancing, same-lane
  no-follow, cross-layer viewer projection overlap including L1/L4 oblique
  surfaces, style-dependent bounds, seek/disable cleanup, merge plugin output,
  bilingual fallback, and translation cache keys.
- Compose tests verify Flat renderer geometry, bilingual layout and empty/error
  states without owning a player or Surface.
- App tests verify real-source parsing and failures without requiring a live
  network.
- Quest tests verify 2D and Spatial overlay rendering, gaze/stage anchor
  behavior, multi-group allocation, no unreadable cross-layer overlap, single
  player/single video Surface, handoff lifecycle, and graceful source/LLM failure.

## Non-Goals

- No comment/reply/DM write APIs.
- No theme-owned network requests or arbitrary executable plugins.
- No second video player, video Surface, Activity, or static scene entity.
- No requirement that LLM translation be enabled for base CC captions.
