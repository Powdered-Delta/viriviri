# Playback stage core (M1)

Pure Kotlin/JVM contracts, geometry and frame coordination for the presentation rebuild.
Only the Kotlin standard library is used at runtime; tests use JUnit. There are no Android,
Meta SDK, Media3, Compose, app or workbench dependencies.

This module contains SDK-independent contracts. The app no longer contains the
old Host adapter or an opt-in SDK backend. Production rendering is owned by
PlaybackStageController and PlaybackStagePanelRenderer in the app, using direct
video replacement and retained danmaku with scene writes disabled. These core
contracts are not a second runtime rendering path or a fallback implementation.
See [production rendering](../docs/production-stage-rendering.md).

## Geometry

`StageInput` is one complete immutable input. Normalize raw controls once, then
`solveStageLayout` derives a 1.6 x 0.9 metre viewport, source/override aspect fit, scaled radius
and semantic surface distances. Video sampling must remain complete-frame in any backend;
overlays occupy the whole viewport. The fixed bottom anchor gives centre Y `0.45 * scale + 0.06`;
centre Z is zero for flat and curved shapes. Native cylinder axes and world poses belong to a
future backend. Effective radius 15.8 m or 1.05 m is valid and must not pass through raw controls.

## Frame and lifecycle contract

- `submit` accepts a complete input and coalesces changed values into a new revision. It calls
  no backend. Resource callbacks call `resourceChanged(role, generation, ready)`; generation
  must increase on replacement. Older notices and identical notices are ignored.
- The serialized frame owner calls `renderFrame`. Each immutable `StageFrame` names only
  roles needing application and captures their generations. A missing role returns `NotReady`
  and retries on later frames. Successful peers do not reshape again.
- Backend geometry must land before visibility. Video can apply without overlays. Overlays
  acknowledge only against the successfully applied video revision; hidden video still has
  valid geometry and does not block independently visible backdrop or idle completion.
- Explicit failures latch per revision/resource. A new input, changed generation/readiness or
  `retry(role)` permits a retry. Retrying failed video also unblocks its failed overlays.
  `failure(role)` preserves revision, generation and reason across idle frames until success.
- Coordinator returns validated outcomes. Missing/wrong-generation results become failures;
  resource replacement during apply cannot acknowledge the replacement. New input during
  apply is deferred to the next frame, while actual submitted geometry remains identifiable.
- `StageRenderer.hide` is an idempotent synchronous failure barrier. Overlay failures hide that
  output; video failure or an apply exception hides the group. Hidden/failed video has no
  `inputTarget`; `applied(VIDEO)` may retain historical evidence. Desired geometry never
  becomes a hit target before successful video application.
- `close` sets terminal state before backend cleanup, ignores late inputs/notices and rejects
  late apply results. Call `renderFrame` and `close` on the same frame/lifecycle owner. Backend
  `close` is called once even if it throws. If hide fails, the coordinator closes terminally.

The stateful recording test backend creates/replaces role resources, validates geometry,
records geometry-before-visibility commands, models partial mutation/failure and tracks hiding.
It proves coordination behavior without pretending to validate native configuration or pixels.

## Verification

On Windows with JDK 17:

```powershell
.\gradlew.bat :playback-stage-core:test
.\scripts\build-windows-debug.ps1
```

The Windows script includes the core test task before app tests and APK assembly.
Build/test success is resource-contract evidence only; it does not establish production
headset visual acceptance. The debug POC B acceptance is recorded separately.
