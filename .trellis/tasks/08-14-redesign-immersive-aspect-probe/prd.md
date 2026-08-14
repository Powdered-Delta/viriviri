# 重做沉浸式纵横比诊断面板

## Goal

把现有沉浸式 aspect probe 改为明确的三段式实验工具：选择目标显示比例、选择提交方案、点击 Apply 后才强制当前播放内容使用目标比例的 mesh geometry。

## Problem

当前 probe 只选择 `Geometry only` / `Commit false` / `Commit true`，选择后立即使用 Media3 原始 `VideoSize` 重放 geometry。它不能验证预设显示比例，也不能让用户在选择多个参数后显式确认应用。

## Required Interaction

```text
Target aspect: Default / 16:9 / 4:3 / 1:1 / 9:16
Rendering plan: Plan 1 / Plan 2 / Plan 3
Apply
```

- `Default` derives the target ratio from the current Media3 `VideoSize` and pixel ratio.
- A ratio preset uses that fixed target display ratio for the contained foreground quad, even when it differs from the decoded source ratio.
- Selecting a target or plan only changes the pending selection.
- `Apply` updates the current immersive mesh exactly once using the selected target and plan.

## Scope

- Keep the existing debug-only `mode_panel`; enlarge it only as needed to comfortably contain all controls.
- Add pure Kotlin target-aspect and probe-selection contracts with tests.
- Reuse the existing `TriangleMesh`, optional `SceneMesh` commit paths, one player, and SDK-owned active video Surface.
- Preserve the current default runtime as source aspect plus Plan 1, so a debug user must explicitly apply an experimental commit path.
- Show pending and applied target/plan, source aspect, resolved target aspect, and target quad dimensions in the panel and bounded debug log.

## Non-goals

- Do not change the Media3 stream, decoder output, `VideoSize`, player scaling mode, Surface buffer, source URL, or 2D `TextureView` behavior.
- Do not create a new panel/entity/player/Surface, add a shader, or modify Meta Spatial Editor scene content.
- Do not assert any plan as the final aspect fix before Quest validation.

## Acceptance Criteria

- [x] Target-aspect presets and contain geometry are pure Kotlin and unit-tested.
- [x] Target and plan selection do not apply geometry before Apply.
- [x] Apply forces current immersive foreground geometry to the selected target aspect while retaining the full-stage shadow/input footprint.
- [x] Default + Plan 1 remains the initial applied configuration.
- [x] Debug panel displays distinct controls for target, plan, and Apply and uses debug-only dimensions of `420dp x 380dp` / `1.0m x 0.8m`.
- [x] No extra player, Surface, entity, panel, shader, 2D regression, or scene reparenting.
- [x] Windows JDK 17 `:app:testDebugUnitTest :app:assembleDebug` passed in 23s with `:app:export`.
- [ ] Quest validation records target, plan, source/target geometry, video visibility, and observed displayed ratio for each tested combination.

## Quest Procedure

1. Start on `Target: Default`, `Plan: Plan 1`; this is the known-visible baseline.
2. Select a target ratio, then select a plan. Confirm the current video does not change before pressing `Apply`.
3. Press `Apply` and record the displayed ratio, whether the video stays visible, and the `ViriViriAspect` log values.
4. Restart before testing another plan if a plan makes the video disappear.
5. Test at least `Default`, `16:9`, `1:1`, and `9:16` against the same portrait source, then repeat the chosen target with each Plan.
