# Infrastructure Roadmap

This document records product and platform decisions that should shape future
implementation. It is not an implementation commitment or an SDK protocol
reference.

## Current Priorities

1. Finish and stabilize the redesigned immersive UX, especially the semantic
   Browse and future Library information architecture.
2. Validate the one-player, one-active-video-Surface Spatial media path across
   ordinary landscape and portrait sources.
3. Preserve device observations with build identity and bounded logs before
   attributing system behavior to ViriViri.
4. Introduce persistent user data only when it has a stable product surface and
   explicit ownership, privacy, and deletion behavior.

## Quest Tracking Observation

The current debug wrist panel is only a tracking probe. Quest testing observed
intermittent spatial or hand-follow drift. The cause is not yet known: it may
involve Horizon OS tracking, `LOCAL_FLOOR` reference-space changes, or
application lifecycle behavior.

Do not change panel transforms, add yaw compensation, or re-anchor scene
objects based on this observation alone. A future reproduction must capture:

- `DEV <git-hash>` from the running headset build;
- whether every world panel drifts or only the wrist panel;
- the action path, including long-press reset, system menu, 2D/immersive route,
  passthrough, and hand-tracking availability transitions;
- `adb logcat -d -s ViriViriSpatial:I` and a timestamped visual description.

If all fixed panels drift together, investigate the system reference space first.
If only the wrist panel drifts, investigate `AvatarBody` hand/head transforms and
visibility policy separately.

## Restricted Bilibili Content

The anonymous UGC provider assumes the signed web `playurl` response contains
`data.dash` with an AVC video and MPEG-4 audio track. A charging-exclusive video
may legitimately omit that structure for an unauthenticated or unauthorized
request. `Bilibili did not provide DASH streams` therefore means the provider
received no compatible ordinary UGC DASH payload; it does not prove a decoder
or spatial-video fault.

Before compatibility work, preserve the affected BV ID and capture only bounded,
non-sensitive protocol facts: endpoint result code/message, whether `data.dash`
exists, and whether an explicit access/charging marker is present. Never log
signed media URLs, cookies, SESSDATA, CSRF values, device identifiers, or full
response bodies.

A later restricted-content contract should distinguish at least:

```text
Playable: ordinary source resolved to compatible streams.
Restricted: source needs a Bilibili entitlement or authenticated access.
Unsupported: source type is outside the current UGC DASH provider contract.
Unavailable: transient API, network, or malformed response failure.
```

The list and viewer may eventually present Restricted as an unavailable/access
state. They must not attempt unauthenticated credential bypasses or speculative
MP4 fallbacks.

## Watching History

ViriViri will eventually support watching history, but it should not be
implemented until the redesigned immersive Browse/Library placement and
navigation model are stable. The first persistent history UI belongs to that
future Library surface, not to a temporary debug or legacy panel.

### Local History First

The future local record should be owned by an app repository, not by a player,
Spatial panel, Compose UI, or Bilibili provider. A tentative record shape is:

```text
content ID, title, author, cover reference,
last position, known duration, last watched time, completion state
```

It must have bounded retention, explicit clear/delete actions, and offline
behavior. Recording cadence and completion thresholds must be deterministic and
shared by the 2D and immersive hosts. The single shared Player remains playback
truth; persistence observes controlled snapshots and never creates a second
player or video Surface.

### Optional Bilibili Sync

Bilibili sync is a separate feature from local history and defaults to off.
It requires all of the following before any request can be sent:

1. Explicit Bilibili login and consent for history synchronization.
2. Secure credential storage and a deletion/sign-out path.
3. A user-visible enable/disable control distinct from local-history recording.
4. A bounded, cancellable report policy with no silent retry storm.
5. Verified request contracts for history report/heartbeat/list behavior.
6. Clear handling when a source is restricted, unsupported, or not eligible.

Until these conditions exist, ViriViri must remain anonymous: it sends no
Cookie, SESSDATA, CSRF value, access token, or playback heartbeat. It may retain
local history only after that separate implementation is approved.

## PiliPlus Reference

PiliPlus demonstrates why local and remote history must be separated. Its history
list, pause state, delete/clear controls, report endpoint, and progress heartbeat
are distinct account-aware operations. Its report and heartbeat requests use an
explicit authenticated account and CSRF value, while its history UI can query
server state. ViriViri should borrow the separation of concerns, not copy the
protocol into its anonymous provider.
