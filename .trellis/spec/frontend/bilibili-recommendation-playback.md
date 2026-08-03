# Bilibili Recommendation Playback

## Scenario: Shared Recommendation and Playback Session

### 1. Scope / Trigger

Use this pattern when ViriViri loads Bilibili recommendations and plays a
selected item through the same process-scoped Media3 session in a 2D Horizon OS
window and an immersive Spatial panel.

### 2. Signatures

```kotlin
fun BilibiliPlaybackProvider.loadRecommendations(): List<Recommendation>
fun BilibiliPlaybackProvider.createMediaSource(videoId: String): MediaSource
fun ViriViriAppState.selectRecommendation(recommendation: Recommendation)
fun PlayerSession.beginOutputHandoff()
fun PlayerSession.attachImmersiveSurface(surface: Surface)
fun PlayerSession.attach2dSurface(surface: Surface)
```

### 3. Contracts

* Recommendations use `GET /x/web-interface/wbi/index/top/feed/rcmd` with the
  web-feed parameters. The response source is `data.item` and only a usable
  `bvid` becomes a `Recommendation`.
* Playback resolves `cid` with `/x/web-interface/view`, obtains public WBI key
  material from `/x/web-interface/nav`, then signs `/x/player/wbi/playurl`.
* The provider exposes only neutral `Recommendation` and Media3 `MediaSource`
  objects. UI code and Activities must not construct Bilibili URLs, parse DTOs,
  or sign requests.
* DASH video must select an AVC track and audio must select an MPEG-4 audio
  track. The DASH HTTP source includes the selected video page as `Referer` and
  a stable, non-credential user agent.
* No Cookie, SESSDATA, access key, CSRF value, device identifier, or server-side
  playback heartbeat is sent or logged.
* `ViriViriAppState` is the single process-level source for the list, selected
  item, browse/viewer destination, and sole `PlayerSession`.

### 4. Validation & Error Matrix

| Condition | Required behavior |
| --- | --- |
| Recommendation HTTP/API/JSON failure | Keep the browse UI alive and display a recoverable error. |
| Missing `bvid` or incomplete recommendation item | Skip the item without failing the full list. |
| Missing `cid`, WBI key, DASH object, AVC track, or MPEG-4 audio track | Enter viewer error state; do not replace the prior request's player source. |
| A slower prior selection completes after a newer selection | Ignore the stale completion. |
| 2D or immersive output changes | Capture position and play intent, then attach the target to the same player. |
| Old 2D Surface is destroyed after immersive attaches | Detach by identity only, then release the application-created Surface. |

### 5. Good / Base / Bad Cases

* Good: the app starts at recommendations, a selection updates the shared viewer
  state, and one player renders on the active 2D or immersive output.
* Base: an endpoint or codec is unavailable; the selected title remains visible
  with a user-readable error and the user can return to recommendations.
* Bad: an Activity creates another ExoPlayer, or a Composable requests a
  Bilibili endpoint directly.

### 6. Tests Required

* Unit test the WBI key permutation, invalid key data, sorted signing parameters,
  and forbidden-character removal.
* Unit test the recommendation endpoint contract so changes do not silently
  replace the verified web feed with another endpoint.
* Unit test stale selection completion and current-surface-only detachment when
  those state helpers are extracted or changed.
* Device test the initial recommendations, item selection, return to browse,
  both directions of 2D/immersive playback handoff, and retained playback
  position.

### 7. Wrong vs Correct

#### Wrong

```kotlin
@Composable
fun RecommendationRow(video: Recommendation) {
    Button(onClick = { URL(video.videoUrl).readText() }) { /* ... */ }
}
```

The UI now owns an unstable protocol, bypasses errors, and cannot coordinate the
shared player session.

#### Correct

```kotlin
Button(onClick = { appState.selectRecommendation(video) }) { /* ... */ }
```

`ViriViriAppState` delegates parsing to the provider and updates the shared
viewer state while the active host manages only its output Surface.
