# PRD — Immersive controller input injection for automated testing

Status: planning (research complete, no code yet).
Research: [`docs/research/immersive-input-injection-testing.md`](../../../docs/research/immersive-input-injection-testing.md)

## Problem

The immersive UI (Spatial panels, stage, workbench) can only be exercised by hand: host-side
input (`adb shell input`, UI Automator) cannot address 3D/VR-composited content, and Meta
Spatial Simulator is 2D-only. Every regression in panel wiring, controller handling or stage
tuning is currently verified by wearing the headset.

## Decision needed

Approve building an in-process, debug-only test-input seam and a first `androidTest` suite.

## Proposed scope (Phase 1)

1. `TestInputDriver` in `app/src/debug/`: panel click/hover/drag (L0), virtual controller
   state (L1), state snapshot.
2. Three instrumentation tests: transport play/pause, stage trigger toggle, thumbstick scale.
3. Optional: localhost JSON transport for host-driven exploration (debug only).

## Non-goals

- Native OpenXR/ISDK grab injection.
- Release-build test hooks.
- Compose-semantics assertions (unverified; state/view-tree first).

## Acceptance criteria

- A test can click `controls_id` play/pause and observe `isPlaying` toggle, 10/10 runs, with
  no controller in hand.
- A test can drive the stage trigger and observe the workbench transition.
- A test can hold the right thumbstick and observe the stage scale step.
- Driver is absent from release builds.
