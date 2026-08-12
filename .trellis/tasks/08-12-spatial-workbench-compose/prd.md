# Spatial Workbench Compose Shell

## Goal

Add the first reusable Compose layer for the spatial workbench and use it in
ViriViri's current search input panel without changing the existing search
behavior or visual layout more than necessary.

## Requirements

- Add a `:spatial-workbench-compose` Android library module depending only on
  `:spatial-workbench-core` and the existing AndroidX Compose stack.
- Implement a reusable `SpatialPanelShell` with named slots for:
  - `header`
  - `toolbar`
  - `mainArea`
  - `footer`
  - `overlay`
- Keep header, toolbar, and footer fixed while `mainArea` is a caller-owned
  content region. The shell must not force a particular scrolling component.
- Define a Compose-only style contract for background, padding, spacing,
  divider behavior, and panel transparency without importing Meta Spatial SDK.
- Preserve accessibility and stable layout dimensions for panel content.
- Refactor `SearchInputPanel` to use `SpatialPanelShell`:
  - search field in the header;
  - candidate strip and input board in mainArea;
  - clear/search/backspace actions in footer;
  - no Bilibili, Activity, Surface, or player logic in the shell.
- Add unit or compile-level coverage for the Compose module and retain the
  existing app search and build behavior.

## Non-Goals

- No Spatial SDK panel registration or entity changes.
- No changes to the input-method engine, Bilibili provider, search state, player,
  or 2D/immersive handoff.
- No full low-code renderer, theme JSON parser, Drawer, Popup, or FocusPanel in
  this task; those are later consumers of the shell.

## Acceptance Criteria

- `:spatial-workbench-compose:compileDebugKotlin` passes.
- `:spatial-workbench-compose:test` or the module's available test task passes.
- `:spatial-workbench-core:test` remains green.
- `:app:testDebugUnitTest` and `:app:assembleDebug` pass.
- `SearchInputPanel` is rendered through `SpatialPanelShell` and no longer
  owns the panel-level vertical structure itself.
- The Compose module's public source imports neither `com.meta.*` nor Media3
  nor Bilibili code.
