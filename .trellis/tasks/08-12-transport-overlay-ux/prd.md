# Transport Overlay UX Correction

## Goal

Correct the UX documentation so playback controls are modeled as a front-of-stage
overlay rather than a panel below the video, while preserving persistent-panel
support for second-party themes.

## Requirements

- Define `TRANSPORT` as a panel overlay in front of `MEDIA_STAGE` on the spatial
  Z axis, sharing the stage's projected bounds.
- Describe transport placement as a front overlay whose renderer constraints are
  intentionally deferred. Use `top = 0.60` for video watch and
  `top = 0.80..0.90` for video-list only as visual CSS analogies, not theme
  schema fields or required 2D coordinates.
- State explicitly that the list-state parent must allow visible overflow and
  preserve hit testing; controls must not be clipped when the overlay extends
  below the video list content region.
- Keep `GrabHandle` inside the transport overlay while it remains the only
  workbench movement affordance.
- Add a theme-level panel presentation policy so `PERSISTENT` panels are not
  hidden by the default Quiet Watch/canvas fade state. Auto-fade and on-demand
  panels remain available.
- Distinguish spatial Z ordering from internal Compose/2D layout coordinates.
- Define a distinct `SHORTS` layout mode for Bilibili short-video viewing. It
  must reuse the same MediaStage/player/Surface contracts but use portrait
  stage geometry, a short-video queue, and independent quiet/control canvases.
- In short quiet mode show only the compact `previous`, `like`, `feedback`, and
  `next` actions around the portrait stage. Bilibili does not provide a general
  dislike action, so the UX must model that position as a controlled
  not-interested/feedback action or an explicit disabled state.
- Package the default YouTube-VR-like playback canvas as a default-theme
  `DefaultCinemaPlaybackCanvasGroup`, not a global mandatory screen or new slot.
  The group is a convenience recipe for SystemStatusStrip,
  ContentNavigationSlot, MediaStage, WatchTitle, TransportActionStrip,
  SeekTimeline, PlaybackConfigPopup, and GrabHandle.
- Keep every group member independently addressable and renderable so custom
  themes can split, relocate, replace, omit where valid, or recombine them
  without duplicating player/Surface/business ownership.
- Define a shared top `ContentNavigationSlot`: it mutually renders Home category
  tabs, SearchQueryField, BackNavigation, or source-aware OpenVideoList based
  on route; OpenVideoList occupies this region during playback.
- In regular Playback Canvas, define system/status row, the mutually exclusive
  ContentNavigationSlot, MediaStage, and a front transport overlay containing
  title, volume, previous, replay, play, seek forward, next, config, and
  progress/time.
- Define playback `config` as a Popup containing autoplay, speed, quality,
  curved screen, and screen size. It must be distinct from Short `more -> 举报`,
  and curved/size changes must operate on the existing MediaStage only.
- Define OpenVideoList as source-aware continuation. For a video selected from
  search, user-visible behavior is return to the prior search-results page:
  restore that same query, filters, result snapshot/page state, and scroll
  position rather than re-running search or replacing it with recommendations.
  The spatial host may restore this inside Browse Canvas rather than navigate an
  Android route.
- In short controls mode show a three-column overlay: details, portrait stage
  controls, and comments; place volume, previous, like, feedback, next, and a
  content-level `more` entry in the front transport overlay. Its Popup contains
  only `举报`; it is not a general settings menu.
- Define Search Canvas with a fixed header: Back, query placeholder/Clear,
  `RequestVoiceInput`, and `RequestSystemIme` in that order; the system-IME
  icon sits immediately to the right of voice. Add a second Voice key to the
  application keyboard action column; both locations dispatch the same
  RequestVoiceInput flow and share availability/result lifecycle.
- Make a 26-key QWERTY Pinyin board the target application-owned Chinese input
  UX: numeric zone, alphabet zone, and Delete/Voice/Enter/Dismiss action column
  in a separate front-of-transport console. Input-method composition/candidates
  must occupy a fixed-height strip directly above the alphabet zone. In Chinese
  mode Enter confirms the preferred candidate when composition is nonempty;
  only the next Enter after composition clears submits search. Treat the current
  multi-tap nine-key board as a compatibility implementation until the
  replacement is built.
- Keep three search-data surfaces separate: empty-query platform default hot
  searches belong in the searchable content region; query-driven content
  suggestions render as a maximum-four-row select directly below the query
  field; local input-method candidates are conversion results positioned above
  the alphabet keys. Selecting a hot search or content suggestion writes its
  query, immediately submits search, and dismisses the application keyboard
  console and any active system IME. Search-history and search-discovery
  selection follow the same behavior, matching PiliPlus `onClickKeyword()`.
  Provider content suggestions are derived from normalized committed query,
  never raw Chinese Pinyin composition.
- The target QWERTY board includes `中/英` input-language switching and a Pinyin
  syllable delimiter (`'`). Delimiters guide Chinese segmentation but are not
  committed with converted Hanzi. Language switching preserves committed text,
  clears only uncommitted composition, and makes English literal-only without
  first-phase word prediction or candidates.
- Make the application-owned input console theme-stylable. The theme may replace
  shell/key/candidate/popup typography, materials, palette, state treatment,
  spacing, and semantic-module arrangement through non-executable style tokens;
  input engine behavior, SearchSession updates, partial candidate consumption,
  voice/system-IME bridge, and player/Surface/Activity ownership remain outside
  theme control.
- Define a semantic, user-customizable default `CinemaPalette` shared by the
  cinema playback group, input console, Browse, Context, Popup, and component
  state treatments. Ship validated dark/light/high-contrast presets and permit
  user selection for named semantic roles: background, surface, normal/secondary/
  highlight text, primary/secondary button plus label, border, danger, and
  surface opacity. Derive interaction states with contrast/state-distinction
  validation; do not expose arbitrary per-component colors.
- Preserve `呼出系统输入法` as a first-class SearchQueryField action. It invokes
  the Horizon OS system IME while sharing the same committed query with the
  application-owned board. When system-IME or voice final text arrives during
  unconfirmed local Pinyin composition, append that composition verbatim, append
  final text, then clear composition/candidates. System IME, voice final text,
  and board input may not auto-submit search; unsupported voice must display
  unavailable state.
- Record Comments/Replies/Direct Messages as deferred account-capability TODOs
  based on PiliPlus reference implementations. They require verified login,
  session/CSRF, endpoint/protocol, draft, disabled, sending, success/failure,
  and duplicate-send contracts before enabling writes; themes must not own the
  network behavior.
- Do not change application code in this documentation task.

## Acceptance Criteria

- Both UX and architecture documents describe the same transport overlay model.
- Both documents support persistent panel themes such as a cockpit.
- Both documents specify the distinct Shorts quiet/control/comment canvases.
- No wording says `TRANSPORT` is spatially below the player.
- The UX document describes overflow-visible list mode and visual top-offset
  analogies without requiring renderer coordinates.
- Both documents define ContentNavigationSlot reuse, source-aware OpenVideoList,
  and the playback config Popup contract.
- Both documents define DefaultCinemaPlaybackCanvasGroup as a default-theme
  recipe whose atomic members remain independently composable for custom themes.
- The architecture document identifies `SHORTS` as a required but not-yet-coded
  core contract change, so documentation does not imply present runtime support.
- Shorts defines fixed details/comments rails, `feedback` as not-interested only,
  and `more -> 举报` as the sole report route.
- Chinese candidate selection consumes only the candidate's declared composition
  range, preserves the remaining Pinyin, and uses a one-row collapsed strip with
  a trailing expand control and topmost anchored candidate list.
