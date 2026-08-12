# Spatial Workbench Core Contract Extension

## Goal

Implement the next pure Kotlin/JVM contract layer described by the archived cinema
and Shorts UX documents. Keep the module independent from Android, Compose, Meta
Spatial SDK, Media3, Bilibili, Activity, Surface, and network code.

## Scope

1. Extend semantic spatial contracts:
   - add `SHORTS`, `SHORTS_DETAILS`, and `SHORTS_COMMENTS` identifiers;
   - add `ImmersiveLayoutMode.SHORTS`;
   - represent front-of-stage transport ordering with a neutral spatial depth
     relation, not renderer or CSS coordinates;
   - represent list-canvas visible overflow and hit-test preservation.
2. Add panel presentation and canvas contracts:
   - support `PERSISTENT`, `AUTO_FADE`, `ON_DEMAND`, `TRANSIENT`, and
     `THEME_CONTROLLED` policies;
   - expose theme-level per-slot policy lookup;
   - ensure a persistent slot is never eligible for default quiet/canvas hiding;
   - define immutable `WorkbenchCanvas` records for independent quiet/control or
     other canvas compositions.
3. Add default cinema component contracts:
   - provide `DefaultCinemaPlaybackCanvasGroup` as a convenience component tree;
   - include independently addressable members for status, navigation, stage,
     title, transport actions, timeline, config popup, and grab handle;
   - include fixed Shorts details/stage/comments rail members without taking
     player or Surface ownership.
4. Add semantic cinema palette contracts:
   - define role-based colors and surface opacity using pure Kotlin values;
   - provide dark, light, and high-contrast presets;
   - validate channel ranges, opacity, and basic text/surface contrast;
   - derive interaction colors from semantic roles rather than arbitrary per-node
     colors.
5. Add search playback-origin and input contracts:
   - model source-aware search-result restoration with cache identity, snapshot
     identity, pagination cursor, filters, and scroll position;
   - return a deterministic cache-miss fallback decision;
   - model candidate-declared composition ranges and partial consumption;
   - preserve remaining Pinyin and provide the external final-text fallback that
     appends raw composition before final system/voice text and clears composition.

## Non-Goals

- No Android or Compose implementation.
- No runtime theme parser, JSON loader, network request, player, Surface,
  Activity, Spatial entity, or Meta Spatial Editor scene change.
- No app-state migration or Bilibili API integration.
- No implementation of the 26-key board UI; only pure input state contracts and
  deterministic reducer behavior are included.

## Acceptance Criteria

- `:spatial-workbench-core:test` passes with focused tests for Shorts layout,
  transport depth, presentation policy, canvas overflow, default component group,
  palette validation/presets, search-origin cache restore/fallback, partial
  candidate consumption, and external-text composition fallback.
- `CinemaTheme.create()` includes valid WATCH, FOCUS, EDIT, and SHORTS layouts,
  valid default canvases and component group members, and passes validation.
- The validator rejects missing SHORTS layouts, invalid transport overlay
  references, visible-overflow canvases that do not preserve hit testing, invalid
  palette values/contrast, malformed candidate ranges, and invalid search-origin
  state.
- Core source remains free of platform and product-layer imports.

## Design Notes

- Renderer-specific top offsets are intentionally absent from the core model.
- `TRANSPORT` is represented as a front overlay relation to `MEDIA_STAGE`.
- Shorts details/comments use semantic slots so a Meta adapter can bind fixed
  rails later; the component group remains decomposable.
- Search-result snapshots are opaque IDs/item IDs in core. The app layer owns
  actual result objects and cache eviction.
