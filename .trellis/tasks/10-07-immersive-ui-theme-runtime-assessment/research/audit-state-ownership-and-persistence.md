# Audit — State Ownership, Duplicate State Mapping, Panel/Stage Persistence

Scope: `D:\Workspace\unity\viriviri` (Android / Meta Spatial SDK VR app).
Excluded: `D:\Workspace\unity\viriviri\temp\` (vendored third-party samples).
Method: source reading only. **No source file was modified.** This is the only file written.
Date of reading: current working tree; no commit pinned.

---

## Verdict

The immersive UI is driven by **at least four layered state holders that are not a single source of
truth**: the core contract layer (`PlaybackCanvasState` + `SpatialTheme`), the app product layer
(`ImmersiveWorkbenchState`), the app content layer (`ViriViriUiState.searchWorkspace.route`), and the
per-activity ephemeral layer (a dozen `private var` mirrors inside `SpatialVideoSampleActivity` plus
Android `View.visibility`/`Animator` and raw ECS components). The only formally wired chain is
`PlaybackCanvas → PanelSlot set → WorkbenchEvent → ImmersiveWorkbenchState → WorkbenchModule set →
PanelSlot set → Spatial Visible/alpha`, i.e. `PanelSlot` is used as both the input key and the output
key of the same pipeline and `WorkbenchModule` is a lossy re-encode of the `WorkbenchContent` it was
derived from. `ImmersivePlaybackCanvasHost` is **live, not dead code** — it is instantiated at
`SpatialVideoSampleActivity.kt:372-373` and its output really does move Spatial entities via
`ImmersiveWorkbenchHost.applyCanvasSlots` → `applyWorkbenchModules` →
`SpatialPanelVisibilityController.setVisible`. Persistence is **much thinner than the UI suggests**:
only **six** SharedPreferences keys exist (`AppPreferences.kt:92-100`), of which **five are spatial
geometry**; curvature is stored in **three redundant keys that are always written with the same
value** (`ViriViriAppState.kt:869-871`, acknowledged in `docs/research/stage-video-mesh-curvature.md:611-619`),
and there is **no settings UI for spatial layout at all** — the settings icon in the global nav is
literally `enabled = false` and labelled "暂不可用" (not yet available) at `MRPanel.kt:76-78`.

---

## (a) State holders that can affect what is visible on screen

| # | Holder | Type | Declared at | Owner / writer | Readers |
|---|---|---|---|---|---|
| 1 | `PlaybackCanvasState` (field `ImmersivePlaybackCanvasHost.state`) | `data class` (`PlaybackCanvas` + `Boolean`) | `ImmersivePlaybackCanvasHost.kt:15`; type `PlaybackCanvasContracts.kt:11-14` | `ImmersivePlaybackCanvasHost.dispatch` `ImmersivePlaybackCanvasHost.kt:19-22`; app entry `SpatialVideoSampleActivity.dispatchPlaybackCanvas` `:2012-2014` | `PlaybackCanvasReducer.visibleSlots` `PlaybackCanvasContracts.kt:71-87`; `ImmersiveBrowseSessionReducer.onAppState` `SpatialVideoSampleActivity.kt:421`; `openPlaybackCanvasIfQuiet` `:1906-1911`; `syncPlaybackControls` `:2824`; diagnostic log `:1266` |
| 2 | `ImmersiveWorkbenchState` (field `ImmersiveWorkbenchHost.state`) | `data class` | `ImmersiveWorkbenchState.kt:4-9` | `ImmersiveWorkbenchHost.dispatch` `ImmersiveWorkbenchHost.kt:14-25`; only writer in app = `applyCanvasSlots` `:27-37` | `ImmersiveWorkbenchReducer.modules` `ImmersiveWorkbenchState.kt:73-97` → `applyWorkbenchModules` `SpatialVideoSampleActivity.kt:2108`; `onStagePrimaryAction` `:2000`; `isStageInputEnabled` `:1928`; `attachOuterDismissInput` `:658`; `GrabBarHoverSystem` predicate `:542-544`; `createStageBackdropPanel` `:1521` |
| 3 | `SpatialTheme` (`CinemaTheme.create()`) | `data class`, ctor default | `ImmersivePlaybackCanvasHost.kt:12`; value `CinemaTheme.kt:4-115` | nobody at runtime — never reassigned | `PlaybackCanvasReducer.visibleSlots` reads `theme.presentationPolicies` `PlaybackCanvasContracts.kt:82-85` |
| 4 | `ViriViriUiState` | `data class` in `MutableStateFlow` | `ViriViriAppState.kt:93-117`, flow `:235-249`, exposed `:292` | `ViriViriAppState` mutator methods only | `SpatialVideoSampleActivity` observer `:376-429`; `RecommendationUi` / `SearchPanel` Compose; `DanmakuOverlay.kt:38`; `ImmersiveInputMethodPanel.kt:23`; `PancakeActivity.kt:50` |
| 5 | `SearchWorkspaceState` (nested in #4) | `data class` | `ViriViriAppState.kt:78-91` | route writers `:378-466, :603-605, :758-771, :773-799, :422-435` | `RecommendationUi.kt:212` (`val route = state.searchWorkspace.route`), `:271, :346, :401`; keyboard gate `SpatialVideoSampleActivity.kt:1496-1498`; `ImmersiveInputMethodPanel.kt:23` |
| 6 | `ImmersiveBrowseSession` | `data class`, Activity field | `ImmersiveBrowseSession.kt:5-8`; field `SpatialVideoSampleActivity.kt:268` | `ImmersiveBrowseSessionReducer` via `:419-426, :434, :2037, :2046, :2055, :2062, :2079` | same sites (feeds `PlaybackCanvasEvent.OpenPlayback`, and `PlaybackCanvas` feeds the reducer back at `:421`) |
| 7 | `MediaStageState` (field `ImmersiveMediaStageHost.state`) | core `data class` | `ImmersiveMediaStageHost.kt:22-34` | private `dispatch` `:70-79`; fed by `attachOutput*` `:36-55`, `updateClock` `:57-59`, `reportSeek` `:61-63` | only its own reducer + `onEffect` callback `:65` (log only) — **no panel visibility reader** |
| 8 | `ImmersiveTransportOverlayState` | `data class`, Activity field | `ImmersiveTransportOverlayPolicy.kt:7-9`; field `SpatialVideoSampleActivity.kt:151` | `animateControllerVisibility` `:1872`; `applyTransportOverlayVisibility` `:1894` | `:1380, :1884, :1916` |
| 9 | `controllerView.visibility` + `alphaAnimator` | Android `View` + `ObjectAnimator` | `:150, :258, :1869-1899` | Activity (`animateControllerVisibility`) | the compositor — a second, non-data representation of "transport visible" |
| 10 | ECS components `Visible`, `PanelLayerAlpha`, `Transform`, `TransformParent` | Meta Spatial components | `SpatialPanelVisibilityController.kt:28-47`; `GrabBarHoverSystem.kt:117`; `WristAttachedSystem.kt:53-54`; `SpatialVideoSampleActivity.kt:2660-2673` | three independent writers (see Q2) | the compositor / hit-test |
| 11 | `appliedStageScale`, `appliedVideoCurvature`, `appliedDanmakuCurvature`, `appliedBackdropCurvature` | `Float?`, `PlaybackStageCurvature` ×3 | `SpatialVideoSampleActivity.kt:221, :226-228` | `applyPlaybackStageScale` `:2213-2223`, `applyPlaybackVideoCurvature` `:2242-2255`, `applyPlaybackDanmakuCurvature` `:2258-2262`, `applyPlaybackBackdropCurvature` `:2265-2269` | `scaledStageWidth/Height` `:752-754`, `createVideoSceneMesh` `:819`, `overlayShapeOptions` `:959-968`, `applyOverlayStageShape` `:2236-2239` — a process-local mirror of #4 |
| 12 | `grabBarContentHalfHeight` | `Float` | `:216` | `updateGrabBarPose` `:990-1000` | `currentBarOffsetY` `:730-735` → anchor Y and stage-local Y `:1139, :1047` |
| 13 | `danmakuBaseLocalZIndex` | `Int` | `:189` | debug button `:1739-1751` (debug rail) | `stageDanmakuBaseLocalZ` `:186-187` |
| 14 | `spatialVideoAspectProbeState` | `data class` | `SpatialVideoAspectProbeMode.kt:28-33`; field `SpatialVideoSampleActivity.kt:205` | debug menus `:2448, :2465, :2474`; also `applyPlaybackDisplayRatio` `:2335-2358` | `updateSpatialVideoContentQuad` `:2707-2709`; UI `:2489-2504` |
| 15 | `isMrMode` (MRPanel) / `inMrMode` (Activity) / `scene.isSystemPassthroughEnabled()` / `MRPanel` intent extra | `Boolean` ×4 | `MRPanel.kt:43`; `SpatialVideoSampleActivity.kt:263-264`; `:2863` | MRPanel `Switch` `MRPanel.kt:79-82`; Activity `setMrMode` `:2876-2899` | environment/skydome visibility `:2881-2887`; controller visibility `:592-593, :2896-2897`; Switch thumb `MRPanel.kt:79` |
| 16 | `hasWorkbenchDataSource` | `Boolean` | `:220` | observer `:379-385` (mirror of `appState.selected != null`) | `shouldShowWorkbenchModule` `ImmersiveWorkbenchState.kt:45-51` via `:2122` |
| 17 | `ImmersivePlaybackControlState`, `isPlaying`, `isSeeking`, `seekDragPositionMs`, `SeekDragPlaybackPolicy` | derived + transient | `ImmersivePlaybackControlState.kt:3-27`; `SpatialVideoSampleActivity.kt:249-251` | `syncPlaybackControls` `:2807-2826`; seek listeners `:1217-1250` | play/pause icon `:2810-2817`; `dimLights/brightenLights` `:2818-2825` |
| 18 | `ImmersiveTransportTimeline` | derived `data class` | `ImmersiveTransportTimeline.kt:3-9` | `syncTransportTimeline` `:2775-2789` | seek bar / time labels |
| 19 | Compose-local UI state: `isGridView`, `filterState`, `moreFiltersExpanded`; `commentsOpen` | `remember` / `rememberSaveable` | `RecommendationUi.kt:213-215`; `ImmersiveLeftPanel.kt:63` | Compose | center panel body, left rail |
| 20 | `DanmakuCanvasRuntime` + `DanmakuRenderConfig` | runtime object | `DanmakuOverlay.kt:49-52`; `DanmakuRenderConfig.kt:5` | `remember(selectedVideoId)` | `DanmakuOverlay` Canvas `:62-96` |
| 21 | `SpatialPanelVisibilityController.pendingAnimations` | `Map<Entity, Runnable>` | `SpatialPanelVisibilityController.kt:18` | `setVisible` `:20-55` | fade completion `:39-51` |
| 22 | `pendingAnimations` sibling: grab-bar alpha owner in `GrabBarHoverSystem` | `displayedAlpha` / `targetAlpha` | `GrabBarHoverSystem.kt:43-44` | `animateTo` `:103-122` | writes `PanelLayerAlpha` on the grab bar — **a second `PanelLayerAlpha` owner** |
| 23 | `wristDebugPanelEntity` + `WristAttachedSystem` visibility | `Entity?` | `:207, :1577-1586`; `WristAttachedSystem.kt:39-55` | `WristAttachedSystem` sets `Visible(wristIsInFrontOfHead)` | DEBUG-only panel (`:490, :614`) |

Legend: `#` is a local index referenced nowhere else. "Owner" means the single code site that mutates it.

---

## (b) Duplicate mappings — one user-visible outcome, several holders

| Concept (user-visible outcome) | Representations | Mapping / duplication sites | Owners |
|---|---|---|---|
| **Workbench visible** | ① `PlaybackCanvas` ∈ {QUIET_WATCH, PLAYBACK, BROWSE, CONTEXT} (`PlaybackCanvasContracts.kt:4-9`) ② `PanelSlot` set (`:77-80`) ③ `WorkbenchEvent` (`ImmersiveWorkbenchState.kt:34-43`) ④ `ImmersiveWorkbenchState.visible` + `.content` ⑤ `Set<WorkbenchModule>` ⑥ `PanelSlot` set **again** via `toPanelSlot()` ⑦ ECS `Visible`/`PanelLayerAlpha` | `PlaybackCanvasReducer.visibleSlots` `PlaybackCanvasContracts.kt:71-87` → `ImmersiveWorkbenchHost.applyCanvasSlots` `ImmersiveWorkbenchHost.kt:27-37` (`when { PanelSlot.BROWSE in … PanelSlot.CONTEXT in … PanelSlot.TRANSPORT in … }`, so `MEDIA_STAGE`/`SYSTEM_TOOLBAR` are simply dropped) → `ImmersiveWorkbenchReducer.modules` `ImmersiveWorkbenchState.kt:73-97` → `applyWorkbenchModules` `SpatialVideoSampleActivity.kt:2108-2140` → `toPanelSlot()` `:2142-2148` | **SEVERAL.** `PlaybackCanvasState` is the nominal owner, but `ImmersiveWorkbenchState` caches a derived view and the Activity caches `appliedModules` (`ImmersiveWorkbenchHost.kt:12`) and `appliedVisibleSlots` (`ImmersivePlaybackCanvasHost.kt:17`). Only one of {BROWSE, CONTEXT, TRANSPORT} survives the `when` — the mapping is lossy. |
| **`PlaybackCanvas`/`PlaybackCanvasState` vs `ImmersiveWorkbenchState`/`WorkbenchContent`** | `PlaybackCanvas.{QUIET_WATCH,PLAYBACK,BROWSE,CONTEXT}` vs `WorkbenchContent.{NONE,WORKBENCH_EMPTY,BROWSE,VIDEO_CONTEXT,FOCUS}` | Both enums name the same routes with different spellings: `BROWSE`↔`BROWSE`, `CONTEXT`↔`VIDEO_CONTEXT`, plus core has no `WORKBENCH_EMPTY`/`FOCUS` and app has no `QUIET_WATCH`/`PLAYBACK`. Translation is the `when` at `ImmersiveWorkbenchHost.kt:30-35`, and `ImmersiveWorkbenchState.kt:89-94` maps `WorkbenchContent` → `WorkbenchModule`. | **TWO.** `ImmersiveWorkbenchState.content` has **no reader outside its own file** — grep for `WorkbenchContent` matches only `ImmersiveWorkbenchState.kt` and the test `ImmersiveWorkbenchStateTest.kt`. It is a derived shadow of `PlaybackCanvas`. |
| **`WorkbenchContent` vs `SearchWorkspaceRoute`** | `WorkbenchContent.WORKBENCH_EMPTY/BROWSE/VIDEO_CONTEXT/FOCUS` vs `SearchWorkspaceRoute.WORKBENCH_EMPTY/RECOMMENDATIONS/SEARCH_EMPTY/SEARCH_RESULTS` | `WORKBENCH_EMPTY` exists as an identically-named member of **both** enums (`ImmersiveWorkbenchState.kt:18` and `ViriViriAppState.kt:56`). The visible center-panel body is chosen from `SearchWorkspaceRoute` (`RecommendationUi.kt:271, :346, :401`) while the center panel's *presence* is chosen from `WorkbenchModule.CENTER_CONTENT` (`SpatialVideoSampleActivity.kt:2125-2131`). The two are kept in step by hand at `SpatialVideoSampleActivity.kt:2099-2106` and `:2094-2096`. | **SEVERAL / nothing enforces it.** Comment at `:2082-2093` documents that the route reset "used to happen only via `applyPlaybackCanvasSlots`, whose condition … cannot hold right after a dismissal", i.e. the two holders did drift. |
| **Focus panel open** | `PlaybackCanvas.FOCUS` does not exist; `WorkbenchContent.FOCUS` does; `PanelSlot.FOCUS` exists in core; `FocusTarget` + `ThemeRoute` exist in the theme; `SpatialTheme.layouts[FOCUS]` exists (`CinemaTheme.kt:138-171`); `PlaybackCanvasReducer.visibleSlots` never emits `PanelSlot.FOCUS` (`PlaybackCanvasContracts.kt:77-80`) | `WorkbenchEvent.OpenFocus` is dispatched **only from tests** (`ImmersiveWorkbenchStateTest.kt`); grep for `WorkbenchEvent.` finds no app-layer producer. So `WorkbenchContent.FOCUS`, `WorkbenchModule.CENTER_CONTENT`-via-FOCUS, `PanelSlot.FOCUS`, `ImmersiveLayoutMode.FOCUS` and `CinemaTheme.focusLayout()` are all unreachable at runtime. | **NO OWNER at runtime** — five declarations, zero live writers. |
| **Danmaku on/off** | `DanmakuMode.{OFF,FLAT,SPATIAL}` (`ThemeContracts.kt:154-158`) and `ThemeAction.SetDanmakuMode` (`:167`) exist; `PanelSlot` has none; the app has **no** danmaku toggle at all | Danmaku overlay panel is created with a hardcoded `Visible(true)` (`SpatialVideoSampleActivity.kt:1537`) and never toggled. grep for `danmakuEnabled/isDanmakuVisible/danmakuVisible/showDanmaku/toggleDanmaku` → **no matches**. `DanmakuOverlay` only early-returns when no video is selected (`DanmakuOverlay.kt:49-52, :63`). | **NONE.** `DanmakuMode` is a contract-layer declaration with no runtime consumer; "danmaku off" is not expressible in the app today. |
| **2D vs immersive** | ① `ViriViriDestination.{RECOMMENDATIONS,VIEWER}` (`ViriViriAppState.kt:37`) ② `PlaybackCanvas` (core) ③ `ImmersiveBrowseSession.isActive` (`ImmersiveBrowseSession.kt:7`) ④ which Activity is foreground (`PancakeActivity` vs `SpatialVideoSampleActivity`, both in `AndroidManifest.xml`) ⑤ `EXTRA_REATTACH_IMMERSIVE_OUTPUT` intent flag (`SpatialVideoSampleActivity.kt:2904-2905`) ⑥ `PlayerSession.surface` (`ViriViriAppState.kt:178`) | `launchPanelModeInHome` `:1757-1776` + `open_2d_button` `:1656-1659`; return path `PancakeActivity.kt:33-41`; handoff `PlayerSession.beginOutputHandoff` `ViriViriAppState.kt:199-202`; reattach `ImmersiveMediaStageHost.attachOutputAfterHandoff` `ImmersiveMediaStageHost.kt:42-55` driven by `SpatialVideoSampleActivity.kt:1814-1820` | **SEVERAL but deliberately orchestrated**: the Activity boundary plus one explicit one-shot intent signal. Not a state-machine duplicate, but the same outcome ("which output is live") is encoded in ≥4 places. |
| **PanelSlot visibility vs WorkbenchModule set vs stage entity visibility** | ① `Set<PanelSlot>` from `visibleSlots` ② `Set<WorkbenchModule>` from `modules()` ③ `shouldShowWorkbenchModule` final gate `ImmersiveWorkbenchState.kt:45-51` ④ per-entity `Visible` + `PanelLayerAlpha` ⑤ `stageBackdropEntity` visibility keyed off `modules.isNotEmpty()` | `toPanelSlot()` `SpatialVideoSampleActivity.kt:2142-2148` collapses **7 modules onto 4 slots** (`SHORTS_ACTIONS`→`TRANSPORT`, `CENTER_CONTENT`→`BROWSE`, `DETAIL_RAIL`+`VIDEO_CONTEXT`→`CONTEXT`, `NAVIGATION`+`PLAYBACK_CONFIG`→`SYSTEM_TOOLBAR`). The stage backdrop is *not* a module — it is switched on by "any module at all" (`:2132-2139`), so `PanelSlot.MEDIA_STAGE` means two different things depending on the call site: the real media entity vs the dim layer (`:1522` vs `:2135`). | **ONE nominal owner** (`SpatialPanelVisibilityController`) but **three independent writers of `PanelLayerAlpha`** — see the "PanelLayerAlpha" row below. |
| **Stage size** | ① `PlaybackCanvasSize` enum + `scale` (`PlaybackCanvasSize.kt:4-12`) ② `ViriViriUiState.playbackCanvasSize` ③ `ViriViriUiState.playbackStageScale: Float` ④ `appliedStageScale` (Activity) ⑤ `grabBarContentHalfHeight` ⑥ panel *shape* width/height (`scaledStageWidth/Height`) ⑦ persisted key `playback_stage_scale` | `selectPlaybackCanvasSize` writes ②+③+⑦ (`ViriViriAppState.kt:831-840`); `setPlaybackStageScale` writes ③+⑦ **only** (`:842-848`). So after a thumbstick or debug-slider change, ② is stale while ③ is current. The right-rail label renders ② (`SpatialVideoSampleActivity.kt:399, :2209-2211`), so the displayed "Canvas size: 标准" can be wrong. | **ONE key, TWO writers, TWO in-memory representations**; the enum copy is never re-derived (grep `playbackCanvasSize` → only `:99, :238, :833-838` in app state, plus read sites). |
| **Curvature** | `playbackVideoCurvature`, `playbackDanmakuCurvature`, `playbackBackdropCurvature` (state) + 3 persisted keys + `applied*Curvature` (Activity) + `PlaybackStageCurvatureLayer` + `curvatureFor`/`withCurvature` | See Q5/Q9. Always written in lockstep `ViriViriAppState.kt:862-878`. | **ONE effective value, 3+3+3 duplicated slots.** |
| **Transport visible** | `ImmersiveTransportOverlayState.visible` (`:151`) + `controllerView.visibility` (`:1895`) + `controllerView.alpha` (`:1896`) + `ImmersiveTransportPrimaryAction` policy (`ImmersiveTransportOverlayPolicy.kt:12-16`, a constant that carries no state) | `animateControllerVisibility` `:1869-1891` vs `applyTransportOverlayVisibility` `:1893-1899` — two functions mutating the same three things differently (one animates, one snaps). | **TWO writers**, partially redundant. |
| **Grab-bar visibility** | `GrabBarHoverSystem.displayedAlpha` + `PanelLayerAlpha` on the grab-bar entity | `GrabBarHoverSystem.kt:103-122` writes `PanelLayerAlpha` directly, bypassing `SpatialPanelVisibilityController`. | **SECOND `PanelLayerAlpha` owner** alongside `SpatialPanelVisibilityController` (`:28-47`) and `WristAttachedSystem` (`Visible`, not alpha — `:54`). |
| **Passthrough / MR mode** | `scene.isSystemPassthroughEnabled()` + `SpatialVideoSampleActivity.inMrMode` + `MRPanel`'s `remember { mutableStateOf(isMrModeDefault) }` + the intent extra string `"isMrMode"` | `:2863` passes the boolean as a **String** into the panel intent; `MRPanel.kt:43` re-hosts it in local Compose state; `MRPanel.kt:79-82` then pushes it back through `executeOnVrActivity { it.setMrMode(enabled) }`. The panel's own copy is never resynchronised if the Activity changes mode. | **THREE holders, one-way sync.** |
| **Selected video present** | `ViriViriUiState.selected` + `hasWorkbenchDataSource` | `:379-385` mirrors `selected != null` into a local Boolean. | **TWO** (explicit hand-rolled mirror). |
| **Keyboard / input-method panel visible** | `SearchWorkspaceState.{textInputTarget, isKeyboardVisible, isKeyboardDismissed}` (3 mutually-redundant flags) + ECS `Visible` on `inputMethodPanelEntity` | `syncInputMethodPanelVisibility` `:1493-1504` needs **all three** flags (`route == SEARCH_EMPTY && isKeyboardVisible && !isKeyboardDismissed`). Writers keep them in sync by hand at `ViriViriAppState.kt:533-573`. | **ONE owner, three redundant flags.** |

---

## (c) Persisted keys — every one, with classification

All six keys live in one file: `SharedPreferencesAppPreferences`, prefs file name
`"viriviri_app_preferences"` (`AppPreferences.kt:93`). There is exactly **one** persistence mechanism
in the app: grep for `SharedPreferences|DataStore|getSharedPreferences|PreferenceManager` matches only
`AppPreferences.kt:4,39,40,41` and `ViriViriAppState.kt:231`. **No DataStore anywhere.**

| Key (literal) | Type / encoding | Default | Read site | Write site | Settings UI? | Classification |
|---|---|---|---|---|---|---|
| `search_history` | `String` — Base64(URL, no padding) entries joined by `,` | `emptyList()` | `AppPreferences.kt:43-44` ← `ViriViriAppState.kt:246` | `AppPreferences.kt:46-48` ← `ViriViriAppState.kt:480` (remove) and `:616` (submit search) | In-panel history list, not a settings screen (`SearchWorkspaceState.history`) | **USER SETTING (content, not spatial)** |
| `playback_stage_scale` | `String` — `Float.toString()` | `PlaybackCanvasSize.STANDARD.scale` = `1.0f` (`AppPreferences.kt:128-129`) | `AppPreferences.kt:50-51` ← `ViriViriAppState.kt:239` | `AppPreferences.kt:53-55` ← **two writers**: `ViriViriAppState.kt:834` (`selectPlaybackCanvasSize`, user menu) and `:846` (`setPlaybackStageScale`, debug slider `SpatialVideoSampleActivity.kt:1724` **and** right-thumbstick `AnalogMediaStageTuningSystem` via `:556`) | **YES** — `canvas_size_button` in the right rail: layout `mode_panel.xml:57-63`, click `SpatialVideoSampleActivity.kt:1624-1628`, menu `:2388-2403`. Also a debug slider `scale_bar` (`mode_panel.xml:101-108`, click `:1715-1732`) | **USER SETTING** (clamped `0.70 … 2.00` — `PlaybackCanvasSize.kt:15,24`). Debug affordances write the *same* key. |
| `playback_video_curvature` | `String` — radius metres as `Float.toString()`, `0` = flat sentinel (`PlaybackStageCurvature.FLAT_SENTINEL`, `PlaybackStageCurvature.kt:88`) | `Flat` (anything null/non-finite/≤0/≥`MAX_RADIUS_METERS`=8 → `Flat`, `:112-116`) | `AppPreferences.kt:57-58` ← `ViriViriAppState.kt:240` | `AppPreferences.kt:60-62` ← `ViriViriAppState.kt:869` (inside `setPlaybackCurvature`) | **NO menu/binding.** Value is reachable only via the right thumbstick on the stage (`SpatialVideoSampleActivity.kt:557` → `adjustPlaybackCurvature` `ViriViriAppState.kt:885-887`) and the debug `reset_curve_button` (`mode_panel.xml:135-142`, click `:1736-1738`) | **DEBUG-ONLY affordance, persistent effect.** `docs/research/stage-video-mesh-curvature.md:237-260` calls the reset a "Debug escape hatch" and notes the bad value is persisted, "so relaunching the app" kept it broken. |
| `playback_danmaku_curvature` | same encoding | `Flat` | `AppPreferences.kt:64-65` ← `ViriViriAppState.kt:241` | `AppPreferences.kt:67-69` ← `ViriViriAppState.kt:870` | none | **REDUNDANT DUPLICATE** — always written with the same value as the video key. |
| `playback_backdrop_curvature` | same encoding | `Flat` | `AppPreferences.kt:71-72` ← `ViriViriAppState.kt:242` | `AppPreferences.kt:74-76` ← `ViriViriAppState.kt:871` | none | **REDUNDANT DUPLICATE** — same. |
| `workbench_stage_y` | `String` — `Float.toString()`, clamped `0.6 … 3.0 m` (`AppPreferences.kt:146-150`) | key absent → `STAGE_DEFAULT_WORLD_Y = 2.14f` (`ViriViriAppState.kt:35`; `AppPreferenceCodec.decodeStageY` returns `null`) | `AppPreferences.kt:78-79` ← `ViriViriAppState.kt:894-895` ← `SpatialVideoSampleActivity.kt:1026` | `AppPreferences.kt:81-86` ← `ViriViriAppState.kt:898-899` ← `SpatialVideoSampleActivity.kt:578-584` (grab-release edge) | **NO menu.** Only the debug `reset_stage_y_button` (`mode_panel.xml:109-116`, click `SpatialVideoSampleActivity.kt:1733-1735` → `resetStageYToDefault` `:1126-1129` → `clearWorkbenchStageY`) | **USER SETTING via grab gesture; the only *reset* is DEBUG-ONLY.** |

### Geometry that is NOT persisted (verified absences)

| Thing | Evidence it is not persisted |
|---|---|
| **Display ratio / stage aspect** | `selectPlaybackDisplayRatio` (`ViriViriAppState.kt:825-829`) writes `mutableState` only — no `appPreferences` call. `PlaybackDisplayRatio` has no key. Resets to `AUTO` every launch (`:98`). |
| `PlaybackQuality` | `selectPlaybackQuality` `:811-823`, no preference write. |
| **z-offset** (video surface base Z, backdrop base Z, danmaku Z, grab-bar forward offset) | `videoSurfaceBaseLocalZ = 0f` (`:192`), `stageBackdropBaseLocalZ = 0.01f` (`:175`), `DANMAKU_BASE_LOCAL_Z_CANDIDATES` (`:2938`) selected by process-local `danmakuBaseLocalZIndex` (`:189`), `GRAB_BAR_FRONT_OFFSET` (`:2970`). No preference key. |
| **Workbench panel layout** (center/rail/nav/transport/keyboard offsets, yaws, pitches, backdrop alpha) | All are `WorkbenchLayoutConfig` constructor defaults; the Activity holds `val layout = WorkbenchLayoutConfig.DEFAULT` (`:271`) — a `val`, never reassigned. Comment at `WorkbenchLayoutConfig.kt:108`: *"Default layout used by the host; swap for a loaded/preset config in settings."* |
| **Grab rest pose (full)** | Only **Y** is persisted. `VideoStageGrabPersistenceSystem.kt:12-15`: *"Only the vertical offset (Y) survives restart; horizontal position (X/Z) and yaw are reset to their authored defaults on every launch"*. Reinforced at `SpatialVideoSampleActivity.kt:1022-1025`. |
| **Curvature target layer** | `PlaybackStageCurvatureLayer` + `next()` (`PlaybackStageCurvatureLayer.kt:16`) have **no runtime callers** — grep matches only the enum file, `ViriViriAppState.kt:122-138` (`curvatureFor`/`withCurvature`) and `PlaybackStageCurvatureTest.kt`. Not persisted, not reachable. |
| **Danmaku on/off** | Does not exist (see Q2). |
| **MR/passthrough mode** | `setMrMode` `:2876-2899`, no preference write; door into it is a `Switch` (`MRPanel.kt:79`). |
| `appliedStageScale` / `applied*Curvature` | Activity fields, never written to prefs. |

---

## Q1 — Every distinct state holder that can affect the screen

Answered by table **(a)** above. Summary of the layering:

- **Core contract layer** (module `spatial-workbench-core`): `PlaybackCanvasState`, `SpatialTheme`,
  `PanelSlot`, `MediaStageState`. Pure, no Android dependency.
- **App product/UI state**: `ImmersiveWorkbenchState`, `ViriViriUiState` (+ nested `SearchWorkspaceState`),
  `ImmersivePlaybackControlState`, `ImmersiveTransportOverlayState`, `ImmersiveTransportTimeline`.
- **App per-activity ephemeral**: ~12 `private var` mirrors in `SpatialVideoSampleActivity` plus
  `ImmersiveBrowseSession`, `spatialVideoAspectProbeState`.
- **Platform-rendering state**: ECS `Visible` / `PanelLayerAlpha` / `Transform`, and Android
  `View.visibility` / `ObjectAnimator`.
- **Host caches**: `ImmersiveWorkbenchHost.appliedModules` (`:12`),
  `ImmersivePlaybackCanvasHost.appliedVisibleSlots` (`:17`),
  `SpatialPanelVisibilityController.pendingAnimations` (`:18`).

---

## Q2 — Duplicate mapping detail

Answered by table **(b)**. The two hardest offenders, with the exact evidence:

### 2.1 `PlaybackCanvas` → `PanelSlot` → `WorkbenchEvent` → `WorkbenchContent` → `WorkbenchModule` → `PanelSlot` (a full round trip, lossy at both ends)

```kotlin
// PlaybackCanvasContracts.kt:75-81
val requested =
    when (state.canvas) {
      PlaybackCanvas.QUIET_WATCH -> setOf(PanelSlot.MEDIA_STAGE)
      PlaybackCanvas.PLAYBACK -> setOf(PanelSlot.MEDIA_STAGE, PanelSlot.TRANSPORT, PanelSlot.SYSTEM_TOOLBAR)
      PlaybackCanvas.BROWSE -> setOf(PanelSlot.MEDIA_STAGE, PanelSlot.BROWSE)
      PlaybackCanvas.CONTEXT -> setOf(PanelSlot.MEDIA_STAGE, PanelSlot.CONTEXT)
    }
```

```kotlin
// ImmersiveWorkbenchHost.kt:29-36 — the PanelSlot set is collapsed back to ONE event
dispatch(
    when {
      PanelSlot.BROWSE in visibleSlots -> WorkbenchEvent.OpenBrowse
      PanelSlot.CONTEXT in visibleSlots -> WorkbenchEvent.OpenVideoContext
      PanelSlot.TRANSPORT in visibleSlots -> WorkbenchEvent.RevealTransport
      else -> WorkbenchEvent.Dismiss
    }
)
```

```kotlin
// SpatialVideoSampleActivity.kt:2142-2148 — the modules are re-encoded INTO THE SAME PanelSlot enum
private fun WorkbenchModule.toPanelSlot(): PanelSlot =
    when (this) {
      WorkbenchModule.TRANSPORT, WorkbenchModule.SHORTS_ACTIONS -> PanelSlot.TRANSPORT
      WorkbenchModule.CENTER_CONTENT -> PanelSlot.BROWSE
      WorkbenchModule.DETAIL_RAIL, WorkbenchModule.VIDEO_CONTEXT -> PanelSlot.CONTEXT
      WorkbenchModule.NAVIGATION, WorkbenchModule.PLAYBACK_CONFIG -> PanelSlot.SYSTEM_TOOLBAR
    }
```

Consequences visible in the source: the `when` at `ImmersiveWorkbenchHost.kt:30-35` **drops
`MEDIA_STAGE` and `SYSTEM_TOOLBAR`**, so the fact that `PlaybackCanvas.PLAYBACK` requests the system
toolbar exists only inside `PlaybackCanvasContracts.kt`; it is re-introduced downstream by
`WorkbenchModule.NAVIGATION` being unconditionally added whenever `visible` is true
(`ImmersiveWorkbenchState.kt:76`). Two independent paths produce the same nav-bar visibility.

### 2.2 `PanelSlot` visibility vs `WorkbenchModule` set vs stage-entity visibility

`PanelSlot.MEDIA_STAGE` addresses **two different entities**:

```kotlin
// SpatialVideoSampleActivity.kt:1522 — the DIM LAYER, keyed off "any module"
spatialPanelVisibilityController.setVisible(PanelSlot.MEDIA_STAGE, stageBackdropEntity!!, modules.isNotEmpty())
```

```kotlin
// SpatialVideoSampleActivity.kt:2132-2139 — same slot name again, in the normal apply path
stageBackdropEntity?.let { entity ->
  spatialPanelVisibilityController.setVisible(PanelSlot.MEDIA_STAGE, entity, visibleModules.isNotEmpty())
}
```

The real media surface (`videoSurfaceEntity`, `:204`) is **never** passed to
`SpatialPanelVisibilityController` — it stays visible unconditionally. So the "MEDIA_STAGE slot" is
visible-or-not only in its backdrop meaning. There is **one** controller but the identity of the
entity behind the slot is call-site dependent.

Additionally the **stage backdrop's dim alpha is not owned by the controller's state**: the panel's
alpha comes from `WorkbenchLayoutConfig.stageBackdropAlpha = 0.42f`
(`WorkbenchLayoutConfig.kt:72` → `DanmakuOverlay.kt:30-33` `StageBackdrop(alpha)`), while
`SpatialPanelVisibilityController` forces `visibleAlpha = 1f` (`:64`) for every panel it fades. The
comment at `SpatialPanelVisibilityController.kt:13` acknowledges the split: *"Panel layers are fully
opaque when visible. MediaStage dimming belongs only to StageBackdrop."*

### 2.3 `SearchWorkspaceRoute` vs `WorkbenchContent`

`WorkbenchContent.WORKBENCH_EMPTY` (`ImmersiveWorkbenchState.kt:18`) and
`SearchWorkspaceRoute.WORKBENCH_EMPTY` (`ViriViriAppState.kt:56`) are two identical member names in
two enums in the same package. **`WorkbenchContent` has no reader outside `ImmersiveWorkbenchState.kt`**;
the center panel's actual body is selected from `SearchWorkspaceRoute`
(`RecommendationUi.kt:212, :271, :346, :401`). The two are hand-synchronised in the Activity:

```kotlin
// SpatialVideoSampleActivity.kt:2099-2106
private fun applyPlaybackCanvasSlots(visibleSlots: Set<PanelSlot>) {
  Log.d(WORKBENCH_TRACE_TAG, "applyPlaybackCanvasSlots slots=$visibleSlots")
  if (::immersiveWorkbenchHost.isInitialized) immersiveWorkbenchHost.applyCanvasSlots(visibleSlots)
  val appState = ViriViriApplication.appState
  if (PanelSlot.BROWSE !in visibleSlots && PanelSlot.TRANSPORT in visibleSlots && appState.state.value.selected != null) {
    appState.openWorkbenchEmpty()
  }
}
```

and the in-code admission that this coupling is fragile is at `:2082-2093`:

> *"The reset used to happen only via `applyPlaybackCanvasSlots`, whose condition (`BROWSE !in slots &&
> TRANSPORT in slots && selected != null`) cannot hold right after a dismissal … So the route stayed
> stale until the next summon flipped the canvas back to PLAYBACK — which is the route switch the user
> saw on open."*

---

## Q3 — Is `ImmersivePlaybackCanvasHost` instantiated? Is `applyVisibleSlots` live?

**LIVE. Not dead code.** Repo-wide (excluding `temp\`) grep for `ImmersivePlaybackCanvasHost` and
`applyVisibleSlots`:

- `app\src\main\java\com\m0e_n00b\viriviri\ImmersivePlaybackCanvasHost.kt:11` (declaration)
- `app\src\main\java\com\m0e_n00b\viriviri\SpatialVideoSampleActivity.kt:230` (field)
- `app\src\main\java\com\m0e_n00b\viriviri\SpatialVideoSampleActivity.kt:373` (**instantiation**)
- `app\src\test\java\com\m0e_n00b\viriviri\ImmersivePlaybackCanvasHostTest.kt` (unit tests)
- `app\src\main\java\com\m0e_n00b\viriviri\PanelLayerAlphaSystem.kt:11` (doc reference only)

The instantiation:

```kotlin
// SpatialVideoSampleActivity.kt:372-373
immersivePlaybackCanvasHost =
    ImmersivePlaybackCanvasHost(applyVisibleSlots = ::applyPlaybackCanvasSlots)
```

Entry points that actually run it:
`applyInitialState()` `SpatialVideoSampleActivity.kt:1111` (in `onVRReady`),
`applyCurrentState()` `:1795` (`onResume`) and `:2824` (`syncPlaybackControls`), and
`dispatch(...)` via `dispatchPlaybackCanvas` `:2012-2014` from 11 call sites (`:303, :427, :436, :1114,
:2005, :2041, :2049, :2057, :2063, :2080`, plus `:1909` directly).

Full trace of what `applyVisibleSlots` does to Spatial entities:

```
ImmersivePlaybackCanvasHost.applyCurrentVisibleSlots()          ImmersivePlaybackCanvasHost.kt:28-33
  └─ PlaybackCanvasReducer.visibleSlots(state, theme)           PlaybackCanvasContracts.kt:71-87
       └─ applyVisibleSlots(Set<PanelSlot>)  ==  ::applyPlaybackCanvasSlots
            SpatialVideoSampleActivity.kt:2099-2106
              ├─ immersiveWorkbenchHost.applyCanvasSlots(slots)         ImmersiveWorkbenchHost.kt:27-37
              │    └─ dispatch(WorkbenchEvent…)                          ImmersiveWorkbenchHost.kt:14-25
              │         └─ applyModules(Set<WorkbenchModule>) == ::applyWorkbenchModules
              │              SpatialVideoSampleActivity.kt:2108-2140
              │                └─ shouldShowWorkbenchModule(…)          ImmersiveWorkbenchState.kt:45-51
              │                     └─ spatialPanelVisibilityController.setVisible(slot, entity, visible)
              │                          SpatialPanelVisibilityController.kt:20-55
              │                            ├─ entity.setComponent(Visible(true|false))     :28, :31, :46
              │                            └─ entity.setComponent(PanelLayerAlpha(0f|1f))  :30, :43
              │                                 entities: R.id.mr_panel / controls_id /
              │                                 video_selector_panel / mode_panel /
              │                                 centerContentEntity / stageBackdropEntity
              └─ if (BROWSE !in slots && TRANSPORT in slots && selected != null)
                   appState.openWorkbenchEmpty()                       ViriViriAppState.kt:407-420
                        └─ SearchWorkspaceState.route = WORKBENCH_EMPTY → Compose center panel recomposes
```

So the answer is: **instantiated, dispatched, and the only path that turns Spatial panel entities
on and off.** Its `applyVisibleSlots` lambda is a real entity mutator, not a stub.

---

## Q4 — `SearchWorkspaceRoute`

**Values** (`ViriViriAppState.kt:55-60`):

```kotlin
enum class SearchWorkspaceRoute {
  WORKBENCH_EMPTY,
  RECOMMENDATIONS,
  SEARCH_EMPTY,
  SEARCH_RESULTS,
}
```

**Stored in:** `SearchWorkspaceState.route` (`ViriViriAppState.kt:79`), which is nested inside
`ViriViriUiState.searchWorkspace` (`:115`), inside the single `MutableStateFlow` (`:235, :292`). It is
never persisted.

**Every writer** (all inside `ViriViriAppState`, all `mutableState.value = … .copy(route = …)`):

| Line | Method | Value written |
|---|---|---|
| `:344` | `refreshRecommendations` | `RECOMMENDATIONS` |
| `:384` | `openRecommendationList` | `RECOMMENDATIONS` |
| `:399` | `openPlaybackReturnRoute` | `playbackReturnRoute(workbenchReturnTarget)` |
| `:413` | `openWorkbenchEmpty` | `WORKBENCH_EMPTY` |
| `:428` | `openSearchWorkspace` | `SEARCH_EMPTY` |
| `:444` | `returnToSearchEmpty` | `SEARCH_EMPTY` |
| `:459` | `closeSearchWorkspace` | `RECOMMENDATIONS` |
| `:623` | `submitSearch` (blank query) | `SEARCH_EMPTY` |
| `:647` | `submitSearch` (real query) | `SEARCH_RESULTS` |
| `:764` | `returnToRecommendationsFeed` | `RECOMMENDATIONS` |

External callers of those mutators: `SpatialVideoSampleActivity.kt:2038, :2047, :2056, :2095`,
`RecommendationUi.kt:327` (`appState::returnToSearchEmpty`), plus Compose navigation.

**Every reader:**

| Site | Use |
|---|---|
| `ViriViriAppState.kt:144, :147` | `isShowingSearchResults`, `isSearchEmpty` derived getters |
| `SpatialVideoSampleActivity.kt:1496` | gates the input-method panel `Visible` |
| `RecommendationUi.kt:212, :252, :263, :271, :294-295, :314, :327, :331, :347-349, :361, :365, :391, :401-405, :452` | **the actual center-panel body** |
| `ImmersiveInputMethodPanel.kt:23` | `route != SEARCH_EMPTY … return` |
| `ViriViriAppState.kt:439` | `returnToSearchEmpty` early-return guard |

**Does it duplicate `WorkbenchContent`? Yes.**
`WorkbenchContent.BROWSE` (`ImmersiveWorkbenchState.kt:19`) and `SearchWorkspaceRoute.RECOMMENDATIONS` /
`SEARCH_RESULTS` / `SEARCH_EMPTY` all describe "what the center workspace shows", and
`WorkbenchContent.WORKBENCH_EMPTY` is byte-for-byte the same member name as
`SearchWorkspaceRoute.WORKBENCH_EMPTY`. `WorkbenchContent` has **no consumer outside its own file**
(verified by repo-wide grep: main-source matches are confined to `ImmersiveWorkbenchState.kt:7,16,60,64,66,67,90-93`;
every other match is in `app/src/test/java/com/m0e_n00b/viriviri/ImmersiveWorkbenchStateTest.kt:21,55,85`
and `ImmersiveWorkbenchHostTest.kt:15`); `SearchWorkspaceRoute` has ~20 readers. So `WorkbenchContent`
is the redundant copy — it exists solely to be translated into `WorkbenchModule.CENTER_CONTENT`
(`ImmersiveWorkbenchState.kt:89-94`), which in turn is translated back into `PanelSlot.BROWSE`
(`SpatialVideoSampleActivity.kt:2145`).

Two in-tree documents already record this overlap as an open question rather than a settled design:

- `docs/ui-structure-and-terminology.md:745` — *"`WorkbenchContent`（app，中心内容路径）语义相近但互不继承，容易误读。"*
  (semantically close, no inheritance relationship, easily misread.)
- `docs/immersive-theme-runtime-design.md:169` — decision row 56: *"路由归属 `SearchWorkspaceRoute` vs
  `WorkbenchContent` | **[决策]** 语义重叠"*, citing `ImmersiveWorkbenchState.kt:16-22` and
  `ViriViriAppState.kt:55`; the open-decision list repeats it at `:880` (D3: merge, or split explicitly).
  (`docs/immersive-theme-runtime-design.md` is untracked in the working tree — treated as a
  design-intent document, not as evidence of runtime behaviour.)
- `docs/ui-structure-and-terminology.md:758` independently confirms the unreachability found in Q2 for
  the FOCUS/SHORTS branch: *"`WorkbenchPresentation.SHORTS`、`WorkbenchContent.FOCUS` 均无对应实体或行为。"*

---

## Q5 — Persistence audit (panel/stage geometry across restarts)

Repeat of table **(c)** plus the special-attention items the task called out:

| Requested item | Persisted? | Evidence |
|---|---|---|
| **Stage size** | **YES** — `playback_stage_scale` | key `AppPreferences.kt:95`; write `:53-55`; read `:50-51`; clamped `0.70…2.00` (`PlaybackCanvasSize.kt:15,24`) |
| **Stage aspect / display ratio** | **NO** | `selectPlaybackDisplayRatio` `ViriViriAppState.kt:825-829` touches only `mutableState`; no key in `AppPreferences.kt:92-100`; default `AUTO` at `:98` |
| **Curvature (3 redundant keys)** | **YES ×3, all identical** | keys `playback_video_curvature` / `playback_danmaku_curvature` / `playback_backdrop_curvature` (`AppPreferences.kt:96-98`); all three written with the same value `ViriViriAppState.kt:869-871` |
| **Stage scale** | same key as stage size (`playbackStageScale`) | `:846, :834` |
| **z-offset** | **NO** | see table (c) "Geometry that is NOT persisted" |
| **Workbench height** | **YES** — `workbench_stage_y` | write `ViriViriAppState.kt:898-899` ← `SpatialVideoSampleActivity.kt:582`; read `ViriViriAppState.kt:894-895` ← `SpatialVideoSampleActivity.kt:1026` |
| **Grab rest pose** | **Y only** | `VideoStageGrabPersistenceSystem.kt:12-15`; `SpatialVideoSampleActivity.kt:1022-1025` |

### The curvature triple, in the source's own words

```kotlin
// ViriViriAppState.kt:854-871
/**
 * Persists and publishes the stage curvature — ONE value shared by every layer.
 * …
 * The three persisted keys predate the shared parameter and are all written
 * with the same value; collapsing them into one is a follow-up cleanup, not a behaviour change.
 */
fun setPlaybackCurvature(curvature: PlaybackStageCurvature) {
  …
  appPreferences.savePlaybackVideoCurvature(curvature)
  appPreferences.savePlaybackDanmakuCurvature(curvature)
  appPreferences.savePlaybackBackdropCurvature(curvature)
```

Cross-confirmed by the two docs already in the tree:
`docs/research/stage-video-mesh-curvature.md:615-619` ("the three state fields, the three persisted
keys, `curvatureFor` / `withCurvature` / `cyclePlaybackCurvatureEditLayer` and
`PlaybackStageCurvatureLayer` are now redundant … Collapsing them into one field and one key is a
follow-up cleanup") and `docs/ui-structure-and-terminology.md:708` ("三个字段与三个持久化 key 待合并为一个").

### Classification

| Key | Classification |
|---|---|
| `search_history` | **USER SETTING** (non-spatial) |
| `playback_stage_scale` | **USER SETTING** (right-rail canvas-size menu), also writable by a debug slider and the thumbstick |
| `playback_video_curvature` | **DEBUG-ONLY affordance** (thumbstick + debug reset button, no menu) — but a real user setting in effect |
| `playback_danmaku_curvature` | **THEME/REDUNDANT-DUPLICATE CONFIG** — always equal to the video key |
| `playback_backdrop_curvature` | **THEME/REDUNDANT-DUPLICATE CONFIG** — always equal to the video key |
| `workbench_stage_y` | **USER SETTING** (grab gesture); its only in-app reset is **DEBUG-ONLY** |

---

## Q6 — Is any settings / preferences UI exposed for spatial layout?

**Absent.** No settings screen, activity, composable, or preference binding exists for spatial layout.

Evidence:
1. The only settings affordance in the UI is disabled and labelled as unavailable:

```kotlin
// MRPanel.kt:76-78
IconButton(onClick = {}, enabled = false) {
  Icon(Icons.Default.Settings, contentDescription = stringResource(R.string.nav_settings) + "暂不可用", tint = navigationContent.copy(alpha = 0.42f))
}
```
(`R.string.nav_settings` = "设置" / "Settings", `app/src/main/res/values/strings.xml:10`,
`values-en/strings.xml:10`.) `onClick = {}` + `enabled = false` — it does nothing and cannot be
pressed. The neighbouring account icon is identical (`:73-75`).

2. `WorkbenchLayoutConfig` — the class that owns every panel offset, yaw, pitch and the backdrop
   alpha — is read once as an immutable default and never bound to anything:

```kotlin
// SpatialVideoSampleActivity.kt:270-271
// Centralized Workbench spatial tuning; replace/bind for future settings presets.
private val layout = WorkbenchLayoutConfig.DEFAULT
```

   Its own doc says the binding does not exist yet: `WorkbenchLayoutConfig.kt:15-16` *"Values are
   centralized here so they can later be bound to a settings screen or presets"*;
   `:108` *"Default layout used by the host; swap for a loaded/preset config in settings."*

3. grep for `Settings|settings` across `app\src\main` returns no settings Activity/Compose screen
   (only `UIPanelSettings`/`MediaPanelSettings` SDK types and the disabled icon above).

4. The nearest thing to a spatial control panel is the right rail (`R.id.mode_panel`), whose live
   controls are: **display ratio**, **canvas size** (both genuine user controls), then a block
   labelled in the layout itself as *"Debug telemetry (merged from the standalone debug panel)"*
   (`app/src/main/res/layout/mode_panel.xml:71`).

---

## Q7 — Adjustments reachable only through debug affordances

| Adjustment | Reachable how | Debug-only? | Citation |
|---|---|---|---|
| **Curvature (all layers)** | Point at the stage, push the **right thumbstick left/right**. No menu, no label in the user-facing controls. | The *gesture* is registered in release builds (`systemManager.registerSystem(AnalogMediaStageTuningSystem(...))` is outside any `BuildConfig.DEBUG` guard — `SpatialVideoSampleActivity.kt:548-561`), but it is undocumented in the UI and its only readout/reset is the debug block. | `:548-561`; `onCurvatureDelta = …::adjustPlaybackCurvature` `:557`; `AnalogMediaStageTuningSystem.kt:11-28` |
| **Curvature reset to flat** | `reset_curve_button` inside the right rail's debug block | The button is **not** `BuildConfig.DEBUG`-gated (no `visibility="gone"` in `mode_panel.xml:135-142`; the handler at `:1736-1738` is unconditional), so it ships, but its doc calls it a "Debug escape hatch" | `SpatialVideoSampleActivity.kt:2271-2278` |
| **Stage world Y reset** | `reset_stage_y_button` (also un-gated) | same pattern; doc: *"Debug/testing aid for tuning the default height without being masked by a previously persisted value."* | `:1121-1129`; `mode_panel.xml:109-116` |
| **Stage scale fine control (continuous)** | `scale_bar` SeekBar + `scale_text` in the debug block | Planned debug telemetry; the file comment at `:1660` says *"Merged debug telemetry (was the standalone debug panel)"* | `mode_panel.xml:92-108`; handler `:1715-1732` |
| **Danmaku layer authored Z** | `danmaku_z_button` cycles `DANMAKU_BASE_LOCAL_Z_CANDIDATES` | Pure device-tuning escape hatch, documented as *"Rather than guess the sign and magnitude again, the debug button steps this through candidates so the value that actually renders on top can be read off the headset."* | `:177-189, :2930-2938`; handler `:1739-1751` |
| **Aspect probe (Target / Plan / Apply)** | Three buttons + detail text | Explicitly `BuildConfig.DEBUG`-gated: `visibility` forced `VISIBLE` only inside `if (BuildConfig.DEBUG)` and the XML defaults are `android:visibility="gone"` | `:1642-1655`; `mode_panel.xml:165-198` |
| **Summon workbench with controller A** | Press **A** on the right controller/hand | Registered **only** in DEBUG: `if (BuildConfig.DEBUG) { systemManager.registerSystem(AnalogWorkbenchSummonSystem(...)) }` | `:562-568`; `AnalogWorkbenchSummonSystem.kt:28-38` |
| **Wrist debug panel** | Left wrist HUD, `WristAttachedSystem`-driven | DEBUG only: panel registration added inside `if (BuildConfig.DEBUG)` (`:490`) and created inside `if (BuildConfig.DEBUG)` (`:614`) | `:1560-1586` |
| **Debug build label** | `debug_build_label` TextView | `visibility = View.VISIBLE` only inside `BuildConfig.DEBUG` | `:1629-1633`; `mode_panel.xml:155-164` |
| **Stage backdrop dim alpha** | — | **Not reachable at all**; compile-time constant | `WorkbenchLayoutConfig.kt:72` |
| **Panel offsets / yaws / pitches / keyboard placement** | — | **Not reachable at all**; `WorkbenchLayoutConfig` defaults, never rebound | `WorkbenchLayoutConfig.kt:21-72`; `SpatialVideoSampleActivity.kt:271` |

Notable hazard documented in-code: because the thumbstick curvature control writes a **persisted**
value while its only reset lives inside the Workbench that a bad curvature can make unreachable, the
escape hatch had to be duplicated on button A — `:2016-2027` and `AnalogWorkbenchSummonSystem.kt:8-22`.

Also: `registerFeatures()` adds `CastInputForwardFeature`, `HotReloadFeature`, `OVRMetricsFeature`,
`DataModelInspectorFeature` under `if (BuildConfig.DEBUG)` (`:334-343`), and
`ImmersiveWorkbenchHost`/`SpatialPanelVisibilityController`/`ImmersiveMediaStageHost` all log through
`WORKBENCH_TRACE_TAG` unconditionally (`:365-371`).

---

## Q8 — Single-owner check: MediaStage video surface, ExoPlayer, PlayerSession, output Surface

**Confirmed: exactly one `ExoPlayer`, one `PlayerSession`, and one active video output `Surface`.**

- Exactly **one** `ExoPlayer.Builder` call in the entire repo (excluding `temp\`):
  `ViriViriAppState.kt:177` — `val player = ExoPlayer.Builder(context).build()`, inside
  `class PlayerSession(context: Context)` (`:176`).
- Exactly **one** `PlayerSession` instance:

```kotlin
// ViriViriAppState.kt:226-233
class ViriViriAppState(
    context: Context,
    …
) {
  val playerSession = PlayerSession(context)
```
  and exactly one `ViriViriAppState`, created once per process in `ViriViriApplication.onCreate`
  (`ViriViriApplication.kt:8`: `appState = ViriViriAppState(applicationContext)`), exposed via
  `companion object { lateinit var appState … private set }` (`:19-22`).
- All three Activity hosts reach the same instance: `SpatialVideoSampleActivity.kt:148-149`
  (`val player: ExoPlayer get() = ViriViriApplication.appState.playerSession.player`),
  `PancakeActivity.kt:33` and `:50`, `RecommendationUi.kt:839`
  (`renderSurface = android.view.Surface(texture).also(session::attach2dSurface)`), and
  `DanmakuOverlay.kt:42`.
- **The `PlayerSession` is the sole owner of the active `Surface`.** It holds exactly one
  `private var surface: Surface? = null` (`:178`) and every attach path funnels through one private
  method:

```kotlin
// ViriViriAppState.kt:218-223
private fun attachSurface(newSurface: Surface, force: Boolean = false) {
  if (!newSurface.isValid || (!force && surface === newSurface)) return
  if (surface !== newSurface) surface?.let(player::clearVideoSurface)
  surface = newSurface
  player.setVideoSurface(newSurface)
}
```
  Public entry points: `attachImmersiveSurface` (`:204`), `reattachImmersiveSurface` (`:207`),
  `attach2dSurface` (`:209`), `beginOutputHandoff` (`:199-202`), `detachSurface` (`:211-216`).
  All five are the only `setVideoSurface`/`clearVideoSurface` call sites in the app (verified by grep).
- **The Spatial-side handle is also single-owner.** `ImmersiveMediaStageHost<Output : Any>` holds at
  most one `private var output: Output? = null` (`ImmersiveMediaStageHost.kt:21`) and
  `close()` explicitly refuses to release it — *"Drops only the host's SDK handle reference. The host
  must never release or clear it here."* (`:65-68`). The app supplies exactly one target id:
  `IMMERSIVE_VIDEO_TARGET_ID = "immersive-video"` (`:82`).
- There is exactly one Spatial video panel: `createVideoPanel()` (`SpatialVideoSampleActivity.kt:1188`)
  builds one `PanelSceneObject` (`:1202`) bound to one `videoSurfaceEntity` (`:204, :1168-1185`), and
  `attachImmersiveOutput` (`:1814-1822`) is the only path that feeds the host.
  `MediaStageState.registry` is constructed with a single `MediaStageTargetSpec`
  (`ImmersiveMediaStageHost.kt:23-33`).

**One caveat worth recording (not a violation):** the *output* Surface legitimately changes identity —
2D `TextureView` Surface (`RecommendationUi.kt:839`) ↔ SDK panel Surface
(`SpatialVideoSampleActivity.kt:1816/1819`). The handoff is: `beginOutputHandoff()` clears the player's
surface (`ViriViriAppState.kt:199-202`, called at `SpatialVideoSampleActivity.kt:1657` and
`PancakeActivity.kt:33`), then the return intent carries
`EXTRA_REATTACH_IMMERSIVE_OUTPUT = true` (`:2904-2905`, set at `PancakeActivity.kt:37`), which makes
`attachImmersiveOutput` take the `attachOutputAfterHandoff` branch (`:1815-1817`) and bypass the
"same Surface object" no-op (`ImmersiveMediaStageHost.kt:42-55`). Documented in
`docs/quest-runtime-notes.md:87`.

---

## Q9 — TODO / FIXME / HACK comments about state duplication or geometry sync

**There are zero literal `TODO`, `FIXME`, `HACK`, `XXX` or `WORKAROUND` markers anywhere in the app
sources.** Verified: ripgrep for `TODO|FIXME|HACK|XXX|WORKAROUND` over
`app\src\main\java\com\m0e_n00b\viriviri` → *no matches*.

The equivalent information lives in prose comments. The load-bearing ones:

1. **Curvature: three state fields + three persisted keys are redundant** (the known issue).

```kotlin
// app/src/main/java/com/m0e_n00b/viriviri/ViriViriAppState.kt:854-861
/**
 * Persists and publishes the stage curvature — ONE value shared by every layer.
 *
 * Video, danmaku and backdrop are kept in lockstep. They have to be: the backdrop exists to dim the
 * MediaStage, so a backdrop on a different radius cannot dim it, and a danmaku plane on a different
 * radius slices through it. The three persisted keys predate the shared parameter and are all written
 * with the same value; collapsing them into one is a follow-up cleanup, not a behaviour change.
 */
```

```markdown
docs/research/stage-video-mesh-curvature.md:615-619
Deliberately NOT the full collapse: the three state fields, the three persisted keys, `curvatureFor` /
`withCurvature` / `cyclePlaybackCurvatureEditLayer` and `PlaybackStageCurvatureLayer` are now redundant.
They are kept so this stays a behaviour fix rather than a persistence migration, and they are safe because
lockstep keeps them equal. Collapsing them into one field and one key is a follow-up cleanup, and it should
be done before anyone adds a second curvature source.
```

```markdown
docs/ui-structure-and-terminology.md:708
… 本枚举与 `curvatureFor` / `withCurvature` 已无运行时调用方；三个字段与三个持久化 key 待合并为一个
```

2. **Two state machines in series, and an escape hatch that entered at the wrong one.**

```markdown
docs/research/stage-video-mesh-curvature.md:598-599
The lesson generalises: when a state machine is downstream of another one, an "escape hatch" must enter
at the UPSTREAM entry point, not at the downstream reducer.
```

```kotlin
// app/src/main/java/com/m0e_n00b/viriviri/SpatialVideoSampleActivity.kt:2023-2027
// It calls [onStagePrimaryAction] rather than dispatching a [WorkbenchEvent] itself. An earlier
// version dispatched `RevealTransport` directly and that was wrong: the workbench is DOWNSTREAM of
// the canvas, so the canvas stayed in QUIET_WATCH while the workbench went visible. Slot visibility
// ([ImmersivePlaybackCanvasHost.applyCurrentVisibleSlots] -> `applyPlaybackCanvasSlots`) never ran,
// which is why the A-summoned workbench came up without the transport and dismissed unreliably.
```

```kotlin
// app/src/main/java/com/m0e_n00b/viriviri/AnalogWorkbenchSummonSystem.kt:19-21
// An earlier version dispatched `RevealTransport` directly and that was wrong:
// the workbench is downstream of the canvas, so the canvas stayed in QUIET_WATCH while the workbench
// went visible, which desynced slot visibility (transport missing) and dismissal.
```

3. **Geometry sync is creation-order dependent and silently drop-prone** (three separate admissions).

```kotlin
// app/src/main/java/com/m0e_n00b/viriviri/SpatialVideoSampleActivity.kt:2583-2589
 * [applyPlaybackVideoCurvature] can fire before the panel is created (the app-state collector runs
 * during `onCreate`, while the panel is created later), and [reshapeSpatialVideoPanel] silently
 * drops a reshape when the scene object is missing. Because the caller has already recorded the
 * requested curvature in `appliedVideoCurvature`, its `if (applied == curvature) return` guard then
 * blocks every later retry, so a persisted curvature never reached the panel.
```

```kotlin
// app/src/main/java/com/m0e_n00b/viriviri/SpatialVideoSampleActivity.kt:2551-2556
 * k18 root cause: panel scene objects are instantiated by the SDK's panel-creation system a frame
 * after [Entity.createPanelEntity], so a reshape issued at creation time is silently dropped. The
 * danmaku/backdrop therefore kept the shape their registration produced — and that registration is
 * evaluated before the persisted scale is known — leaving the overlay centred on the stage but
 * never resized.
```

```kotlin
// app/src/main/java/com/m0e_n00b/viriviri/SpatialVideoSampleActivity.kt:2604-2610
 * [entity] must be the handle returned by [Entity.createPanelEntity]. The panel registration id
 * and the spawned entity id are different namespaces, so a freshly built `Entity(R.id...)` handle
 * addresses nothing: every overlay reshape issued through one was silently dropped, which is why
 * the danmaku and backdrop layers never followed the stage scale.
```

4. **Scale was applied on two channels (entity `Scale` vs panel shape) and they disagreed.**

```kotlin
// app/src/main/java/com/m0e_n00b/viriviri/SpatialVideoSampleActivity.kt:745-750
 * k17 root cause: the video is a manually built [PanelSceneObject] whose mesh ignored the entity
 * `Scale` component, while the danmaku/backdrop overlays are standard panels that honoured it —
 * that asymmetry is why the canvas loaded at the wrong size.
```

5. **Layer ordering is not owned by the app** (experiment recorded as still open).

```kotlin
// app/src/main/java/com/m0e_n00b/viriviri/SpatialVideoSampleActivity.kt:795-797
 * If this is ignored too, then layer ordering is simply not ours to control and the display path
 * itself has to change — see Stage 3k in docs/research/stage-video-mesh-curvature.md.
```

6. **Route/Workbench coupling is hand-maintained and previously drifted.**

```kotlin
// app/src/main/java/com/m0e_n00b/viriviri/SpatialVideoSampleActivity.kt:2085-2089
// The reset used to happen only via `applyPlaybackCanvasSlots`, whose condition
// (`BROWSE !in slots && TRANSPORT in slots && selected != null`) cannot hold right after a
// dismissal: `PlaybackCanvasEvent.Dismiss` moves the canvas to QUIET_WATCH, whose visible
// slots are MEDIA_STAGE only. So the route stayed stale until the next summon flipped the
// canvas back to PLAYBACK -- which is the route switch the user saw on open.
```

7. **Anti-duplication note for danmaku render config** (an example of the duplication already having
   been removed once):

```kotlin
// app/src/main/java/com/m0e_n00b/viriviri/DanmakuOverlay.kt:22-25
 * Lane counts and durations are deliberately NOT declared here: the renderer reads them from the
 * canvas runtime's [DanmakuRenderConfig]. Keeping a second copy in the overlay let the renderer
 * silently diverge from the scheduler that admitted items into those lanes.
```

Also relevant, from the spatial-UI boundary rules this project already codifies
(`AGENTS.md`, "Spatial UI Boundaries"): *"A single user interaction must have one owner. Do not let
stage click listeners, outer-dismiss geometry, panel callbacks, and hover/touch fallback handlers
independently perform the same show/hide transition."* The audit above shows that rule is enforced for
the **canvas→workbench** transition (`onStagePrimaryAction` is the single entry, `:1980-2010`) but
**not** for the `PanelLayerAlpha` channel (three owners) or for the stage-size enum/float pair.

---

## UNVERIFIED items

| Item | Why unverified |
|---|---|
| Whether `ImmersiveWorkbenchState.content` is *intended* to remain as a shadow of `PlaybackCanvas`, or is a leftover | No design note found; `docs/ui-structure-and-terminology.md:229-236` presents all three layers as intentional, so intent cannot be settled from source. |
| Whether the `mode_panel` debug block (scale slider, reset buttons, danmaku Z) is intended to ship to users | The buttons are **not** `BuildConfig.DEBUG`-gated in code or XML, but the surrounding comment calls them debug telemetry; only the developer can resolve intent. |
| Whether `appliedStageScale`/`applied*Curvature` ever diverge from `ViriViriUiState` in practice | The `if (applied == x) return` guards make divergence *possible* by construction (`:2215, :2243`, and the acknowledged hazard at `:2583-2589`); no runtime observation was made in this audit. |
| Exact on-device behaviour of `PlaybackCanvasSize` label drift after a thumbstick scale change | Derived from code reading (`setPlaybackStageScale` never touches `playbackCanvasSize`); not reproduced on a device. |
| Whether `DanmakuMode` / `ImmersiveLayoutMode` / `PanelSlot.FOCUS` / `SpatialTheme.canvases` have any planned runtime wiring | Contract-layer declarations with no app consumer today (grep-verified); future intent is not in the source. |
| `MediaStageState`'s effect on anything visible | Its only consumer is `onEffect = { effect -> Log.d(...) }` (`SpatialVideoSampleActivity.kt:365`); no Spatial entity reads it. Marked as **no visible effect** rather than unverified, but the reducer's full effect set was not exhaustively enumerated. |
