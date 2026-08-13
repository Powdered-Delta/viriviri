# Geometry And Wrist Probe Boundary

## Aspect Probe

The device previously showed a full-stage black visual plane behind the video.
It came from an application-added backdrop primitive at local `-Z`; it was not
a Media3 letterbox and did not correct video sampling. The probe removes that
material and its vertices, UVs, and primitives entirely.

The existing video mesh returns to one adaptive content quad plus its preexisting
shadow footprint. On each distinct `VideoSize` after the mesh exists, debug
builds log one `ViriViriAspect` event after `SceneMesh.updateWithTriangleMesh`.
The event records source dimensions, pixel ratio, display aspect ratio, target
half-width/half-height, and `meshCommit=true`. It is not a frame log and makes
no player, Surface, source, or panel lifecycle calls.

A Quest result where the event contains `1080x1920`, display aspect `0.5625`,
and quad half-size `0.253125x0.45`, while visible video remains stretched,
is evidence that the remaining fault is downstream of contain geometry: the
Spatial material/UV or raw-Surface compositor path. This task does not guess at
an unsupported shader uniform or change the `updateWithTriangleMesh` boolean
without SDK evidence.

## Wrist Debug Panel

The bundled Meta Spatial scanner showcase implements wrist attachment as a
dynamic local ECS entity. It reads the local player `AvatarBody` left-hand and
head `Transform` components, updates a panel entity pose at runtime, and hides
it when tracking transforms are unavailable.

ViriViri follows that dynamic pattern only in debug builds. The small panel
contains `DEV <BuildConfig.GIT_SHA>` and is intentionally non-interactive. It
owns no video output Surface and does not read/write player state. It exists
only for device validation of tracking, visibility, and input isolation; it is
not a static scene entity, live-room feature, or persistent user-facing tool.
