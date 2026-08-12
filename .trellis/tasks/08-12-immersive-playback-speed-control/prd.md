# Add Immersive Playback Speed Control

## Goal

Add an explicit playback-speed choice to the existing immersive transport panel
while retaining the same shared Media3 player and active video Surface.

## Scope

- Add a `Speed` command to the existing `controls_id` Android layout.
- Display the current normalized speed on that command.
- Use a standard Android menu anchored to that existing panel button with the
  fixed supported choices `0.75x`, `1x`, `1.25x`, `1.5x`, and `2x`.
- Apply the selected value through the existing process-wide Media3 player only.
  No source reload, prepare, seek, player recreation, panel creation, or Surface
  handoff may occur.
- Synchronize the speed label/check state when Media3 playback parameters change
  through another caller or after panel creation.
- Add pure JVM tests for supported choices, normalization, label formatting, and
  fallback behavior.
- Update the immersive panel runbook with ownership and Quest validation rules.

## Non-Goals

- No 2D UX redesign, new Compose UI, new Spatial panel/entity/anchor/transform,
  Bilibili source change, quality selector, autoplay setting, geometry setting,
  danmaku/caption, or Context rail change.
- No arbitrary/free-form speed input; the first release exposes only the fixed
  verified choices.
- No automatic Quest installation. Popup menu rendering in an embedded Spatial
  panel remains a manual device validation item.

## Acceptance Criteria

- Existing transport has a visible command whose label reflects current speed.
- Selecting a supported speed changes only the existing shared player playback
  parameters and keeps the current media, position, player identity, and video
  output intact.
- Panel creation and external playback-parameter changes update the label.
- Unsupported/non-finite player speed displays the safe `1x` fallback without
  attempting to write player state automatically.
- `:spatial-workbench-core:test`, `:app:testDebugUnitTest`, and
  `:app:assembleDebug` pass.
