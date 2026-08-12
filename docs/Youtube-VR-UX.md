# ViriViri VR UX Reconsideration

This document selectively uses the observed YouTube VR interaction model as a
reference, but defines a ViriViri-specific design. It borrows attention and
spatial-interaction principles, not a one-to-one feature set or input model. It
supersedes the early idea that the left browse panel, right context panel,
transport, and system toolbar should all be visibly present whenever a video is
playing.

## Product Principle

The user is here to watch a video, not to operate a floating desktop.

`MediaStage` is therefore a pure media surface. It owns the one video Surface,
subtitle layer, danmaku layers, and only short-lived playback feedback such as
play/pause, seek, and buffering. It never becomes a recommendation page, a
creator page, or a search result list.

The rest of the UI is a temporary or persistent **interaction canvas**. It is
spatially anchored around the stage, but a theme may keep selected panels
visible while others fade or appear on demand.

### Panel presentation is theme-controlled

Permanent panels are a first-class capability for second-party themes. The
YouTube-like cinema preset may fade auxiliary panels, while a cockpit preset
may keep status, transport, browse, and context panels visible together.

```kotlin
enum class PanelPresentationPolicy {
    PERSISTENT,       // always exposed by the theme
    AUTO_FADE,        // exposed, then faded after idle
    ON_DEMAND,        // opened by an explicit action
    TRANSIENT,        // popup, toast, action sheet
    THEME_CONTROLLED,
}
```

Canvas transitions may change the visibility of `AUTO_FADE`, `ON_DEMAND`, and
`TRANSIENT` panels, but must never hide a slot declared `PERSISTENT` by the
active theme. `WorkbenchCanvas` represents the current interaction focus; it
is not a global command to hide every panel.

### Transport is a front overlay

`TRANSPORT` is not spatially below the player. Its panel is placed in front of
`MEDIA_STAGE` on the Z axis, with a small positive depth offset toward the user.
The controls are laid out inside that panel as an overlay layer over the
projected video rectangle. Their eventual constraint/metric representation is a
renderer decision, not part of this UX contract.

```text
Spatial Z:       user -> TRANSPORT overlay -> MEDIA_STAGE -> scene
Panel layout:     renderer-defined constraints inside the overlay panel
```

The overlay uses state-dependent top placement:

```kotlin
// `top: 60%` and `top: 80%..90%` are communication shorthand only.
// The future renderer chooses its own metric/constraint representation.
```

- `top: 60%` is only a visual shorthand for the watch composition; it is not a
  required renderer coordinate system.
- `top: 80%..90%` similarly describes the visual relationship when a video list
  is present. The eventual renderer may use constraints, anchors, or another
  metric system to produce it.
- The list-state parent must preserve visible overflow and hit testing for the
  control overlay. The video list can scroll independently while transport
  remains fully operable.
- `GrabHandle` remains inside this overlay and is still the only workbench
  movement affordance. It does not make individual panels draggable.

## Interaction States

The user-facing model has cinema and Shorts presentation states. They are
presentation states, not new player, Surface, or Activity states.

### 1. Quiet Watch

```text
                         [ MediaStage ]
```

- Video, subtitle, and the user-selected danmaku mode remain visible.
- Panels declared `PERSISTENT` by the active theme remain visible. `AUTO_FADE`,
  `ON_DEMAND`, and `TRANSIENT` panels may be hidden and do not receive hit tests.
- A primary controller click, pinch, or direct touch on the stage opens the
  Playback Canvas. Any interaction with an already visible auxiliary panel
  resets its fade timer.
- Pausing keeps the Playback Canvas visible. Playing fades it after a short
  idle period unless the active theme declares the panel persistent.

### 2. Playback Canvas

```text
┌──────────────────────────────────────────────────────────────────┐
│       time       2D switch       environment          battery     │
│                                                                    │
│  [ content-navigation slot: video list ]  logo  profile / settings │
│                                                                    │
│                         [ current MediaStage ]                     │
│                                                                    │
│                                                                    │
│                              title                                 │
│   volume  previous  replay  play  seek forward  next  config       │
│           =========--------                         00:30 / 01:00  │
└──────────────────────────────────────────────────────────────────┘

                front TRANSPORT overlay + GrabHandle
```

- This is the default result of calling for controls while watching. The frame
  is a conceptual overlay composition, not a literal rectangular spatial panel.
- The first row is system status and environment control: time, 2D switch,
  environment, and battery. The second row has a reusable content-navigation
  slot plus fixed ViriViri logo and profile/settings actions.
- The content-navigation slot presents exactly one context-appropriate module:
  Home shows category tabs; Search shows the query field; a subordinate route
  shows Back; Playback shows source-aware `video list`. It is one physical
  navigation region, not a row of simultaneous buttons. Logo returns Home;
  profile/settings remains a separate fixed action.
- The central area remains MediaStage. The title and transport controls occupy
  the lower front overlay; they do not replace video with a detail page.
- The upper rows form one lightweight, curved, translucent command layer. They
  are not separate always-on dashboards.
- The lower-looking transport row is still a front overlay, not a spatially
  lower panel. The `60%` and `80%..90%` descriptions are visual CSS analogies,
  not a mandated internal coordinate system. List compositions must preserve
  visible overflow and interaction for the overlay.
- `TRANSPORT` uses the same front-of-stage Z relationship in every canvas state.
- The transport row contains no detail text, recommendation list, or account
  flow. `GrabHandle` is the only manipulator for the complete workbench:
  translate and yaw the whole workbench, never individual side panels.
- `config` opens a playback-display `Popup`, distinct from the Short content
  `more -> 举报` Popup. Its first menu contains: autoplay, playback speed,
  quality, curved screen, and screen size. Each item is a controlled setting:
  quality may be unavailable for the current source, and curved screen/screen
  size modify the existing MediaStage geometry/transform without creating a
  second player or video Surface.
- The config Popup does not permanently expand the canvas. Back or clicking an
  explicit Popup-dismiss region closes it before dismissing Playback Canvas.
- A deliberate click on a non-interactive part of the interaction canvas
  dismisses the current temporary canvas. The implementation must use the
  canvas hit region, not a special-case floor click. Persistent theme panels
  remain visible after dismissal.

### 3. Browse Canvas

This state is entered from Home, Search, or an explicit browse affordance. The
video continues to play behind it and remains the visual center.

```text
 [Browse rail]        [ MediaStage ]              [optional Up Next rail]

 fixed: Home | Search | Library
 scroll: recommendations, results, history, or suggestions
```

- `BROWSE` occupies the left rail only while the user is discovering content.
  It has a fixed header and tab/filter row, with exactly one scrollable body.
- `CONTEXT` may show a compact `Up Next` rail on the right, but is not opened
  merely because Browse is open. This avoids presenting two competing feeds.
- Selecting a video replaces the current media through the existing single
  player flow, closes Browse, and returns to Playback Canvas briefly so the
  user receives clear playback feedback.
- Playback selects `video list` for the shared content-navigation slot. When the
  current video was selected from search, this is a return to the previous
  **search results page**: restore its exact query, filters, result snapshot/page
  state, and scroll position so the user can continue discovery. It must not
  restart search or replace results with recommendations. The spatial host may
  preserve this within Browse Canvas rather than perform an Android route change,
  but the visible behavior is return-to-search-results. Other origins return to
  their matching list page when available, otherwise open Browse landing.
- Back closes the current browse subsection before returning to Quiet Watch.

### 4. Shorts Watch

Short video is a separate immersion pattern, not a portrait crop of the regular
watch canvas. It reuses the same `MEDIA_STAGE`, active player, active video
Surface, danmaku engine, and theme slot infrastructure, but it uses portrait
stage geometry and a short-video queue.

#### Shorts Quiet

```text
                         ┌───────────┐
                         │           │
                         │ portrait  │
                         │   video   │
                         │           │
                         └───────────┘

                   previous   like   feedback   next
```

- The portrait stage is the only large visual object; no browse, detail, or
  comment rail is permanently exposed unless the active theme declares it
  `PERSISTENT`.
- `previous` and `next` change the short-video queue item. `like` is a single
  explicit action subject to login state.
- The apparent `dislike` position is named `feedback`, not dislike. It maps
  only to a controlled not-interested action when supported, otherwise renders
  unavailable. Reporting is intentionally separate under `more -> 举报`; neither
  action may claim a successful server write that did not occur.
- A primary stage click, pinch, or touch opens Shorts Controls. Vertical swipe
  / controller navigation can be added only after queue ownership and accidental
  trigger behavior are validated on device.

#### Shorts Controls

```text
 ┌─────────────┐  ┌─────────────┐  ┌─────────────┐
 │    详情     │  │  主播放器   │  │   评论区    │
 │ title/stats │  │   portrait  │  │ thread list │
 │ creator     │  │   pause     │  │ composer    │
 │ description │  │ seek/status │  │             │
 └─────────────┘  └─────────────┘  └─────────────┘

       volume   previous   like   feedback   next   more
                 [front TRANSPORT overlay + GrabHandle]

                     more Popup: [ 举报 ]
```

- Details and comments are fixed left/right rails in Shorts Controls. They use
  the same semantic `CONTEXT` capability, but Shorts has a separate interaction
  focus so comment scrolling/input does not compete with normal-video
  playlist/related tabs. Their visibility has no ordered close relationship.
- The bottom command row remains a front `TRANSPORT` overlay: closer to the
  user than `MEDIA_STAGE`, never placed spatially below it. Its internal 2D
  placement is theme-configured for portrait bounds; it may overflow the side
  rail/content body without being clipped or losing hit testing.
- The `more` control is content-level overflow, not a general settings entry.
  Pointer focus or activation opens a compact `Popup`; its only current item is
  `举报`. Report submission is a separate, permission-aware flow and must show
  unavailable state until a supported Bilibili report contract exists.
- Playback preferences, danmaku style, and account settings do not belong in
  this Popup. They remain in their dedicated controlled surfaces.
- `GrabHandle` is still unique to this overlay and moves the whole workbench.
- Back closes the more Popup when it is open; otherwise it returns from Shorts
  Controls to Shorts Quiet. Fixed details/comments do not form a close stack.
  Back does not stop the active player unless an explicit playback action
  requests it.

### 5. Focus Workspace

```text
 [ FocusPanel: creator uploads / collection / series / channel ] [PiP dock]
```

- Only long, deliberate browsing tasks use `FOCUS`: all creator uploads,
  collections, playlists, series, channels, or future library views.
- `MediaStage` moves to the declared PiP dock. This is a transform/shape change
  to the same stage and the same active video Surface, never a second player.
- Focus content reserves the PiP exclusion region in its layout. A floating
  PiP must not cover arbitrary list rows or primary actions.
- Closing Focus returns the stage and the prior auxiliary panel state to their
  previous positions.

## Current Video Context

YouTube VR places much of the current-video metadata on a left detail panel.
For ViriViri, that material belongs to an on-demand right-side `CONTEXT` rail
because the left rail is the user's discovery route.

```text
Creator summary
Tabs: 选集 | 相关 | 详情
Video action bar: like | coin | favorite | share | more
```

- Multi-part videos open `选集`; single-part videos open `相关`.
- `详情` contains title, stats, date, description, tags, and a compact creator
  summary. It is not a permanently expanded page beside every video.
- Selecting creator avatar/name opens `CreatorDrawer` in the same right rail.
  Closing it restores the selected tab and scroll position.
- `查看全部投稿` is the deliberate escalation into Focus Workspace.
- Comment browsing, when implemented, is also a Drawer or Focus route, not a
  permanent fourth rail.
- Single-tap actions such as like may remain in the action bar. Multi-step or
  account-dependent actions such as coin, favorite-folder selection, and share
  use an `ActionSheet` and must clearly show unavailable/login-required state.

## Search UX

Search is an intentional workspace, not an inline field that expands and
pushes the rest of a browsing panel unpredictably.

```text
返回 | [ 搜索 / 已输入查询                 Clear ] | 语音 | 系统键盘

查询为空：平台默认推荐搜索 / 热搜
[ 热搜 1 ] [ 热搜 2 ] [ 热搜 3 ]

查询非空：内容联想下拉 select（最多四行，位于搜索框下方）
┌ 建议 1                                               ┐
├ 建议 2                                               ┤
├ 建议 3                                               ┤
└ 建议 4                                               ┘

历史搜索 / 匹配结果

┌──────────┐  ┌──────────────────────┐  ┌──────────┐
│  数字区  │  │  组合: nihao 词组|单字│  │   删除   │
│          │  │  输入法候选: [你好][你]│  │   语音   │
│          │  │  26 键拼音字母区      │  │   回车   │
│          │  │  Q W E R T Y ...      │  │ 收起键盘 │
│          │  │  [中/英] [分词符 ']   │  │          │
└──────────┘  └──────────────────────┘  └──────────┘
              application keyboard console, in front of transport
```

- Opening Search enters Browse Canvas with a fixed header. Back restores the
  previous browse tab or Playback Canvas. The query field shows the `搜索`
  placeholder while empty and exposes `Clear` only after text exists; Clear
  removes the committed query and active composition without leaving Search.
- The input board is a distinct lower console in front of the transport overlay.
  It is the topmost **application-owned spatial** input layer while open,
  including its expanded candidate list. A requested Horizon OS system IME may
  still take system-level focus/display above it. The console is driven by the
  same `SearchSession`, but does not own a panel entity, player, Activity, or
  network request.
- The target application-owned Chinese board is a 26-key QWERTY Pinyin layout,
  not the current multi-tap nine-key implementation. It has three stable zones:
  number keys, alphabet keys, and an action column for Delete, Voice, Enter,
  and keyboard dismissal. The alphabet zone also provides `中/英` and a Pinyin
  syllable delimiter (`'`). The existing nine-key board remains a compatibility
  implementation until the 26-key method and its candidate engine are built.
- Empty-query default recommendations are platform hot searches: the terms the
  web search box cycles through or exposes before the user starts a query. They
  are not derived from currently typed text and appear in the Search Canvas
  content region with history/results.
- Content suggestions are a separate provider/search-service result. They are
  requested from the normalized committed query, never raw unconverted Pinyin.
  Once the committed query has content, they appear directly below the query
  field as a selectable dropdown with at most four rows. They are neither hot
  searches nor input-method candidates.
- Hot searches, search discovery terms, content suggestions, and history entries
  share one `SelectSearchEntryAndSubmit` behavior, matching PiliPlus's
  `onClickKeyword()` path: write the selected term, clear local composition and
  provider suggestions, submit immediately, then dismiss the application input
  console and any visible system IME. Local Pinyin conversion candidates are
  excluded: they only partially commit their consumed composition range and keep
  the console open.
- Input-method candidates are local Pinyin conversion results, not platform hot
  searches or content suggestions. Composition, `词组 | 单字`, and a fixed-height,
  one-row collapsed candidate strip sit immediately above the 26-key alphabet
  zone inside the application keyboard console. The strip ends with an expand
  button when more candidates exist than fit on the first row; expansion opens
  a list anchored above the strip. Candidate availability must never move the
  alphabet keys while a user is entering text. Phrase is the default and appears
  before single characters.
- In Chinese Pinyin mode, `'` is composition-only. It explicitly separates
  adjacent syllables such as `xi'an` and `chang'an`, guides segmentation, and is
  omitted when a Chinese candidate is committed. Every candidate declares the
  raw composition span it consumes: selecting a single character or phrase
  commits only that span, retains unconsumed Pinyin, and refreshes candidates
  for the remainder. Leading, trailing, or repeated delimiters must be ignored
  or cleaned up deterministically and must not create empty candidate tokens.
- `中/英` switches the active application input method within the same session.
  Switching Chinese to English preserves committed query text and clears only
  uncommitted Pinyin composition. English mode writes literal Latin text to the
  committed query; phase one has no English prediction or candidate strip.
  Switching back starts a fresh Pinyin composition after the committed query.
- Voice has two equivalent locations: the header exposes `语音` followed
  immediately by the `呼出系统输入法` icon, and the keyboard action column exposes
  a second `语音` key. Both dispatch the same controlled `RequestVoiceInput`
  action and share availability, requesting, partial-result, final-result,
  cancellation, and failure state; they are not two recording flows. Until a
  supported headset speech contract exists both display the same unavailable
  state. The system-IME icon requests Horizon OS IME without leaving Search
  Canvas.
- System-IME finalized text and voice final text update the same committed
  query. If local Pinyin composition is still unconfirmed when either arrives,
  first append that composition **verbatim** to the query, then append the final
  system/voice text, and clear local composition/candidates. This prevents data
  loss without pretending that a remote source can safely convert local Pinyin.
  The application keyboard otherwise follows its active method: Chinese Pinyin
  keys update only composition until a candidate partially commits its consumed
  span; English keys write literal Latin text directly to the committed query.
  Selecting an input source is not a mode switch and must not discard valid text
  from another source. System IME remains important for installed languages,
  handwriting, and future headset-native capabilities.
- Application-keyboard Enter is two-stage in Chinese mode: while composition is
  nonempty, the first Enter confirms the current preferred candidate only and
  keeps Search Canvas open; after composition is empty, the next Enter submits
  the committed query. English mode has no composition, so Enter submits
  directly. System IME uses its own explicit confirmation button to submit.
  Text changes, voice partial results, and system-IME dismissal never submit
  automatically; dismissal returns to the same Search Canvas with query intact.

### Input Console Is Theme-Stylable

The application-owned IME is not visually fixed to the default cinema console.
Input-method behavior stays inside the input engine: active method/language,
raw composition, segmentation, candidate ranking, partial candidate commit,
Enter behavior, and the shared `SearchSession` update rules. A theme controls
only visual presentation and declarative module arrangement.

```kotlin
data class InputConsoleStyle(
    val shell: SurfaceStyle,
    val numberKeyStyle: KeyStyle,
    val alphabetKeyStyle: KeyStyle,
    val actionKeyStyle: KeyStyle,
    val selectedLanguageStyle: SelectionStyle,
    val compositionStyle: TextStyleToken,
    val candidateStripStyle: CandidateStripStyle,
    val candidatePopupStyle: PopupStyle,
    val disabledStyle: DisabledStyle,
)
```

A theme may replace colors, typography, opacity, materials, borders, press/
hover/focus/disabled treatment, spacing, key grouping, candidate-list treatment,
and the visual arrangement of the semantic modules below:

```text
SearchNumberKeypad
SearchPinyinQwertyBoard
SearchCandidateStrip
SearchCandidatePopupList
SearchInputActions
```

It may render them as a cinema overlay, a cockpit keypad, a compact sidebar, or
another theme-specific composition. It must preserve the semantic contracts:
fixed keyboard geometry during typing, candidate strip immediately above the
alphabet region when that region is present, one shared voice flow, one shared
SearchSession, and no player/Surface/Activity ownership. Theme data is values
and whitelisted module arrangement, never arbitrary Compose/Kotlin code.

## Deferred Account Communication

Comments, replies, and direct messages are future product modules, not theme
features. PiliPlus has reference implementations for comment threads, nested
reply composers, direct-message sessions/detail, block lists, and IM settings.
ViriViri should later expose controlled `CommentThread`, `CommentComposer`,
`ReplyComposer`, `DirectMessageSessionList`, `DirectMessageThread`, and
`DirectMessageComposer` modules.

Before enabling a write action, login/session/CSRF and the target service
contract must be implemented and verified. Each composer needs disabled,
login-required, draft, sending, success, and failure states; sending must guard
against duplicate requests, preserve text on failure where appropriate, and
update local content only after success. The application IME can be reused as an
input source, but comment/message composition must have its own session and
never share search history or `SearchSession`.

Until then, comment display may be read-only and reply/DM controls must show an
explicit unavailable or login-required state rather than a simulated result.

## Default Theme Component Group

The Playback Canvas described above is the **default cinema theme recipe**, not
a mandatory product-wide screen. The default theme may expose it as one
convenience group:

```kotlin
DefaultCinemaPlaybackCanvasGroup(
    systemStatus = SystemStatusStrip,
    contentNavigation = ContentNavigationSlot,
    stage = MediaStage,
    title = WatchTitle,
    transportActions = TransportActionStrip,
    timeline = SeekTimeline,
    configPopup = PlaybackConfigPopup,
    grabHandle = GrabHandle,
)
```

The group exists to make the default YouTube-VR-like theme coherent and easy to
apply. It is not a new `PanelSlot`, does not own a player, video Surface,
Activity, route, or static spatial entity, and must not become the only renderer
for these capabilities.

Every member remains individually public and theme-composable:

| Atomic component | Default-group role | What a custom theme may do |
| --- | --- | --- |
| `SystemStatusStrip` | Time, 2D, environment, battery | Keep it, split its modules, relocate it, or omit allowed modules |
| `ContentNavigationSlot` | Tabs/search/back/video-list region | Put it in a side rail, make it persistent, replace its shell, or render one member directly |
| `MediaStage` | Current video, subtitle, danmaku | Use another stage shape/placement while preserving the one-player/one-Surface contract |
| `WatchTitle` | Current video title above transport | Move to context, collapse it, or use a custom title treatment |
| `TransportActionStrip` | Volume through config controls | Split controls across panels or use a different action arrangement |
| `SeekTimeline` | Progress and timecode | Move, replace visual treatment, or omit only where the theme offers an equivalent seek action |
| `PlaybackConfigPopup` | Playback/display settings | Replace its shell/menu composition while reusing controlled settings actions |
| `GrabHandle` | Whole-workbench manipulation | Reposition within transport only; it remains the sole workbench manipulator |

A custom theme can therefore choose any of these patterns without duplicating
business behavior:

```text
Cinema:    DefaultCinemaPlaybackCanvasGroup
Cockpit:   SystemStatusStrip + persistent TransportActionStrip + custom side navigation
Minimal:   MediaStage + TransportActionStrip + GrabHandle
Custom:    independent atomic components in theme-defined slots
```

Group-level state is only a convenience mapping to atomic component state. For
example, opening default `config` opens `PlaybackConfigPopup`; a custom theme
may expose the same playback actions through an ActionSheet or persistent
settings module. No theme may bypass the controlled actions or create another
MediaStage output.

### Default Cinema Palette

The default cinema theme exposes a user-customizable semantic palette. It is a
shared source for `DefaultCinemaPlaybackCanvasGroup`, `CinemaInputConsole`,
Browse, Context, Popup, and focus/disabled states, so a palette change remains
visually coherent across the whole workbench.

```kotlin
data class CinemaPalette(
    val background: ColorToken,
    val surface: ColorToken,
    val surfaceOverlay: ColorToken,
    val surfaceOpacity: Float,
    val border: ColorToken,
    val text: ColorToken,
    val textSecondary: ColorToken,
    val textHighlight: ColorToken,
    val button: ColorToken,
    val buttonText: ColorToken,
    val buttonSecondary: ColorToken,
    val buttonSecondaryText: ColorToken,
    val danger: ColorToken,
)

data class CinemaPaletteOverride(
    val appearance: CinemaAppearance? = null, // DARK, LIGHT, HIGH_CONTRAST
    val background: ColorToken? = null,
    val surface: ColorToken? = null,
    val surfaceOpacity: Float? = null,
    val text: ColorToken? = null,
    val textSecondary: ColorToken? = null,
    val textHighlight: ColorToken? = null,
    val button: ColorToken? = null,
    val buttonText: ColorToken? = null,
    val buttonSecondary: ColorToken? = null,
    val buttonSecondaryText: ColorToken? = null,
    val border: ColorToken? = null,
    val danger: ColorToken? = null,
)
```

The theme ships validated `DARK`, `LIGHT`, and `HIGH_CONTRAST` presets. Users
may override the named semantic roles above, rather than individual component
colors. The runtime derives hover, pressed, focus, disabled, and readable
foreground values from those roles. Custom themes may provide their own
semantic palette, but use the same component roles.

Palette validation must enforce legible text/background and button-label/button
contrast, an allowed surface-opacity range, and distinct normal/focus/disabled
states. A palette may
change the appearance of danmaku controls, but not danmaku content styling;
that remains a separate user preference and renderer contract.

### Extensible Media Overlay Pipeline

Danmaku and captions share the rendering substrate, not the content pipeline:

```text
MediaOverlayEngine
├── PlayerClock
├── OverlaySurfaceRegistry
├── FlatOverlayRenderer
├── SpatialOverlayRenderer
├── DanmakuPipeline
│   ├── DanmakuSource
│   ├── DanmakuTransformPlugin[]
│   ├── DanmakuScheduler
│   └── DanmakuGroupAllocator
└── CaptionPipeline
    ├── CaptionSource
    ├── CaptionTransformPlugin[]
    ├── CaptionScheduler
    └── BilingualCaptionComposer
```

`OverlaySurface` is a renderer target, never a video output Surface. It declares
an ID, enabled state, supported overlay kinds, capacity, anchor mode, depth, and
`basicStyle`. The registry, player clock, Flat/Spatial renderer lifecycle,
object-pool budget, pause/seek cleanup, and stage geometry are shared. Sources,
transforms, schedulers, allocation policy, and content semantics remain
independent.

```kotlin
enum class OverlayKind { DANMAKU, CAPTION }
enum class OverlayAnchorMode { STAGE_LOCKED, GAZE_LOCKED }
```

`STAGE_LOCKED` follows MediaStage transform, size, and cylinder geometry.
`GAZE_LOCKED` follows a stable viewer-facing anchor and is primarily for CC
captions when the stage moves or shrinks. A caption selects one target; it does
not enter the danmaku allocator.

### Multi-Surface Spatial Danmaku

A Spatial danmaku surface is not an independent scheduler. Multiple parallel
surfaces are grouped into a `DanmakuSurfaceGroup` that shares normalized
projected coordinates and desktop-style lane occupancy. It does not share
physical meter coordinates; each Meta adapter maps a layer's local pose to world
space.

```text
DanmakuOcclusionDomain
├── cockpit-left-group  -> layers L1..L4
└── cockpit-right-group -> layers R1..R4
```

Cockpit groups first balance events by enabled capacity, active load, lane ratio,
allocation weight, and stable event hash. Within the selected group, the
scheduler chooses a standard scrolling lane, then a depth layer, then a physical
surface. Same-group layers share lane state; different directions use separate
`DanmakuLaneSet`s.

Group-level balancing does not by itself solve side-surface overlap. A shared
`DanmakuOcclusionDomain` projects candidate and active items through a reference
viewer/head pose at entry, closest approach, and exit times. This catches L1/L4
end-to-end overlap that is invisible in either surface's local normal view. A
small head-motion tolerance margin is applied; visible items are not reassigned
on every head frame. Large viewer relocation, seek, stage switch, or theme
switch may clear and reschedule active items.

The default policy is `AVOID_PROJECTED_OVERLAP`. Try another lane or layer when a
projection conflicts; drop the event when all lanes are unavailable rather than
queueing unreadable text. Left/right groups may skip cross-group checks only when
the theme declares their viewer-projected regions disjoint. Otherwise both groups
belong to the same occlusion domain.

### Direction And Surface Style

Emission direction and internal text layout are separate:

```kotlin
enum class DanmakuEmissionDirection {
    LEFT_TO_RIGHT, RIGHT_TO_LEFT, TOP_TO_BOTTOM, BOTTOM_TO_TOP,
}

enum class TextWritingMode { HORIZONTAL_TB, VERTICAL_RL, VERTICAL_LR }

enum class TextDirection { AUTO, LTR, RTL }
```

Direction is relative to the surface local tangent/axis and is mapped by the
renderer; sources never write world coordinates. Panel/surface style may define
`fontScale`, opacity, speed scale, outline, `writingMode`, bidi direction, max
lines, line spacing, and overflow. Far depth layers may use larger glyphs and
near layers smaller glyphs to preserve apparent size.

The resolved style participates in glyph measurement before lane and occlusion
prediction. Each scheduled item stores its target, direction, measured bounds,
and style snapshot. A live user/theme style change affects future items only;
existing items do not reverse, jump layers, or switch writing mode.

### Captions, Bilingual CC, And Translation

CC is a separate timed cue pipeline sharing the overlay renderer:

```kotlin
data class CaptionCue(
    val id: String,
    val startMs: Long,
    val endMs: Long,
    val originalText: String,
    val translatedText: String? = null,
)

enum class CaptionDisplayMode { ORIGINAL_ONLY, TRANSLATED_ONLY, BILINGUAL }
```

Bilingual output is one cue with original and translated lines under one
schedule. Missing or failed translation always falls back to the original.
Caption display exposes `STAGE_LOCKED` and `GAZE_LOCKED` target selection, plus
language, font, line count, background, and safe-area preferences. It never uses
random depth allocation intended for danmaku.

Optional LLM translation is a cancellable `CaptionTransformPlugin`, not a theme
action or renderer feature. Only cue text and required language metadata may be
sent. Provider/model/target-language settings, bounded concurrency, timeout,
seek cancellation, and cache key `cueId + sourceTextHash + targetLanguage +
provider + model` are required. Keys remain in secure platform storage and never
enter theme JSON, logs, Bilibili headers, or player metadata. Translation failure,
offline mode, rate limits, or missing credentials never block the original cue.

### Overlay Implementation Stages

1. **Contracts**: define shared overlay kinds, surface registry, clock lifecycle,
   danmaku groups/layers/occlusion domain, emission/text layout, caption cues,
   plugins, and privacy-safe translation settings.
2. **Flat renderer**: implement mock danmaku and CC sources, stable geometry,
   pause/seek/disable cleanup, bilingual layout, and one caption target.
3. **Real sources**: add Bilibili XML/deflate and optional segmented Proto
   adapters, CC/VTT sources, `DuplicateMergePlugin`, bounded caches, and a mock
   translator. Protocol details stay outside scheduler/renderer.
4. **2D integration**: overlay Flat rendering over the existing TextureView
   without a second video Surface; connect current video identity/cid and player
   clock while retaining mock-source regression mode.
5. **Spatial adapter**: bind surface IDs to Meta entities, implement parallel
   depth layers, group balancing, viewer projection occlusion, local emission
   direction, and surface style compensation for cockpit and cinema themes.
6. **Gaze/LLM**: add gaze/stage caption anchors, secure optional LLM provider,
   translation cache/prefetch/cancellation, and Quest regression.

## Spatial Behavior Rules

1. Only `GrabHandle` moves the workbench. No panel exposes its own grab region.
2. A hidden panel has both alpha zero and disabled visibility/hit testing.
3. Canvas dismissal is predictable: interact with a known non-action canvas
   area or use Back. The floor is never the sole dismissal target.
4. `SYSTEM_TOOLBAR`, `BROWSE`, and `CONTEXT` are semantic slots, not a promise
   that every slot is visible. The theme determines their placement; UX state
   determines their exposure.
5. Temporary UI never creates a player, Surface, Activity, or static spatial
   entity. Fixed anchors and decorative scene elements remain authored in Meta
   Spatial Editor.
6. Danmaku is independent from transport. Transport changes its mode and style;
   `DanmakuEngine` owns sourcing, scheduling, and rendering. Flat danmaku and
   subtitles follow the same flat or cylinder `StageGeometry` as video.

## Implementation Order

1. Keep the current five slot registrations but add a single interaction-canvas
   state machine and unified fade/hit-test control.
2. Make Playback Canvas the default cinema auxiliary presentation; do not expose
   Browse and Context until their explicit entry actions are selected.
3. Add the distinct Shorts quiet/control canvases, portrait stage geometry,
   short-video queue contract, feedback-unavailable state, and comments rail.
4. Rebuild Browse as a fixed-header, single-scroll-region rail and place search
   in its focused workspace form.
5. Add Context tabs, CreatorDrawer, ActionSheet, and Focus/PiP in that order.
6. Validate cinema and Shorts stage input, canvas dismissal, fade, grab
   behavior, portrait/full-frame mapping, queue changes, PiP, and the
   no-second-Surface invariant on Quest before treating the new UX as complete.
