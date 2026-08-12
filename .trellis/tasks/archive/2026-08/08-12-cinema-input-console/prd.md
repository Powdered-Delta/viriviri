# Cinema Input Console Theme

## Goal

Implement the Compose visual layer for the reusable cinema input console. The
console must render the existing application-owned `SearchInputSession` and
`SearchInputMethod` through theme-controlled style tokens without taking over
input reduction, SearchSession state, system IME/voice lifecycle, player, Surface,
Activity, or network ownership.

## Requirements

- Extend `spatial-workbench-compose` with pure visual style contracts:
  - map core `CinemaPalette` semantic roles to Compose colors;
  - define `InputConsoleStyle` for shell, number/alphabet/action keys,
    composition, candidate strip, candidate popup, selected language, disabled,
    and spacing tokens;
  - derive hover/pressed/focus/disabled colors from semantic roles rather than
    arbitrary per-component colors.
- Refactor existing search components to accept optional style tokens while
  preserving their current callback/action contracts and default appearance.
- Add `CinemaInputConsole` as a composable convenience group with independently
  addressable sections:
  - query field/header;
  - fixed-height composition row;
  - candidate mode and one-row candidate strip;
  - trailing expand action and anchored candidate popup overlay;
  - keyboard board;
  - action column/row including backspace, voice, system IME, dismiss, and
    submit callbacks.
- Keep candidate/composition geometry stable when candidates are empty or change:
  - composition row remains fixed height;
  - collapsed candidate strip remains fixed height;
  - expanded candidates render in an overlay above the strip and do not push the
    alphabet board.
- Expose a `CinemaInputConsoleActions` callback contract. Voice entry from the
  query header and keyboard action area must dispatch the same callback supplied
  by the host. System IME remains a separate explicit callback.
- Preserve existing `SearchInputPanel` behavior by adapting it to the new group
  or style defaults without changing reducer semantics.
- Temporary Quest readability exception: while the input console remains embedded
  in the existing `video_selector_panel`, enlarge that pre-existing panel to
  `1.2m x 2.0m`. Compensate its initial pose along its local left axis so its
  local right edge remains fixed and does not expand over `MEDIA_STAGE`. This is
  user-approved Kotlin tuning because `mse-agent` is unavailable; replace it
  with scene-authored anchors and an overlay input console when the Meta adapter
  is implemented.
- Add Compose tests or deterministic UI-level tests for palette mapping, fixed
  geometry/style defaults, empty/non-empty candidate rendering state, and shared
  voice callback wiring where the project test setup permits.

## Non-Goals

- No 26-key Pinyin engine, candidate ranking, composition reducer, Enter behavior,
  SearchSession changes, or system IME implementation.
- No Meta Spatial SDK, scene anchor, player, Surface, Activity, network, or
  Bilibili changes.
- No arbitrary theme JSON parsing or executable theme actions.
- No forced migration of unrelated playback UI.

## Acceptance Criteria

- `:spatial-workbench-compose:compileDebugKotlin` passes.
- `:spatial-workbench-core:test` and `:app:testDebugUnitTest` pass.
- `:app:assembleDebug` passes.
- Existing `SearchInputPanel` still renders the same state/action flow using
  default style values.
- `CinemaInputConsole` visibly keeps composition/candidate rows at stable
  heights and renders expanded candidates as an overlay.
- The Compose module imports no Meta SDK, Media3, Bilibili, Activity, player, or
  Surface ownership APIs.
- Tests verify semantic palette colors are used by the default cinema style and
  that voice callbacks are shared between both entry points.
