package com.m0e_n00b.spatialworkbench.core

object CinemaTheme {
  fun create(): SpatialTheme =
      SpatialTheme(
          id = "cinema",
          displayName = "Cinema",
          sceneReference = "themes/cinema/scene/Main.metaspatial",
          layouts =
              mapOf(
                  ImmersiveLayoutMode.WATCH to watchLayout(),
                  ImmersiveLayoutMode.FOCUS to focusLayout(),
                  ImmersiveLayoutMode.EDIT to watchLayout(),
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
              ComponentNode(
                  id = "workbench",
                  kind = ComponentKind.PANEL,
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
      )

  private fun watchLayout() =
      ThemeLayout(
          placements =
              mapOf(
                  PanelSlot.MEDIA_STAGE to placement("media_stage_anchor", 1.6f, 0.9f),
                  PanelSlot.TRANSPORT to placement("transport_anchor", 0.9f, 0.24f),
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
                  PanelSlot.TRANSPORT to placement("transport_anchor", 0.9f, 0.24f),
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

  private fun placement(anchor: String, width: Float, height: Float) =
      SlotPlacement(
          visibility = PanelVisibility.VISIBLE,
          anchor = SpatialAnchor.SceneAnchor(anchor),
          sizeMeters = PanelSizeMeters(width, height),
      )

  private fun hiddenPlacement(anchor: String, width: Float, height: Float) =
      placement(anchor, width, height).copy(visibility = PanelVisibility.HIDDEN)
}
