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
    )
  }

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
                  PanelSlot.SYSTEM_TOOLBAR to placement("system_toolbar_anchor", 0.48f, 0.12f),
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
                  PanelSlot.SYSTEM_TOOLBAR to placement("system_toolbar_anchor", 0.48f, 0.12f),
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
                  PanelSlot.SYSTEM_TOOLBAR to placement("system_toolbar_anchor", 0.48f, 0.12f),
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
