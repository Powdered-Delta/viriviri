# Media Overlay Core Contracts

## Goal

Implement Stage 0 of the documented Media Overlay architecture in
`:spatial-workbench-core`. Provide immutable, platform-neutral contracts and
deterministic validation/allocation for shared overlay targets, Spatial danmaku
groups, captions, text layout, plugins, and translation settings.

## Scope

- Add core contracts for `OverlayKind`, anchor modes, surfaces, styles, timed
  items, assignments, lane families, emission direction, text writing direction,
  caption cue/display state, plugin metadata, and translation settings.
- Model `DanmakuSurfaceGroup`, depth layers, lane sets, and
  `DanmakuOcclusionDomain`.
- Implement deterministic allocation that:
  - filters disabled/incompatible/capacity-exhausted targets;
  - balances eligible groups using available lane ratio, active load, weight, and
    stable event ID hash;
  - assigns a compatible lane set/lane/depth surface;
  - uses injected metrics and occlusion resolver interfaces; and
  - snapshots resolved style and assignment data.
- Keep local desktop-lane scheduling and viewer-projection conflict checks
  injectable/pure contracts in this stage. Do not invent Meta coordinate or
  renderer code in core.
- Validate surface/group topology, lane direction rules, duplicate IDs, capacity,
  style bounds, cue timing, translation configuration safety, and theme/core
  independence.
- Add tests for stable allocation, group balancing, disabled surfaces, lane
  direction, basic-style snapshot, cue validation, and invalid topology.

## Non-Goals

- No Compose, Meta Spatial SDK, Media3, Activity, Surface, network source,
  XML/Proto parsing, real LLM provider, Entity binding, or renderer.
- No final 2D/Spatial collision implementation. Core exposes metrics/occlusion
  interfaces and deterministic inputs only.
- Do not mutate existing player ownership or input UI.

## Acceptance Criteria

- `:spatial-workbench-core:test` passes with focused overlay tests.
- `:app:testDebugUnitTest` and `:app:assembleDebug` continue to pass.
- Core source imports no Android, Compose, Meta, Media3, Bilibili, network, or
  Activity/Surface APIs.
- Allocation is stable for equal inputs and visibly changes only when enabled
  topology/capacity/style inputs require it.
- No untrusted executable plugin, API key, endpoint, or secret can enter theme
  or core overlay contracts.
