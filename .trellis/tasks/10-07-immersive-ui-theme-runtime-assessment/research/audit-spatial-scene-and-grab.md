# Audit — Current Spatial Scene, Entity Hierarchy, Panel Registration and Grab

Scope: the `:app` module (`app/src/main/**`), the `:spatial-workbench-core` contracts it consumes, and the
Meta Spatial Editor scene data under `app/scenes/**`. `temp/**` (vendored Meta-Spatial-SDK-Samples) was
**excluded from every search**. No source file was modified; this document is the only artifact written.

---

## Verdict

The current immersive Workbench is **one movable Kotlin-built assembly**, not a themed scene. Exactly
**one** entity in the whole runtime comes from Meta Spatial Editor as a *panel*
(`WorkbenchCenterContent`, `app/scenes/Composition/Main.scene:30-51`); the other two scene nodes are a
mesh environment and an invisible dismiss slab, while **every panel that the user actually sees** —
transport, both rails, nav bar, centre content's runtime pose, keyboard, danmaku overlay, stage backdrop,
grab bar — is created in Kotlin and placed by hardcoded constants from
`WorkbenchLayoutConfig.DEFAULT` (`WorkbenchLayoutConfig.kt:21-110`, consumed only at
`SpatialVideoSampleActivity.kt:271`). There are **10 `PanelRegistration`s** (`SpatialVideoSampleActivity.kt:477-492`,
the 10th debug-gated) plus **one unregistered media panel** built by hand as a `PanelSceneObject`
(`:1202-1213`). The declarative theme contract exists (`spatial-workbench-core` `SpatialTheme` /
`SlotPlacement` with `offsetMeters` / `rotationDegrees` / `sizeMeters` / `anchor`) but
**no app-runtime code path reads a theme offset, rotation, anchor or size and writes it to a Spatial
`Transform`** — the only runtime consumer, `ImmersivePlaybackCanvasHost`, reads
`theme.presentationPolicies` and nothing else (`ImmersivePlaybackCanvasHost.kt:12,29` →
`PlaybackCanvasContracts.kt:71-87`); a repo-wide search of `app/src/main` for
`offsetMeters|placement(|SpatialAnchor|Meters3|ThemeLayout|sizeMeters` returns **zero matches**. Grab is
**exactly one group**: a single non-panel anchor carrying `IsdkBoxCollider` + `IsdkGrabbable`
(`:691-707`) with the stage root and the grab-bar visual as its `TransformParent` children
(`:717`, `:1051`); movement is the SDK's parent-child propagation, not a mirroring system.
`GrabPilotSystem` (the generic delta-mirror/follower implementation) is **dead code** — repo-wide it has
exactly one match, its own declaration (`GrabPilotSystem.kt:28`). Visibility is written from **two
independent state sources** (`ImmersiveWorkbenchReducer.modules()` and
`PlaybackCanvasReducer.visibleSlots()`) via one controller, but the same workbench show/hide transition
is *initiated* from at least six places (outer-dismiss hit layer, centre-panel blank space, hover/touch
fallback on every button, stage tap, Android-view transport overlay, canvas events), and the transport
panel has **two unrelated visibility notions** (Spatial `Visible`/`PanelLayerAlpha` vs the inner
Android `View.visibility`).

---

## A. Panel registration table (all `PanelRegistration`s)

Registration block: `SpatialVideoSampleActivity.kt:477-492`.

```kotlin
477:  override fun registerPanels(): List<PanelRegistration> {
478:    return mutableListOf(
479:        controlsPanelRegistration(),
480:        selectorPanelRegistration(),
481:        mrPanelRegistration(),
482:        modePanelRegistration(),
483:        centerContentPanelRegistration(),
484:        inputMethodPanelRegistration(),
485:        stageBackdropPanelRegistration(),
486:        danmakuOverlayPanelRegistration(),
487:        grabBarPanelRegistration(),
488:    )
489:        .apply {
490:          if (BuildConfig.DEBUG) add(wristDebugPanelRegistration())
491:        }
492:  }
```

`s = currentStageScale()` = `appliedStageScale ?: PlaybackCanvasSize.STANDARD.scale` (`:740`), clamped to
`[0.70, 2.00]` (`PlaybackCanvasSize.kt:15,24,26-27`); default `1.00` (`PlaybackCanvasSize.kt:9`).

| # | Registration id | Registration kind / content | Registration site | `shape` (metres, physical) | `display` (buffer units) | `style` | `input` |
|---|---|---|---|---|---|---|---|
| 1 | `R.id.controls_id` | `LayoutXMLPanelRegistration`, `R.layout.controls` | `:1331-1383` | `QuadShapeOptions(1.32f, 0.38f)` `:1337` | `DpDisplayOptions(460f, 132f, dpi = 600)` `:1338` | `PanelAppThemeTransparent` `:1339` | default (none passed) |
| 2 | `R.id.video_selector_panel` | `ActivityPanelRegistration`, `MoviePanel::class.java` | `:2837-2855` | `QuadShapeOptions(layout.railWidth=0.70f, layout.leftRailHeight=0.90f)` `:2845` | `DpDisplayOptions(361f, 464f, dpi = 800)` `:2846` | none | `PanelInputOptions(ButtonA or ButtonTriggerL or ButtonTriggerR)` `:2847-2851` |
| 3 | `R.id.mr_panel` | `IntentPanelRegistration`, `MRPanel::class.java` | `:2858-2874` | `QuadShapeOptions(1.24f, 0.30f)` `:2869` | `DpDisplayOptions(520f, 128f, dpi = 600)` `:2870` | none | default |
| 4 | `R.id.mode_panel` | `LayoutXMLPanelRegistration`, `R.layout.mode_panel` | `:1588-1755` | `QuadShapeOptions(layout.railWidth=0.70f, layout.leftRailHeight=0.90f)` `:1596` | `DpDisplayOptions(280f, 464f, dpi = 600)` `:1597` | `PanelAppThemeTransparent` `:1598` | default |
| 5 | `R.id.center_content_panel` | `ComposeViewPanelRegistration` → `ImmersiveCenterContentPanel` | `:1385-1409` | `QuadShapeOptions(layout.centerWidth=1.50f, layout.centerHeight=1.20f)` `:1404` | `DpDisplayOptions(768f, 615f, dpi = 512)` `:1405` | `PanelAppThemeTransparent` `:1406` | default |
| 6 | `R.id.input_method_panel` | `ComposeViewPanelRegistration` → `ImmersiveInputMethodPanel` | `:1411-1424` | `QuadShapeOptions(1.62f, 0.68f)` `:1419` | `DpDisplayOptions(832f, 348f, dpi = 512)` `:1420` | `PanelAppThemeTransparent` `:1421` | default |
| 7 | `R.id.stage_backdrop_panel` | `ComposeViewPanelRegistration` → `StageBackdrop` | `:1426-1435` | `stageOverlayPanelSettings(scaledStageWidth(), scaledStageHeight(), Flat)` → `QuadShapeOptions(1.60·s, 0.90·s)` `:1433`, `:1467-1477` | `DpDisplayOptions(1280f, 720f, dpi = 800)` `:1474` | `PanelAppThemeTransparent` `:1476` | `PanelInputOptions(0)` `:1475` |
| 8 | `R.id.danmaku_overlay_panel` | `ComposeViewPanelRegistration` → `DanmakuOverlay` | `:1437-1445` | same, with `appliedDanmakuCurvature` `:1443` | `DpDisplayOptions(1280f, 720f, dpi = 800)` `:1474` | `PanelAppThemeTransparent` `:1476` | `PanelInputOptions(0)` `:1475` |
| 9 | `R.id.grab_bar_panel` | `LayoutXMLPanelRegistration`, `R.layout.grab_bar` | `:1453-1465` | `QuadShapeOptions(GRAB_BAR_WIDTH_METERS=1.00f, GRAB_BAR_HEIGHT_METERS=0.08f)` `:1459` | `DpDisplayOptions(512f, 56f, dpi = 600)` `:1460` | `PanelAppThemeTransparent` `:1462` | `PanelInputOptions(0)` `:1461` |
| 10 | `R.id.wrist_debug_panel` (**DEBUG only**) | `LayoutXMLPanelRegistration`, `R.layout.wrist_debug_panel` | `:1560-1575` (registered `:490`) | `QuadShapeOptions(0.14f, 0.05f)` `:1566` | `DpDisplayOptions(224f, 80f, dpi = 1600)` `:1567` | `PanelAppThemeTransparent` `:1568` | default |

### A.1 Not a `PanelRegistration`: the media/video surface

`R.id.spatialized_video_panel` has **no** entry in `registerPanels()`. It is a hand-built
`PanelSceneObject` (`:1202-1213`) attached to the entity named by `res/values/ids.xml:6`:

```kotlin
1191:    val settings = MediaPanelSettings(
1194:        shape = videoShapeOptions(scaledStageWidth(), scaledStageHeight(), appliedVideoCurvature),
1196:            PixelDisplayOptions(
1197:                width = IMMERSIVE_VIDEO_OUTPUT_WIDTH,   // 1920
1198:                height = IMMERSIVE_VIDEO_OUTPUT_HEIGHT, // 1080
1200:        rendering = MediaPanelRenderOptions(stereoMode = StereoMode.None, zIndex = VIDEO_LAYER_Z_INDEX),
```

Shape is `QuadShapeOptions(w,h)` when flat, `CylinderShapeOptions(radius, w, h)` when curved
(`videoShapeOptions`, `:773-782`); `w/h` = `1.60·s × 0.90·s` (`scaledStageWidth/Height`, `:752-754`;
`MR_SCREEN_WIDTH = 16/10`, `MR_SCREEN_HEIGHT = 9/10`, `:2962-2963`). Display = 1920×1080 px
(`ImmersiveVideoOutputConfig.kt:5-6`). It is added to the scene object system manually instead of via a
`Panel` component (`:1288-1293`) because a `Panel` without a registration crashes
(`:1301-1302`).

### A.2 "units" and "HORIZONTAL / VERTICAL"

* `shape` is **metres** (`QuadShapeOptions(width, height)`), `display` is the Android buffer size
  (`DpDisplayOptions(width, height, dpi)` = dp + dpi, or `PixelDisplayOptions(width, height)` = px).
* There is **no HORIZONTAL/VERTICAL (or any orientation) parameter** on any panel option class in
  Spatial SDK 0.13.2. `javap` of the toolkit jar used by this project
  (`meta-spatial-sdk-toolkit-0.13.2.aar → classes.jar`) shows:
  `DpDisplayOptions{float width, float height, int dpi}`,
  `PixelDisplayOptions{int width, int height}`,
  `QuadShapeOptions{float width, float height}`,
  `CylinderShapeOptions{float radius, float width, float height}`,
  `UIPanelSettings{shape, display, rendering, style, input}`.
  A byte-level scan of every class in both `meta-spatial-sdk-0.13.2` and
  `meta-spatial-sdk-toolkit-0.13.2` for `HORIZONTAL|VERTICAL|Orientation` matched only
  `Pose`/`ControllerPose`/`ByPoseFilterOperation`/`PoseSortCriterion` — i.e. nothing panel-related.
  Panel orientation in 3D therefore comes **only** from the entity `Transform` quaternion.
* The only `orientation` attributes in the repo are Android `LinearLayout` attributes inside the 2D
  panel content: `res/layout/grab_bar.xml:7` (horizontal), `res/layout/controls.xml:8,15,128`,
  `res/layout/mode_panel.xml:14`, `res/layout/debug.xml:14`, `res/layout/window_2d.xml:6`. These do not
  affect panel layout in space.

---

## B. Entity inventory: scene-authored vs runtime-created

### B.1 Scene-authored (Meta Spatial Editor) — 3 top-level nodes + their sub-scene nodes

| Scene node | Declared in | Gleaned in Kotlin at | Carries |
|---|---|---|---|
| `MediaRoom` | `app/scenes/Composition/Main.scene:17-29` (`DocumentReference: projref:MediaRoom/Main.metaspatialobj`) | `SpatialVideoSampleActivity.kt:458` | mesh environment; runtime `Visible(false)` when MR (`:1102`, `:2882`) |
| `WorkbenchCenterContent` | `Main.scene:30-51` | `:460` | `com.meta.spatial.toolkit.Panel: panel: '@id/center_content_panel'` (`Main.scene:48-49`) + `PanelDimensions: {}` (`:50`) + `Visibility: visible: true` (`:46-47`) |
| `WorkbenchOuterDismiss` | `Main.scene:52-73` (`DocumentReference: projref:WorkbenchOuterDismiss/Main.metaspatialobj`) | `:461` | invisible dismiss slab; mesh `cube` (`WorkbenchOuterDismiss/Main.scene:11-32`), scene transform `scale 9,6,0.025`, `translation 0,1.25,2.2` (`Main.scene:62-70`) |

Sub-scene nodes (same scene files, loaded through the two `DocumentReference`s — **not** looked up by name
in Kotlin): `MediaRoom_geo`, `MediaRoom_plants_geo`, `MediaRoom_env_geo`
(`app/scenes/MediaRoom/Main.scene:12-69`, meshes `Mesh.095/096/097`) and `cube` / tag `cube1`
(`app/scenes/WorkbenchOuterDismiss/Main.scene:11-32`).

The lookup mechanism is the only scene-node access in the project (three calls, one block):

```kotlin
457:    loadGLXF { composition ->
458:      environmentGLXF = composition.getNodeByName("MediaRoom").entity
460:      centerContentEntity = composition.getNodeByName("WorkbenchCenterContent").entity
461:      outerDismissEntity = composition.getNodeByName("WorkbenchOuterDismiss").entity
```

Exported data, verified in `app/src/main/assets/scenes/Composition.glxf` (a JSON-format GLXF):
`"assets":[{"uri":"MediaRoom.gltf","name":"MediaRoom"},{"uri":"WorkbenchOuterDismiss.gltf",…}]`,
3 `nodes` (`MediaRoom`, `WorkbenchCenterContent`, `WorkbenchOuterDismiss`), with
`WorkbenchCenterContent` carrying
`com.meta.spatial.toolkit.Panel.panel = "@id/center_content_panel"` and
`PanelDimensions = [1.5, 0.8399999737739563]`. There are no scene nodes named
`media_stage_anchor`, `transport_anchor`, `system_toolbar_anchor`, `browse_anchor`, `context_anchor`,
`focus_anchor`, `action_sheet_anchor`, `pip_dock_anchor`, `shorts_*_anchor` — i.e. **none of the anchor
names referenced by `CinemaTheme` exist in the scene**
(`CinemaTheme.kt:121-192,205`; scene has 3 nodes).

### B.2 Runtime-created in Kotlin

| Entity | How created | Site |
|---|---|---|
| `gltfxEntity` (GLXF root that parents the scene nodes) | `Entity.create()` | `:1003` |
| `skydome` | `Entity.create(listOf(Mesh("mesh://skybox"), Material, Transform(Pose(0,0,0)), Visible(false)))` | `:600-611` |
| `workbenchRootEntity` = **grab anchor** | `Entity.create(listOf(Transform(...), IsdkBoxCollider(...), IsdkGrabbable()))` | `:691-707` |
| `grabBarEntity` = `R.id.grab_bar_panel` visual | `Entity(R.id.grab_bar_panel).setComponents(listOf(Panel(id), Transform(...), TransformParent(anchor)))` | `:711-719` |
| stage root `R.id.spatialized_video_panel` | `Entity(R.id.spatialized_video_panel).setComponents(listOf(SpatializedAudioPanel(), Transform(...), TransformParent(anchor)))` | `:1041-1053` |
| `videoSurfaceEntity` (render carrier) | `Entity.create(listOf(Transform(Pose(0,0,0)), TransformParent(stageRoot)))` | `:1170-1176` |
| `R.id.video_selector_panel` | `Entity(id).setComponents(listOf(Panel(id), Transform(...), TransformParent(stage)))` | `:1057-1070` |
| `R.id.controls_id` | same pattern | `:1071-1078` |
| `R.id.mr_panel` | same pattern | `:1079-1086` |
| `R.id.mode_panel` | same pattern | `:1089-1101` |
| `centerContentEntity` (scene node, **reparented + repositioned at runtime**) | `setComponent(TransformParent(...))` + `setComponent(Transform(...))` + `setComponent(PanelDimensions(...))` | `:619,624-626,631-633` (and again `:1087`) |
| `inputMethodPanelEntity` | `Entity.createPanelEntity(R.id.input_method_panel, Transform(...), TransformParent(stage), Visible(false))` | `:1481-1489` |
| `stageBackdropEntity` | `Entity.createPanelEntity(R.id.stage_backdrop_panel, Transform(...), TransformParent(stage), Visible(false))` | `:1508-1517` |
| `danmakuOverlayEntity` | `Entity.createPanelEntity(R.id.danmaku_overlay_panel, Transform(...), TransformParent(stage), Visible(true))` | `:1532-1540` |
| `wristDebugPanelEntity` (DEBUG only) | `Entity.createPanelEntity(R.id.wrist_debug_panel, Transform(Pose()), WristAttached(...), Visible(false))` | `:1579-1585` |

SDK note that makes "created in Kotlin" precise: in 0.13.2 `Entity(int)` is **only a handle**
(`javap`: `Entity(int)` → `Entity(EntityContext.getDataModel(), (long) id)`); the creation API is
`Entity.Companion.create(...)` / `Entity.createPanelEntity(...)` /
`DataModel.createEntity()`/`createUniqueEntity(long)`. For `R.id.spatialized_video_panel`,
`R.id.video_selector_panel`, `R.id.controls_id`, `R.id.mr_panel`, `R.id.mode_panel` and
`R.id.grab_bar_panel` the app never calls a create API — the entity is materialised by the first
`setComponents`/`setComponent` on that fixed id, which is exactly the `Entity(id) + Panel(id)` idiom the
SDK expects (the id doubles as the panel registration id; `PanelCreationSystem` bytecode contains the
guard string *"Please make sure you have set up a PanelRegistration before creating using a Panel
component with this key"*). The final materialisation step is not observable from the jar alone → see
§9.

Unused ids (declared, never referenced anywhere): `R.id.debug_panel` (`res/values/ids.xml:7`),
`R.id.workbench_root` (`:15`); `res/layout/debug.xml` is likewise unreferenced.

---

## C. Visibility writers per panel / slot

`SpatialPanelVisibilityController.setVisible(slot, entity, visible)` (`SpatialPanelVisibilityController.kt:20-55`)
is the only fade+`Visible` implementation. Its callers are `applyWorkbenchModules`
(`SpatialVideoSampleActivity.kt:2108-2140`) and `syncInputMethodPanelVisibility` (`:1493-1504`) and the
one-shot in `createStageBackdropPanel` (`:1520-1523`).

| Panel / entity | Slot passed | Writer(s) — file:line | Writes |
|---|---|---|---|
| `R.id.controls_id` | `TRANSPORT` (`:2144`) | `:2113-2123` only | `Visible` + `PanelLayerAlpha` |
| `R.id.video_selector_panel` | `CONTEXT` (`:2146`) | `:2115` only | idem |
| `R.id.mr_panel` | `SYSTEM_TOOLBAR` (`:2147`) | `:2113` only | idem |
| `R.id.mode_panel` | `CONTEXT` (`:2146`) | `:2116` only | idem |
| `R.id.center_content_panel` (= scene node `WorkbenchCenterContent`) | `BROWSE` (`:2127`) | **scene** `Main.scene:46-47` (`visible: true`) + `:2125-2131` | scene `Visibility`; then `Visible`+alpha |
| `R.id.stage_backdrop_panel` | `MEDIA_STAGE` | **two call sites**: `:1520-1523` (creation) and `:2132-2139`; created `Visible(false)` at `:1514` | `Visible` + alpha |
| `R.id.danmaku_overlay_panel` | — (absent from every module map) | **creation only**: `Visible(true)` `:1537` | `Visible` |
| `R.id.input_method_panel` | `ACTION_SHEET` (`:1500`) | `:1493-1504`, called from `:1490` and `:416`; created `Visible(false)` `:1487` | `Visible` + alpha |
| `R.id.grab_bar_panel` (visual) | — | `Visible(true)` `:720`; `PanelLayerAlpha(GRAB_BAR_IDLE_ALPHA)` `:722`; `GrabBarHoverSystem.kt:117` (alpha only) | `Visible` once, **alpha from 2 writers** |
| `R.id.wrist_debug_panel` | — | `Visible(false)` `:1584`; `WristAttachedSystem.kt:29`, `:35`, `:54` | `Visible` |
| stage root `R.id.spatialized_video_panel` | — | never writes `Visible`; `Hittable(MeshCollision.NoCollision)` `:1300`; `Transform` written `:998`, `:1138-1140`, `:2673` | transform only |
| `MediaRoom` (`environmentGLXF`) | — | `Visible(false)` `:1102`; `setMrMode` `:2882`, `:2885` | `Visible` |
| `skydome` | — | `Visible(false)` `:609`; `setMrMode` `:2883`, `:2886` | `Visible` |
| `WorkbenchOuterDismiss` | — | **no Kotlin writer**; scene `visible: true` (`Main.scene:71-72`); `Hittable()` added `:639` | scene `Visibility` |
| transport *content* (`controllerView`, i.e. `R.layout.controls` root `View`) | — | `applyTransportOverlayVisibility` `:1893-1899`; `animateControllerVisibility` `:1869-1891` (writes `View.visibility` + `alpha`, `:1873-1875`, `:1884`, `:1895-1898`); called from `:1380`, `:1856`, `:1902`, `:1917`, `:2003`, `:2007`, `:2081`, `:2823` | Android `View.visibility`/`alpha` — **a second, unrelated visibility channel for the same panel** |

Transition *initiators* (all funnel into the workbench/canvas state, i.e. each is a competing owner of the
same transition):

| Initiator | Site | Effect |
|---|---|---|
| Scene dismiss slab click | `attachOuterDismissInput` `:636-676`, `dismissWorkbenchFromCenterContent("outerDismiss")` `:669` | `PlaybackCanvasEvent.Dismiss` `:2080` + `animateControllerVisibility(false)` `:2081` |
| Centre-panel blank space | `onDismissWorkbench = { dismissWorkbenchFromCenterContent("centerPanelBlank") }` `:1396` → `RecommendationUi.kt:263-267` (gated to `SearchWorkspaceRoute.WORKBENCH_EMPTY`) | same function |
| Hover/touch fallback on every button/panel view | `setupHoverAndTouchListeners` `:1834-1867` (`:1840`, `:1844`, `:1855`, `:1860`) → `openPlaybackCanvasIfQuiet()` `:1906-1911` | `PlaybackCanvasEvent.PrimaryStageAction` when `canvas == QUIET_WATCH` |
| Stage tap / trigger | `onStagePrimaryAction()` `:1988-2010` (`:2005`) | `PrimaryStageAction`, or `showTransportOverlay()` if the workbench is already up |
| Debug controller summon | `AnalogWorkbenchSummonSystem(onSummon = ::summonWorkbenchFromController)` `:565-567` → `:2029-2032` | same as stage tap |
| Canvas events from app state | `:427`, `:436` (`dispatchPlaybackCanvas(OpenPlayback)`), `:1114` (`OpenBrowse`), `:2041/2049/2057` (`OpenBrowse`), `:2063` | drives `visibleSlots` |
| Route/visibility cross-checks | `dismissWorkbenchFromCenterContent` `:2094-2096`, `applyPlaybackCanvasSlots` `:2103-2105` (`openWorkbenchEmpty()`) | content-route rewrites |

Two *sources of truth* for slot visibility:

* `ImmersiveWorkbenchReducer.modules(state)` (`ImmersiveWorkbenchState.kt:73-97`) → `ImmersiveWorkbenchHost.dispatch`
  (`ImmersiveWorkbenchHost.kt:14-25`) → `applyWorkbenchModules` (`:2108-2140`).
* `PlaybackCanvasReducer.visibleSlots(state, theme)` (`PlaybackCanvasContracts.kt:71-87`) →
  `ImmersivePlaybackCanvasHost` (`ImmersivePlaybackCanvasHost.kt:28-33`) → `applyPlaybackCanvasSlots`
  (`:2099-2106`) → `ImmersiveWorkbenchHost.applyCanvasSlots` (`ImmersiveWorkbenchHost.kt:27-37`).

The code itself documents one behavioural mismatch caused by the pair: `:2085-2093` explains that after
`Dismiss` the canvas goes to `QUIET_WATCH` while the centre route stayed stale, which is why
`dismissWorkbenchFromCenterContent` now resets the route itself (`:2094-2096`) *and* `applyPlaybackCanvasSlots`
still calls `openWorkbenchEmpty()` (`:2103-2104`).

---

## 1. Every registered panel (detail)

See table A. Additional registrar-level facts:

* Panel ids live in `res/values/ids.xml:3-14`; the SDK uses the registration id as the entity id for
  panel lookup (`Entity(R.id.controls_id)`, `Entity(R.id.mr_panel)`, …).
* Only one scene-authored entity carries a `Panel` component: `WorkbenchCenterContent`
  (`Main.scene:48-50`, exported in `assets/scenes/Composition.glxf`).
* Per-panel `panelSetupWithRootView`/`composeViewCreator` wiring (transport buttons `:1342-1381`,
  nav/mode rail `:1601-1753`, wrist label `:1571-1573`) is 2D content and does not affect spatial layout.
* `grabBarPanelRegistration` carries `PanelInputOptions(0)` (`:1461`), i.e. buttons disabled; the comment
  at `:1447-1452` still describes the bar as an "indicator", which contradicts the live implementation
  documented in `:678-727` (see §5). The comment is stale, the code is authoritative.
* The `PanelDimensions` of the scene-authored centre panel are overridden at runtime
  (`:631-633`) because the export hardcodes the old 1.5 × 0.84 size (visible in
  `assets/scenes/Composition.glxf`).

## 2. Static vs runtime entities

See §B. Short form:

* **From Meta Spatial Editor (3 panels-or-meshes)**: `MediaRoom`, `WorkbenchCenterContent`,
  `WorkbenchOuterDismiss` — looked up by name at `:458,460,461`. Their sub-scene geometry nodes are never
  looked up individually.
* **Everything else is Kotlin**, including all nine visible Workbench surfaces (transport, both rails,
  nav bar, keyboard, danmaku overlay, stage backdrop, grab bar) and the stage root itself.
* Runtime manipulation of *static* nodes exists: the centre panel is reparented
  (`:619`), re-posed (`:624-626`) and re-sized (`:631-633`); the environment and dismiss nodes are only
  toggled for MR (`:1102`, `:2882-2886`).

## 3. Panel placement: where each pose comes from; does any theme/offset config reach a Transform?

### 3.1 Source per panel

| Panel | World/local pose source | Site |
|---|---|---|
| grab anchor (`workbenchRootEntity`) | Kotlin `Transform(initialPose * Pose(Vector3(0f, anchorWorldY, 2f)))` where `anchorWorldY = persistedStageY - currentBarOffsetY()` | `:689`, `:694` |
| stage root | Kotlin `Transform(Pose(Vector3(0f, currentStageAnchorOffsetY(), 0f), Quaternion(0,0,0)))` | `:1045-1050` |
| `videoSurfaceEntity` | Kotlin `Transform(Pose(Vector3(0f, 0f, videoSurfaceBaseLocalZ=0f)))`, then curvature offset | `:1173`, `:2655-2674` |
| `R.id.video_selector_panel` | Kotlin constants: `Vector3(-layout.railX, 0f, layout.railLocalZ)`, `Quaternion(0f, -layout.railYawDegrees, 0f)` | `:1060-1067` |
| `R.id.controls_id` | Kotlin constants: `Vector3(0, layout.transportLocalY, layout.transportLocalZ)`, pitch `layout.transportPitchDegrees` | `:1075` |
| `R.id.mr_panel` | Kotlin constants: `Vector3(0, layout.navLocalY, layout.navLocalZ)`, pitch `layout.navPitchDegrees` | `:1083` |
| `R.id.mode_panel` | Kotlin constants: `Vector3(layout.railX, 0f, layout.railLocalZ)`, yaw `layout.railYawDegrees` | `:1093-1098` |
| `R.id.center_content_panel` | **scene translation overridden** by Kotlin `Transform(Pose(Vector3(0f, layout.centerLocalY, layout.centerLocalZ)))` after `TransformParent` reparent | `:619-626`; scene value `Main.scene:41-44` (`0,0,-0.12`) |
| `R.id.input_method_panel` | Kotlin constants: `Vector3(0, layout.keyboardLocalY, layout.keyboardLocalZ)`, pitch `layout.keyboardPitchDegrees` | `:1485` |
| `R.id.stage_backdrop_panel` | Kotlin constant `stageBackdropBaseLocalZ = 0.01f` + curvature offset | `:1511`, `:175`, `:2663` |
| `R.id.danmaku_overlay_panel` | Kotlin `stageDanmakuBaseLocalZ` (candidate array, default `-0.01f`) + curvature offset | `:1535`, `:186-189`, `:2938`, `:2663` |
| `R.id.grab_bar_panel` (visual) | Kotlin `Transform(Pose(Vector3(0,0,0), Quaternion(0,0,0)))` relative to the anchor | `:716` |
| `R.id.wrist_debug_panel` | `WristAttached(position = Vector3(0f, 0.03f, 0.02f), faceUser = true)` driven per frame by `WristAttachedSystem` | `:1583`; `WristAttachedSystem.kt:39-55` |
| media/video panel | entity transform (above) + compositor layer shape (`videoShapeOptions`) | `:1194`, `:2517-2536` |

`layout` is bound once, to a hardcoded default with no loader:

```kotlin
270:  // Centralized Workbench spatial tuning; replace/bind for future settings presets.
271:  private val layout = WorkbenchLayoutConfig.DEFAULT
```

(`WorkbenchLayoutConfig.kt:104-110`: `DEFAULT = WorkbenchLayoutConfig()`, `STAGE_WORLD_Z = 2.0f`.)

### 3.2 Does any code path read a theme/offset config and apply it to a Spatial `Transform`? — **NO**

Evidence:

1. Repo-wide search over `app/src/main` for `offsetMeters|placement(|SpatialAnchor|Meters3|ThemeLayout|sizeMeters`
   → **no matches**. Those are exactly the geometry-bearing fields of the theme contract
   (`spatial-workbench-core/.../ThemeContracts.kt:64-73`: `SlotPlacement(visibility, anchor, offsetMeters,
   rotationDegrees, sizeMeters, shape, parentSlot, depthRelation)`;
   `SpatialTheme.placement()` at `:186-187`).
2. The only runtime consumer of `SpatialTheme` is `ImmersivePlaybackCanvasHost`:
   `ImmersivePlaybackCanvasHost.kt:12` (`theme: SpatialTheme = CinemaTheme.create()`) and `:29`
   (`PlaybackCanvasReducer.visibleSlots(state, theme)`); that reducer reads **only**
   `theme.presentationPolicies` (`PlaybackCanvasContracts.kt:82-86`). `visibleSlots` returns a
   `Set<PanelSlot>`, never a pose.
3. `CinemaTheme`'s placements are constructed (`CinemaTheme.kt:117-209`) and validated in tests only
   (`ThemeValidatorTest.kt:13` uses `theme.placement(...)`), never read in `app/`.
4. `CinemaTheme`'s anchor names (`media_stage_anchor`, `transport_anchor`, `system_toolbar_anchor`,
   `browse_anchor`, `context_anchor`, `focus_anchor`, `action_sheet_anchor`, `pip_dock_anchor`,
   `shorts_*`) do not exist in the scene (`Composition/Main.scene` has 3 nodes), so even a future
   consumer would resolve nothing.
5. What *does* reach a `Transform` from outside the file is only these, and none of them is a theme:
   * `WorkbenchLayoutConfig.DEFAULT` — a Kotlin `data class` instance (see §3.1 table).
   * the persisted stage height: `loadWorkbenchStageYOrDefault()` → `createWorkbenchAnchor(initialPose, persistedStageY)`
     (`:1026`, `:1040`, `:689`, `:694`) and `resetStageYToDefault()` → `applyStageWorldY(y)`
     (`:1126-1142`, `Transform(...)` written at `:1138-1140`);
   * the persisted per-layer curvature: `appState.playbackVideoCurvature` etc. (`:402-404`) →
   * `applyStageLayerCurvatureOffset(...)` writing the layer's local Z
     (`:2655-2674`, `pose.t = Vector3(0f, 0f, targetZ)` at `:2672`);
   * the *derived* stage-local Y from the content half-height: `updateGrabBarPose` writes
     `stageEntity.setComponent(Transform(pose))` (`:990-999`).
   Persisted **scale** deliberately does *not* reach a Transform: it is baked into panel shapes/meshes
   (`:742-754`, `:2213-2223`, comment at `:1325-1327`).

## 4. `TransformParent` / parent-child hierarchy

| Child | Parent | Site |
|---|---|---|
| stage root `R.id.spatialized_video_panel` | `workbenchRootEntity` (grab anchor) | `:1051` (`TransformParent(workbenchRootEntity ?: Entity.nullEntity())`) |
| `R.id.grab_bar_panel` visual | `workbenchRootEntity` (anchor) | `:717` |
| `videoSurfaceEntity` | stage root | `:1174` |
| `R.id.video_selector_panel` | stage root | `:1068` |
| `R.id.controls_id` | stage root | `:1076` |
| `R.id.mr_panel` | stage root | `:1084` |
| `R.id.mode_panel` | stage root | `:1099` |
| `centerContentEntity` (scene node) | stage root | `:619` (and `bindCenterContentPanel()` again at `:1087`) |
| `inputMethodPanelEntity` | stage root | `:1486` |
| `stageBackdropEntity` | stage root | `:1512` |
| `danmakuOverlayEntity` | stage root | `:1536` |

```kotlin
619:    centerContentEntity?.setComponent(TransformParent(Entity(R.id.spatialized_video_panel)))
...
1051:                  TransformParent(workbenchRootEntity ?: Entity.nullEntity()),
...
1071:      Entity(R.id.controls_id)
1072:          .setComponents(
1073:              listOf(
1074:                  Panel(R.id.controls_id),
1075:                  Transform(Pose(Vector3(0.0f, layout.transportLocalY, layout.transportLocalZ), Quaternion(layout.transportPitchDegrees, 0f, 0f))),
1076:                  TransformParent(Entity(R.id.spatialized_video_panel)),
```

**Is the whole Workbench a single subtree?** Effectively **yes for the movable assembly**, and it is a
two-level subtree:

```
gltfxEntity (Entity.create, :1003; identity — GLXF inflate root)
├─ MediaRoom                      (scene; Visible(false) in MR, :1102)
├─ WorkbenchCenterContent         (scene; reparented to stage root at :619)
└─ WorkbenchOuterDismiss          (scene; independent root of the dismiss slab)

workbenchRootEntity  (Entity.create, :691-707 — IsdkBoxCollider + IsdkGrabbable)
├─ R.id.grab_bar_panel            (:717)
└─ R.id.spatialized_video_panel   (:1051)              <-- the "stage root"
   ├─ videoSurfaceEntity          (:1174)
   ├─ R.id.video_selector_panel   (:1068)
   ├─ R.id.controls_id            (:1076)
   ├─ R.id.mr_panel               (:1084)
   ├─ R.id.mode_panel             (:1099)
   ├─ centerContentEntity         (:619)
   ├─ inputMethodPanelEntity      (:1486)
   ├─ stageBackdropEntity         (:1512)
   └─ danmakuOverlayEntity        (:1536)

independent roots (no TransformParent at all):
  skydome            (Entity.create, :600-611)
  wristDebugPanelEntity (Entity.createPanelEntity, :1579-1585) — driven by WristAttachedSystem
```

So the Workbench itself is **one** parent-child subtree; the dismiss slab, the environment, the skydome
and the wrist panel are **separate roots**. The locales that contradict the "static layout belongs in the
scene" rule are listed in §7. Manual world-pose resolution exists because `Transform` is local
(`worldPoseOf`, `:1968-1978`, depth guard `MAX_PARENT_DEPTH = 8`, `:2941`).

## 5. GRAB

### 5.a The grabbable handle and its components

The handle is the runtime-created anchor `workbenchRootEntity`, **not** a panel:

```kotlin
691:    val anchor =
692:        Entity.create(
693:            listOf(
694:                Transform(initialPose * Pose(Vector3(0f, anchorWorldY, 2f))),
695:                IsdkBoxCollider(
696:                    size =
697:                        Vector3(
698:                            GRAB_BAR_WIDTH_METERS,        // 1.0f
699:                            GRAB_BAR_HEIGHT_METERS,       // 0.08f
700:                            GRAB_BAR_HIT_DEPTH_METERS,    // 0.05f
701:                        ),
702:                    offset = Vector3(0f, 0f, GRAB_BAR_HIT_FORWARD_OFFSET),
703:                ),
704:                IsdkGrabbable(),
705:            )
706:        )
707:    workbenchRootEntity = anchor
```

Components on the handle: `Transform`, `IsdkBoxCollider`, `IsdkGrabbable` — **no** `IsdkPanelGrabHandle`
(imported but never instantiated: `:70`; only mentioned in comments `:680`, `:1312`), **no** toolkit
`Grabbable`, **no** `Panel`. `IsdkGrabbable()` at `:704` is the **only** instantiation of any grabbable
component in `app/src` (repo search for `IsdkGrabState|Grabbable|grabState|Isdk` → hits only in
`GrabPilotSystem.kt`, `VideoStageGrabPersistenceSystem.kt` and the lines above). The stage explicitly
carries none:

```kotlin
1311:    // Scheme A: the grab BAR is the single grab target (movable_group bound to the bar handle).
1312:    // The stage itself is intentionally NOT grabbable — removing IsdkGrabbable/IsdkPanelGrabHandle
```

The `.grab_bar_panel` visual is a plain child panel with no collider of its own; hover proximity is
distance-based, not a collider (`GrabBarHoverSystem.kt:55`, `PointerInfoSystem.nearestRayDistanceTo`
`:54-61`).

ISDK is live because `VRFeature` registers `IsdkSystems` (early systems, systems, components) and loads
`MetaSpatialSDKIsdk`; verified in the 0.13.2 bytecode (`VRFeature` fields/methods reference
`com.meta.spatial.isdk.IsdkSystems.earlySystemsToRegister/systemsToRegister/componentsToRegister` and
`System.loadLibrary("MetaSpatialSDKIsdk")`). `registerFeatures()` (`:334-343`) adds `VRFeature(this)` at
`:335` and does **not** add `IsdkFeature`; that is not an omission.

### 5.b Is `GrabPilotSystem` registered? — **NO. It is dead code.**

Repo-wide grep for `GrabPilotSystem` over `*.kt` (excluding `temp/`) returns exactly **one** hit — its own
declaration:

```
app\src\main\java\com\m0e_n00b\viriviri\GrabPilotSystem.kt
Line 28: internal class GrabPilotSystem(
```

There is no `GrabPilotSystem(` call site, no import of it elsewhere, no `systemManager.registerSystem(GrabPilotSystem(...))`.
It is `internal` and therefore also unreachable from another module. Verdict: **dead code** — its
generic API (`handle`, `followersProvider`, `restPoseProvider`, `mirrorRotation`, `onGrab*`) is
unused, including its delta application (`applyDeltaToTarget`, `:86-91`) and quaternion mirror
(`applyRotationToTarget`, `:94-101`).

### 5.c Which grab-related systems ARE registered

All in `onSceneReady()`:

```kotlin
526:    systemManager.registerSystem(SpatialAudioSystem(panner, this))
527:    componentManager.registerComponent<SpatializedAudioPanel>(SpatializedAudioPanel.Companion)
528:    componentManager.registerComponent<PanelLayerAlpha>(PanelLayerAlpha.Companion)
529:    componentManager.registerComponent<WristAttached>(WristAttached.Companion)
530:    systemManager.registerSystem(PanelLayerAlphaSystem(systemManager.findSystem()))
531:    systemManager.registerSystem(WristAttachedSystem())
532:    val pointerInfoSystem = PointerInfoSystem()
533:    systemManager.registerSystem(pointerInfoSystem)
534:    systemManager.registerSystem(
535:        GrabBarHoverSystem(
536:            pointerInfo = pointerInfoSystem,
537:            barEntity = Entity(R.id.grab_bar_panel),
538:            barAnchorProvider = { workbenchRootEntity },
...
548:    systemManager.registerSystem(
549:        AnalogMediaStageTuningSystem(
...
565:      systemManager.registerSystem(
566:          AnalogWorkbenchSummonSystem(onSummon = ::summonWorkbenchFromController)
567:      )
571:    systemManager.registerSystem(StageOverlayReshapeSystem { flushPendingStageOverlayReshape() })
...
575:    systemManager.registerSystem(
576:        VideoStageGrabPersistenceSystem(
577:            anchorProvider = { workbenchRootEntity },
578:            onGrabFinished = { anchorY ->
580:              val stageY = anchorY + currentStageAnchorOffsetY()
582:                ViriViriApplication.appState.persistWorkbenchStageY(stageY)
```

Complete registered-system list: `SpatialAudioSystem` `:526`; `PanelLayerAlphaSystem` `:530`;
`WristAttachedSystem` `:531`; `PointerInfoSystem` `:533`; `GrabBarHoverSystem` `:534-547`;
`AnalogMediaStageTuningSystem` `:548-561`; `AnalogWorkbenchSummonSystem` `:565-567` (DEBUG only);
`StageOverlayReshapeSystem` `:571`; `VideoStageGrabPersistenceSystem` `:575-586`. Plus whatever
`VRFeature`/`ComposeFeature` and (in DEBUG) `CastInputForwardFeature`, `HotReloadFeature`,
`OVRMetricsFeature`, `DataModelInspectorFeature` register (`:334-343`), and the ISDK-internal systems
inside `VRFeature`.

Grab-relevant ones: `GrabBarHoverSystem` (alpha only) and `VideoStageGrabPersistenceSystem`
(persist-on-release only). `AnalogMediaStageTuningSystem` handles trigger/thumbstick *tuning*
(scale/curvature) and the stage primary action — it is not a grabber.

### 5.d What happens on grab — exact follow mechanism

1. The SDK's ISDK grabber moves the **anchor only** (`workbenchRootEntity`). Nothing in this project
   writes the anchor's pose while grabbed: no system mirrors a delta, no `followersProvider` exists at
   runtime, and `VideoStageGrabPersistenceSystem` explicitly never writes:
   `VideoStageGrabPersistenceSystem.kt:16-18` — *"This system only OBSERVES the grab … It never writes the
   pose itself."*
2. Everything else follows through **engine-side `TransformParent` propagation**. The stage root is the
   anchor's child (`:1051`), the grab-bar visual is the anchor's child (`:717`), and all nine panels hang
   off the stage root (§4 table). The design intent is stated in the code:
   ```kotlin
   572:    // Scheme A: the ANCHOR is the grabbable entity; the stage hangs under it, so the SDK moves
   573:    // everything when the anchor is dragged — no mirroring system needed. Watch the anchor's
   574:    // grab state to persist the stage's world height on release.
   ```
3. On release, `VideoStageGrabPersistenceSystem` detects `Grabbed → NotGrabbed`
   (`VideoStageGrabPersistenceSystem.kt:31-43`) and calls back with the anchor's **local** `Transform.t.y`
   (`:39-40`). The activity converts it to a stage world Y by adding the local stage offset (`:580`:
   `anchorY + currentStageAnchorOffsetY()`) and persists it through the app state (`:582`,
   `ViriViriAppState.kt:898-900`, `AppPreferences.kt:81-86`, clamped `0.6…3.0 m`
   (`AppPreferences.kt:145-150`)). Only Y survives; X/Z/yaw are reset each launch
   (`:1022-1025`, `ViriViriAppState.kt:890-895`).
4. Vertical rest pose is not a separate system: the anchor is created directly at
   `persistedStageY - currentBarOffsetY()` (`:689`) and re-parked by `applyStageWorldY`
   (`:1138-1140`); `GrabPilotSystem`'s `restPoseProvider` has no live counterpart.
5. Which layers move the whole group: the anchor + its two direct children + (transitively) the stage
   root's nine children. **Not** moved: `WorkbenchOuterDismiss` (independent root; it keeps its authored
   world pose `0,1.25,2.2`, `Main.scene:66-69`), `MediaRoom`, `skydome`, `wristDebugPanelEntity`.

### 5.e How many independent grab groups exist today? — **exactly one**

* Only one grabbable entity exists: one `IsdkGrabbable()` (`:704`) and one `IsdkBoxCollider` (`:695`) in
  the whole `app/src`.
* That entity is stored in a single field (`workbenchRootEntity`, `:214`), consumed by exactly two
  systems (`:538`, `:577`).
* There is no registry, collection, list or loop over grabbables (`GrabPilotSystem`'s
  `followersProvider: () -> List<Entity>` — the only list-shaped grab API in the codebase — is dead).
* The stage root, all overlays and the grab-bar visual are deliberately not grabbable
  (`:1311-1314`; overlays get `MeshCollision.NoCollision` at `:1517`, `:1540`; the moving surface gets
  `Hittable(MeshCollision.NoCollision)` at `:1300`).

## 6. Visibility: mechanisms and owners

Mechanisms in use today (one example line each):

| Mechanism | Example | Notes |
|---|---|---|
| `Visible(false/true)` component toggled directly | `:720` `visual.setComponent(Visible(true))`; `:1584` `Visible(false)` | also removes the entity from hit testing (`:640-645`) |
| `Visible` + animated `PanelLayerAlpha` via controller | `SpatialPanelVisibilityController.kt:28-31,43,46`; called from `:2119-2138` | the only fade path for panel slots |
| `PanelLayerAlpha` alone (no `Visible`) | `GrabBarHoverSystem.kt:117`; `:722` | grab bar only |
| Android `View.visibility`/`alpha` inside a panel | `:1895-1898`, `:1873-1875` | transport content only |
| Environmental `Visible` toggling for MR | `:2882-2886` | `MediaRoom`, `skydome` |
| Per-frame `Visible` from a system | `WristAttachedSystem.kt:29,35,54` | wrist debug panel |

No code in `app/src` hides anything by moving it far away, and no panel entity is destroyed on hide
(destroy only happens for the wrist panel in `onDestroy`, `:1809-1810`). Panel entities are **created
once** and only re-shaped/re-painted afterwards (`Entity.createPanelEntity`, `:1482/1509/1533/1580`).

Owners / conflicts:

* **Two visibility authorities** (`modules()` vs `visibleSlots()`, §C). `applyWorkbenchModules` is only
  called when `modules` changes or on `ImmersiveWorkbenchHost.dispatch`; `applyPlaybackCanvasSlots`
  drives that dispatch (`:2101`, `ImmersiveWorkbenchHost.kt:27-37`).
* **`stage_backdrop_panel` has two controller call sites** (`:1522`, `:2134`) with the same slot; the
  first runs at creation, the second on every module change. Not contradictory today, but two writers.
* **`center_content_panel` has two writers** with different mechanisms: the scene-authored
  `Visibility: visible: true` (`Main.scene:46-47`) and the controller (`:2126`). Any scene edit to that
  flag competes with runtime intent.
* **`input_method_panel`** is written by `syncInputMethodPanelVisibility` from two triggers
  (`:1490` creation, `:416` state collector) — same owner, two cadences.
* **Transport has two unrelated visibility channels**: the panel slot (`TRANSPORT` via
  `applyWorkbenchModules`) and the inner Android view (`controllerView`), whose visibility is written by
  `animateControllerVisibility`/`applyTransportOverlayVisibility` from **eight** call sites
  (`:1380`, `:1856`, `:1902`, `:1917`, `:2003`, `:2007`, `:2081`, `:2823`). The hover/touch fallback on
  every button (`:1840/1844/1855/1860`) makes a *content* view the entry point into a *canvas* transition,
  and the stage tap (`:2005`) plus the A-button summon (`:2031`) inject the same canvas event from two
  more places; the outer dismiss slab (`:669`) and the centre-panel blank space (`:1396`) inject
  `Dismiss` independently (both routed through `dismissWorkbenchFromCenterContent`, `:2076-2097`). That is
  six initiation paths for one show/hide transition — the AGENTS.md "single interaction, one owner" rule
  is not satisfied.
* `danmaku_overlay_panel` has exactly one visibility write (creation, `:1537`) and is absent from every
  controller map — it can never be hidden through the workbench module path.

## 7. Runtime creation of Spatial Entities for STATIC layout (rule violations)

Per the project rule (AGENTS.md → Meta Spatial SDK: static, fixed-position scene objects belong in
`.metaspatial`, authored via mse-agent), the following runtime constructions are **static layout** and
therefore violations. All of them are per-launch, single-instance, fixed-relative-position entities
(no runtime count, no dynamic data):

| # | Entity | Site | Why it is static-layout code |
|---|---|---|---|
| 1 | grab anchor (`workbenchRootEntity`) with `Transform` + `IsdkBoxCollider` + `IsdkGrabbable` | `:691-707` | fixed relative pose rebuilt every launch; the only "dynamic" part is the persisted Y |
| 2 | stage root `R.id.spatialized_video_panel` (+ its `Transform`/parent) | `:1041-1053` | fixed local offset, always the same entity |
| 3 | `R.id.video_selector_panel` pose/parent | `:1057-1070` | hardcoded rail pose from `WorkbenchLayoutConfig` |
| 4 | `R.id.controls_id` pose/parent | `:1071-1078` | hardcoded transport pose |
| 5 | `R.id.mr_panel` pose/parent | `:1079-1086` | hardcoded nav pose |
| 6 | `R.id.mode_panel` pose/parent | `:1089-1101` | hardcoded rail pose |
| 7 | `videoSurfaceEntity` | `:1170-1176` | fixed 0,0,0 carrier with a static parent |
| 8 | grab-bar visual pose/parent | `:713-719` | fixed 0,0,0 relative pose |
| 9 | `R.id.stage_backdrop_panel` | `:1508-1517` | constant local Z |
| 10 | `R.id.danmaku_overlay_panel` | `:1532-1540` | constant local Z (debug-cycled through a 4-value array) |
| 11 | `R.id.input_method_panel` | `:1481-1489` | hardcoded keyboard pose |
| 12 | `R.id.wrist_debug_panel` | `:1579-1585` | DEBUG; attached pose is a component constant |
| 13 | `skydome` | `:600-611` | fixed pose, `Visible(false)` for the app's lifetime |
| 14 | `gltfxEntity` | `:1003` | fixed identity GLXF root |

Runtime-only entities (legitimately dynamic, listed for contrast): none of the above. The project has no
per-video / per-item entity spawn; danmaku is drawn inside one Compose panel (`danmakuOverlayEntity`),
not as entities. `PlaybackStageCurvature` and stage scale are *properties* of these static entities, not
new entities.

The scene-authored panel counter-example is `WorkbenchCenterContent` (`Main.scene:30-51`), which shows
what the intended path looks like — and even it is reparented and re-posed at runtime (`:619-633`).

## 8. Custom ECS components

Schema: `app/src/main/components/spatial_video_sample_components.xml`
(package `com.m0e_n00b.viriviri`, schema `spatial-sdk/0.13.2`, referenced by
`app/scenes/config.json:2-5`). All three declared components are registered in `onSceneReady`
(`:527-529`).

| XML component (declared at) | Fields | Declared in Kotlin | Queried/written in Kotlin | Registered |
|---|---|---|---|---|
| `SpatializedAudioPanel` (`xml:7`) | marker only | `:1044` `SpatializedAudioPanel()` added to the stage root | `SpatialAudioSystem.kt:33` `Query.where { has(SpatializedAudioPanel.id, Transform.id) }` | `:527` |
| `PanelLayerAlpha` (`xml:8-10`) | `layerAlpha: Float = 1.0f` | `:722` `PanelLayerAlpha(GRAB_BAR_IDLE_ALPHA)` | `SpatialPanelVisibilityController.kt:22,30,43`; `GrabBarHoverSystem.kt:117`; `PanelLayerAlphaSystem.kt:19-24` | `:528` |
| `WristAttached` (`xml:11-15`) | `position: Vector3`, `rotation: Vector3`, `faceUser: Boolean = true` | `:1583` `WristAttached(position = Vector3(0f, 0.03f, 0.02f), faceUser = true)` | `WristAttachedSystem.kt:40,42,43,49,64` | `:529` |

All three are used; none is dead. The declaration's own contract notes are in
`app/src/main/components/README.md:10-19`. No other XML schema exists in `app/src/main/components/`
(only that file plus the README).

---

## 9. Not provable from source (UNVERIFIED)

1. **Effective world pose of the two scene sub-assets.** `inflateGLXF(..., rootEntity = gltfxEntity)`
   (`:1006-1009`) parents the scene nodes under a runtime-created, component-less root; the resulting
   local/world transforms of `MediaRoom` / `WorkbenchCenterContent` / `WorkbenchOuterDismiss` (and how
   `com.meta.spatial.toolkit.PanelDimensions` is derived from `SceneNode.scale`) are produced by the SDK,
   not visible in this source tree. The scene values are known (`Main.scene:35-45,60-70`); the composed
   world pose is not.
2. **Implicit entity materialisation.** That `Entity(R.id.<unregistered-id>).setComponents(...)` creates
   the data-model entity (rather than requiring a prior `Entity.create`) is inferred from
   (a) `Entity(int)` being a handle-only constructor in 0.13.2 (`javap`) and (b) the absence of any other
   creation site for `R.id.spatialized_video_panel`. It is not directly asserted by any document in the
   repo.
3. **That `TransformParent` propagation is what physically moves the children during an ISDK grab.**
   The mechanism is asserted by the code comments (`:572-574`, `:1938-1942`) and is consistent with
   `Transform` holding a local pose (`worldPoseOf`, `:1968-1978`), but no source line performs or verifies
   the propagation — it happens in the SDK/native layer. No on-device verification was performed in this
   audit.
4. **Whether `IsdkBoxCollider` + `IsdkGrabbable` requires a same-entity `IsdkPanelDimensions`/collider
   registration beyond what is written.** Not decidable from this source tree.
5. **The behaviour of `PanelInputOptions(0)` on the raycast collider.** The code comments assert it does
   not disable the collider (`:1516`), which is a device observation, not a source fact.
6. **`theme.presentationPolicies` effect on panel count.** `visibleSlots` adds every `PERSISTENT` slot
   (`PlaybackCanvasContracts.kt:82-86`; `CinemaTheme.kt:103,105` → `MEDIA_STAGE`, `SYSTEM_TOOLBAR`), but
   whether that ever changes `applyWorkbenchModules`' outcome is not provable without running the app.

## 10. Conflict / finding summary (one line each)

1. Theme geometry contract (`offsetMeters`, `rotationDegrees`, `anchor`, `sizeMeters`) is **zero-consumed**
   in `app/`.
2. All nine visible Workbench panels are Kotlin-created static layout (rule violation, §7).
3. `GrabPilotSystem` is dead code; the only live grab mechanism is a single `IsdkGrabbable` anchor + SDK
   parent propagation.
4. `GrabPilotSystem`'s doc comment (`GrabPilotSystem.kt:15-19`) still describes "future themes: bind
   different handles to different … GROUPS" as if wired — it is not.
5. `grabBarPanelRegistration`'s KDoc (`:1447-1452`) still describes the bar as non-grabbable
   "ISDK grab handle already makes the stage edges grabbable", which is the *pre-Scheme-A* state
   contradicted by `:678-727` and `:1311-1314`.
6. `IsdkPanelGrabHandle` is imported (`:70`) but never used.
7. Two slot-visibility sources (`modules()` / `visibleSlots()`) with a documented behavioural mismatch
   (`:2085-2093`).
8. Six initiation paths for one workbench show/hide transition; transport additionally has an Android-view
   visibility channel owned by eight call sites.
9. `danmaku_overlay_panel` is invisible to every visibility owner (creation-only write).
10. `R.id.debug_panel`, `R.id.workbench_root` and `res/layout/debug.xml` are declared but unused.
