# Spatial Workbench Core Contracts

## Goal

Create the first internal module for ViriViri's reusable immersive workbench.
The module defines the pure Kotlin contracts required for low-code themes before
any existing panel, Media3, Bilibili, Compose, or Spatial SDK implementation is
migrated.

The module is the foundation for a later component library and demo applications;
it is not a visual redesign task.

## Requirements

- Add a `:spatial-workbench-core` Kotlin/JVM library module that contains no Meta
  Spatial SDK, Compose, Media3, Bilibili, Activity, Surface, or network
  dependency.
- Define stable semantic `PanelSlot` identifiers for `MEDIA_STAGE`, `TRANSPORT`,
  `SYSTEM_TOOLBAR`, `BROWSE`, `CONTEXT`, `FOCUS`, and `ACTION_SHEET`.
- Define `ImmersiveLayoutMode` with `WATCH`, `FOCUS`, and `EDIT`.
- Define immutable theme contracts for:
  - Theme identity and scene reference.
  - Per-layout-mode slot visibility and placement.
  - Spatial placement expressed with neutral meters, Euler degrees, panel size,
    shape, anchor reference, and optional parent slot.
  - A named PiP dock and content exclusion metadata.
  - System toolbar module ordering.
- Define a declarative component tree contract for `Panel`, `TabBar`, `Drawer`,
  `Popup`, `ActionSheet`, `FocusPanel`, layout primitives, and semantic modules.
  The contract must support component properties, ordered children, named slots,
  and data binding paths without evaluating scripts.
- Define a sealed, whitelisted `ThemeAction` contract. Actions may express
  navigation and playback intent but must not embed arbitrary Kotlin, URLs,
  player instances, or Surface references.
- Implement deterministic validation that rejects duplicate component IDs,
  invalid or missing slot references, negative panel dimensions, invalid
  cylinder geometry, duplicate toolbar module IDs, and forbidden action/data
  binding forms.
- Provide a `CinemaTheme` fixture that represents the current five-panel
  workbench only through semantic slots and mock component definitions. It does
  not alter the current ViriViri visual layout.
- Add focused unit tests for theme validation, layout mode resolution, the
  component tree, and allowed action handling.
- Preserve all existing application behavior and build targets.

## Non-Goals

- No migration of `RecommendationUi`, `SearchInputPanel`, player controls, or
  existing Spatial panel registrations.
- No Meta Spatial Editor scene creation or edits.
- No media-stage implementation, PiP animation, curved panels, danmaku
  rendering, API changes, login, or Bilibili provider changes.
- No runtime theme loading, JSON parsing, plugin execution, visual editor, or
  independent repository extraction in this task.

## Acceptance Criteria

- `:spatial-workbench-core:test` passes on the host JVM.
- `:app:testDebugUnitTest` and `:app:assembleDebug` continue to pass.
- The core module's public source imports neither `android.*` nor `com.meta.*`
  nor `androidx.media3.*` packages.
- `CinemaTheme` validates successfully and can resolve both `WATCH` and
  `FOCUS` placement for `MEDIA_STAGE` without a UI runtime.
- Invalid fixture tests demonstrate all required validation failures.

## Follow-up Tasks

1. Add `:spatial-workbench-compose` with `SpatialPanelShell` and the modular
   search migration.
2. Add `:spatial-workbench-meta` with slot binding, workspace grab handling,
   panel layer fading, and layout mode transitions.
3. Add `:spatial-workbench-media` with `MediaStage`, stage geometry, PiP dock,
   and danmaku contracts.
4. Create Mock-data cinema and cockpit demos after the core API has a real
   application consumer.
