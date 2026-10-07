package com.m0e_n00b.spatialworkbench.core

object CinemaTheme {
  fun create(): SpatialTheme {
    val componentGroup = DefaultCinemaPlaybackCanvasGroup.create()
    return SpatialTheme(
        id = "cinema",
        displayName = "Cinema",
        sceneReference = "themes/cinema/scene/Main.metaspatial",
        layouts =
            mapOf(
                ImmersiveLayoutMode.WATCH to watchLayout(),
                ImmersiveLayoutMode.FOCUS to focusLayout(),
                ImmersiveLayoutMode.EDIT to watchLayout(),
                ImmersiveLayoutMode.SHORTS to shortsLayout(),
            ),
        toolbarModules =
            listOf(
                ToolbarModule("clock", SystemToolbarModule.CLOCK),
                ToolbarModule("battery", SystemToolbarModule.BATTERY),
                ToolbarModule("enter2d", SystemToolbarModule.ENTER_2D),
                ToolbarModule("passthrough", SystemToolbarModule.PASSTHROUGH),
                ToolbarModule("more", SystemToolbarModule.MORE_ACTIONS),
            ),
        rootComponent =
            componentGroup.root.copy(
                namedSlots =
                    mapOf(
                        "browse" to listOf(ComponentNode("browse-panel", ComponentKind.PANEL)),
                        "context" to
                            listOf(
                                ComponentNode(
                                    id = "context-tabs",
                                    kind = ComponentKind.TAB_BAR,
                                    dataBindings = mapOf("video" to "currentVideo"),
                                    children =
                                        listOf(
                                            ComponentNode("playlist-list", ComponentKind.SEMANTIC_MODULE),
                                            ComponentNode("related-list", ComponentKind.SEMANTIC_MODULE),
                                            ComponentNode("video-details", ComponentKind.SEMANTIC_MODULE),
                                        ),
                                )
                            ),
                    ),
            ),
        componentGroups = listOf(componentGroup.asComponentGroup()),
        canvases =
            listOf(
                WorkbenchCanvas(
                    id = "cinema-watch-quiet",
                    mode = ImmersiveLayoutMode.WATCH,
                    visibleSlots = setOf(PanelSlot.MEDIA_STAGE),
                    visibleComponentIds = setOf("system-status-strip", "content-navigation-slot", "media-stage"),
                ),
                WorkbenchCanvas(
                    id = "cinema-watch-controls",
                    mode = ImmersiveLayoutMode.WATCH,
                    visibleSlots = setOf(PanelSlot.MEDIA_STAGE, PanelSlot.SYSTEM_TOOLBAR, PanelSlot.TRANSPORT),
                    visibleComponentIds =
                        setOf(
                            "system-status-strip",
                            "content-navigation-slot",
                            "media-stage",
                            "watch-title",
                            "transport-action-strip",
                            "seek-timeline",
                            "playback-config-popup",
                            "grab-handle",
                        ),
                    overflowPolicy = CanvasOverflowPolicy.VISIBLE,
                    preservesHitTesting = true,
                ),
                WorkbenchCanvas(
                    id = "cinema-shorts-quiet",
                    mode = ImmersiveLayoutMode.SHORTS,
                    visibleSlots = setOf(PanelSlot.MEDIA_STAGE, PanelSlot.TRANSPORT),
                    visibleComponentIds = setOf("media-stage", "shorts-quiet-action-strip"),
                ),
                WorkbenchCanvas(
                    id = "cinema-shorts-controls",
                    mode = ImmersiveLayoutMode.SHORTS,
                    visibleSlots =
                        setOf(
                            PanelSlot.MEDIA_STAGE,
                            PanelSlot.SHORTS_DETAILS,
                            PanelSlot.SHORTS_COMMENTS,
                            PanelSlot.TRANSPORT,
                        ),
                    visibleComponentIds =
                        setOf(
                            "shorts-details-rail",
                            "media-stage",
                            "shorts-stage-controls",
                            "shorts-comments-rail",
                            "grab-handle",
                        ),
                    overflowPolicy = CanvasOverflowPolicy.VISIBLE,
                    preservesHitTesting = true,
                ),
            ),
        presentationPolicies =
            mapOf(
                PanelSlot.MEDIA_STAGE to PanelPresentationPolicy.PERSISTENT,
                PanelSlot.TRANSPORT to PanelPresentationPolicy.AUTO_FADE,
                PanelSlot.SYSTEM_TOOLBAR to PanelPresentationPolicy.PERSISTENT,
                PanelSlot.BROWSE to PanelPresentationPolicy.ON_DEMAND,
                PanelSlot.CONTEXT to PanelPresentationPolicy.ON_DEMAND,
                PanelSlot.FOCUS to PanelPresentationPolicy.ON_DEMAND,
                PanelSlot.ACTION_SHEET to PanelPresentationPolicy.TRANSIENT,
                PanelSlot.SHORTS_DETAILS to PanelPresentationPolicy.AUTO_FADE,
                PanelSlot.SHORTS_COMMENTS to PanelPresentationPolicy.AUTO_FADE,
            ),
        palette = CinemaPalette.DARK,
        schemaVersion = ThemeSchema.CURRENT_SCHEMA_VERSION,
        // Declared, not verified: none of these anchor nodes exists in `app/scenes` today
        // (design §3.2). They are the Phase 2.1 scene authorization work list, and an injected
        // empty SceneAnchorCatalog correctly reports every one of them as MissingSceneAnchor.
        requiredAnchors = REQUIRED_ANCHORS,
        panels = panels(),
        slotBindings = slotBindings(),
        grabGroups = listOf(DefaultWorkbenchGrabGroup.create()),
        danmakuGroups = listOf(defaultDanmakuGroup()),
        danmakuOcclusionDomains = listOf(DanmakuOcclusionDomain("stageDomain", setOf("mainDanmaku"))),
    )
  }

  /** Scene anchors this theme depends on (design §3.2). */
  val REQUIRED_ANCHORS: Set<String> =
      linkedSetOf(
          "media_stage_anchor",
          "transport_anchor",
          "system_toolbar_anchor",
          "browse_anchor",
          "context_anchor",
          "focus_anchor",
          "action_sheet_anchor",
          "pip_dock_anchor",
          "shorts_media_stage_anchor",
          "shorts_transport_anchor",
          "shorts_details_anchor",
          "shorts_comments_anchor",
          "focus_content_top_right",
      )

  /** The single danmaku render target the app registers today (`R.id.danmaku_overlay_panel`). */
  const val DEFAULT_DANMAKU_SURFACE_ID: String = "danmaku_overlay_panel"

  private fun panels(): Map<PanelId, PanelSpec> {
    val all = ThemeableGeometry.entries.toSet()
    val scenePanelGeometry =
        setOf(
            ThemeableGeometry.SIZE_METERS,
            ThemeableGeometry.SHAPE,
            ThemeableGeometry.OFFSET_METERS,
            ThemeableGeometry.ROTATION_DEGREES,
            ThemeableGeometry.Z_ORDER,
        )
    return listOf(
            panel(
                id = "mediaStage",
                panelClass = PanelClass.MEDIA_SURFACE,
                component = "mediaStage",
                placement = scenePlacement("media_stage_anchor", 1.6f, 0.9f),
                // Type C: only size and shape are themeable; compositor order and the video
                // Surface stay with the host adapter (design §5.6).
                themeableGeometry = setOf(ThemeableGeometry.SIZE_METERS, ThemeableGeometry.SHAPE),
            ),
            panel(
                id = "transport",
                panelClass = PanelClass.UI_PANEL,
                component = "transport",
                placement =
                    parentedPlacement(
                        size = PanelSizeMeters(0.9f, 0.24f),
                        depthRelation = SpatialDepthRelation.FRONT_OF_PARENT,
                    ),
                zOrder = 10,
                overflow = CanvasOverflowPolicy.VISIBLE,
                themeableGeometry = all,
                parentPanelId = PanelId("mediaStage"),
                parentAttach = AttachPoint.BOTTOM_CENTER,
            ),
            panel(
                id = "centerContent",
                panelClass = PanelClass.SCENE_PANEL,
                component = "centerContent",
                placement =
                    parentedPlacement(
                        offset = Meters3(z = -0.52f),
                        size = PanelSizeMeters(1.5f, 1.2f),
                        depthRelation = SpatialDepthRelation.FRONT_OF_PARENT,
                    ),
                zOrder = 5,
                themeableGeometry = scenePanelGeometry,
                parentPanelId = PanelId("mediaStage"),
                parentAttach = AttachPoint.CENTER,
            ),
            panel(
                id = "leftDetailRail",
                panelClass = PanelClass.UI_PANEL,
                component = "detailRail",
                placement =
                    scenePlacement(
                        anchor = "browse_anchor",
                        width = 0.70f,
                        height = 0.90f,
                        rotation = EulerDegrees(yaw = -45f),
                    ),
                zOrder = 20,
                overflow = CanvasOverflowPolicy.VISIBLE,
                themeableGeometry = all,
            ),
            panel(
                id = "rightContextRail",
                panelClass = PanelClass.UI_PANEL,
                component = "contextRail",
                placement =
                    scenePlacement(
                        anchor = "context_anchor",
                        width = 0.70f,
                        height = 0.90f,
                        rotation = EulerDegrees(yaw = 45f),
                    ),
                zOrder = 20,
                overflow = CanvasOverflowPolicy.VISIBLE,
                themeableGeometry = all,
            ),
            panel(
                id = "systemToolbar",
                panelClass = PanelClass.UI_PANEL,
                component = "systemToolbar",
                placement = scenePlacement("system_toolbar_anchor", 0.48f, 0.16f),
                zOrder = 30,
                themeableGeometry = all,
            ),
            panel(
                id = "inputConsole",
                panelClass = PanelClass.UI_PANEL,
                component = "inputConsole",
                placement = scenePlacement("action_sheet_anchor", 0.58f, 0.42f),
                zOrder = 40,
                themeableGeometry = all,
            ),
            panel(
                id = "focusPanel",
                panelClass = PanelClass.UI_PANEL,
                component = "focusPanel",
                placement = scenePlacement("focus_anchor", 1.5f, 1.0f),
                zOrder = 15,
                themeableGeometry = all,
            ),
            panel(
                id = "shortsDetailsRail",
                panelClass = PanelClass.UI_PANEL,
                component = "shortsDetailsRail",
                placement = scenePlacement("shorts_details_anchor", 0.68f, 1.1f),
                zOrder = 20,
                themeableGeometry = all,
            ),
            panel(
                id = "shortsCommentsRail",
                panelClass = PanelClass.UI_PANEL,
                component = "shortsCommentsRail",
                placement = scenePlacement("shorts_comments_anchor", 0.68f, 1.1f),
                zOrder = 20,
                themeableGeometry = all,
            ),
        )
        .associateBy(PanelSpec::id)
  }

  /**
   * The existing product really does bind `PanelSlot.CONTEXT` to two rails (left detail rail and
   * right context rail), so the slot-ownership rule cannot be "one slot, one panel". Both claims are
   * declared `MIRRORED` in one mirror group; without that declaration the pair is rejected (R7.2/A8).
   */
  private fun slotBindings(): List<SlotBinding> =
      listOf(
          SlotBinding(PanelSlot.MEDIA_STAGE, PanelId("mediaStage")),
          SlotBinding(PanelSlot.TRANSPORT, PanelId("transport")),
          SlotBinding(PanelSlot.BROWSE, PanelId("centerContent")),
          SlotBinding(PanelSlot.SYSTEM_TOOLBAR, PanelId("systemToolbar")),
          SlotBinding(PanelSlot.ACTION_SHEET, PanelId("inputConsole")),
          SlotBinding(PanelSlot.FOCUS, PanelId("focusPanel")),
          SlotBinding(PanelSlot.SHORTS_DETAILS, PanelId("shortsDetailsRail")),
          SlotBinding(PanelSlot.SHORTS_COMMENTS, PanelId("shortsCommentsRail")),
          SlotBinding(
              PanelSlot.CONTEXT,
              PanelId("leftDetailRail"),
              SlotSharePolicy.MIRRORED,
              "rails",
          ),
          SlotBinding(
              PanelSlot.CONTEXT,
              PanelId("rightContextRail"),
              SlotSharePolicy.MIRRORED,
              "rails",
          ),
      )

  private fun panel(
      id: String,
      panelClass: PanelClass,
      component: String,
      placement: SlotPlacement,
      zOrder: Int = 0,
      hitTest: PanelHitTest = panelClass.defaultHitTest,
      overflow: CanvasOverflowPolicy = panelClass.defaultOverflow,
      themeableGeometry: Set<ThemeableGeometry> = emptySet(),
      parentPanelId: PanelId? = null,
      parentAttach: AttachPoint? = null,
  ): PanelSpec =
      PanelSpec(
          id = PanelId(id),
          panelClass = panelClass,
          componentId = ComponentId(component),
          placement = placement,
          zOrder = zOrder,
          hitTest = hitTest,
          overflow = overflow,
          themeableGeometry = themeableGeometry,
          parentPanelId = parentPanelId,
          parentAttach = parentAttach,
      )

  private fun scenePlacement(
      anchor: String,
      width: Float,
      height: Float,
      rotation: EulerDegrees = EulerDegrees(),
  ): SlotPlacement =
      SlotPlacement(
          visibility = PanelVisibility.VISIBLE,
          anchor = SpatialAnchor.SceneAnchor(anchor),
          rotationDegrees = rotation,
          sizeMeters = PanelSizeMeters(width, height),
      )

  private fun parentedPlacement(
      size: PanelSizeMeters,
      depthRelation: SpatialDepthRelation = SpatialDepthRelation.ROOT,
      offset: Meters3 = Meters3(),
      rotation: EulerDegrees = EulerDegrees(),
  ): SlotPlacement =
      SlotPlacement(
          visibility = PanelVisibility.VISIBLE,
          anchor = null,
          offsetMeters = offset,
          rotationDegrees = rotation,
          sizeMeters = size,
          depthRelation = depthRelation,
      )

  /** One group, one layer, one surface: the allocation domain equals today's single overlay panel. */
  private fun defaultDanmakuGroup(): DanmakuSurfaceGroup =
      DanmakuSurfaceGroup(
          id = "mainDanmaku",
          laneSets =
              listOf(
                  DanmakuLaneSetSpec(
                      id = "scrollRtl",
                      laneFamily = DanmakuLaneFamily.SCROLLING,
                      emissionDirection = DanmakuEmissionDirection.RIGHT_TO_LEFT,
                      laneCount = 12,
                  ),
                  DanmakuLaneSetSpec(
                      id = "topFixed",
                      laneFamily = DanmakuLaneFamily.TOP_FIXED,
                      laneCount = 3,
                  ),
                  DanmakuLaneSetSpec(
                      id = "bottomFixed",
                      laneFamily = DanmakuLaneFamily.BOTTOM_FIXED,
                      laneCount = 3,
                  ),
              ),
          layers =
              listOf(
                  DanmakuDepthLayer(
                      id = "danmakuL1",
                      surfaceIds = setOf(DEFAULT_DANMAKU_SURFACE_ID),
                      depthOffsetMeters = -0.01f,
                      maxActiveItems = 240,
                  )
              ),
          laneStateSharing = LaneStateSharing.SHARED,
          maxActiveItems = 240,
          occlusionDomainId = "stageDomain",
      )

  private fun watchLayout() =
      ThemeLayout(
          placements =
              mapOf(
                  PanelSlot.MEDIA_STAGE to placement("media_stage_anchor", 1.6f, 0.9f),
                  PanelSlot.TRANSPORT to
                      placement(
                          "transport_anchor",
                          0.9f,
                          0.24f,
                          parentSlot = PanelSlot.MEDIA_STAGE,
                          depthRelation = SpatialDepthRelation.FRONT_OF_PARENT,
                      ),
                  PanelSlot.SYSTEM_TOOLBAR to placement("system_toolbar_anchor", 0.48f, 0.16f),
                  PanelSlot.BROWSE to placement("browse_anchor", 0.78f, 1.18f),
                  PanelSlot.CONTEXT to placement("context_anchor", 0.72f, 1.18f),
                  PanelSlot.FOCUS to hiddenPlacement("focus_anchor", 1.5f, 1f),
                  PanelSlot.ACTION_SHEET to hiddenPlacement("action_sheet_anchor", 0.58f, 0.42f),
              )
      )

  private fun focusLayout() =
      ThemeLayout(
          placements =
              mapOf(
                  PanelSlot.MEDIA_STAGE to placement("pip_dock_anchor", 0.62f, 0.35f),
                  PanelSlot.TRANSPORT to
                      placement(
                          "transport_anchor",
                          0.9f,
                          0.24f,
                          parentSlot = PanelSlot.MEDIA_STAGE,
                          depthRelation = SpatialDepthRelation.FRONT_OF_PARENT,
                      ),
                  PanelSlot.SYSTEM_TOOLBAR to placement("system_toolbar_anchor", 0.48f, 0.16f),
                  PanelSlot.BROWSE to hiddenPlacement("browse_anchor", 0.78f, 1.18f),
                  PanelSlot.CONTEXT to hiddenPlacement("context_anchor", 0.72f, 1.18f),
                  PanelSlot.FOCUS to placement("focus_anchor", 1.5f, 1f),
                  PanelSlot.ACTION_SHEET to hiddenPlacement("action_sheet_anchor", 0.58f, 0.42f),
              ),
          pipDocks =
              mapOf(
                  "focus-pip" to
                      PipDock(
                          id = "focus-pip",
                          anchor = SpatialAnchor.SceneAnchor("pip_dock_anchor"),
                          sizeMeters = PanelSizeMeters(0.62f, 0.35f),
                          exclusion =
                              ContentExclusion(
                                  id = "focus-main-top-right",
                                  anchor = SpatialAnchor.SceneAnchor("focus_content_top_right"),
                              ),
                      )
              ),
      )

  private fun shortsLayout() =
      ThemeLayout(
          placements =
              mapOf(
                  PanelSlot.MEDIA_STAGE to placement("shorts_media_stage_anchor", 0.62f, 1.1f),
                  PanelSlot.TRANSPORT to
                      placement(
                          "shorts_transport_anchor",
                          0.62f,
                          0.28f,
                          parentSlot = PanelSlot.MEDIA_STAGE,
                          depthRelation = SpatialDepthRelation.FRONT_OF_PARENT,
                      ),
                  PanelSlot.SYSTEM_TOOLBAR to placement("system_toolbar_anchor", 0.48f, 0.16f),
                  PanelSlot.SHORTS_DETAILS to placement("shorts_details_anchor", 0.68f, 1.1f),
                  PanelSlot.SHORTS_COMMENTS to placement("shorts_comments_anchor", 0.68f, 1.1f),
                  PanelSlot.BROWSE to hiddenPlacement("browse_anchor", 0.78f, 1.18f),
                  PanelSlot.CONTEXT to hiddenPlacement("context_anchor", 0.72f, 1.18f),
                  PanelSlot.FOCUS to hiddenPlacement("focus_anchor", 1.5f, 1f),
                  PanelSlot.ACTION_SHEET to hiddenPlacement("action_sheet_anchor", 0.58f, 0.42f),
              )
      )

  private fun placement(
      anchor: String,
      width: Float,
      height: Float,
      parentSlot: PanelSlot? = null,
      depthRelation: SpatialDepthRelation = SpatialDepthRelation.ROOT,
  ) =
      SlotPlacement(
          visibility = PanelVisibility.VISIBLE,
          anchor = SpatialAnchor.SceneAnchor(anchor),
          sizeMeters = PanelSizeMeters(width, height),
          parentSlot = parentSlot,
          depthRelation = depthRelation,
      )

  private fun hiddenPlacement(anchor: String, width: Float, height: Float) =
      placement(anchor, width, height).copy(visibility = PanelVisibility.HIDDEN)
}

object DefaultCinemaPlaybackCanvasGroup {
  data class Definition(
      val root: ComponentNode,
      val members: List<ComponentNode>,
  ) {
    fun asComponentGroup(): ComponentGroup = ComponentGroup("default-cinema-playback-canvas", members)
  }

  fun create(): Definition {
    val members =
        listOf(
            ComponentNode("system-status-strip", ComponentKind.SEMANTIC_MODULE),
            ComponentNode("content-navigation-slot", ComponentKind.SLOT),
            ComponentNode("media-stage", ComponentKind.SEMANTIC_MODULE),
            ComponentNode("watch-title", ComponentKind.SEMANTIC_MODULE),
            ComponentNode("transport-action-strip", ComponentKind.SEMANTIC_MODULE),
            ComponentNode("seek-timeline", ComponentKind.SEMANTIC_MODULE),
            ComponentNode("playback-config-popup", ComponentKind.POPUP),
            ComponentNode("grab-handle", ComponentKind.SEMANTIC_MODULE),
            ComponentNode("shorts-quiet-action-strip", ComponentKind.SEMANTIC_MODULE),
            ComponentNode("shorts-details-rail", ComponentKind.SEMANTIC_MODULE),
            ComponentNode("shorts-stage-controls", ComponentKind.SEMANTIC_MODULE),
            ComponentNode("shorts-comments-rail", ComponentKind.SEMANTIC_MODULE),
        )
    return Definition(
        root =
            ComponentNode(
                id = "default-cinema-playback-canvas",
                kind = ComponentKind.PANEL,
                children = members,
            ),
        members = members,
    )
  }
}
