package com.m0e_n00b.viriviri

import com.m0e_n00b.spatialworkbench.compose.LibraryComponents
import com.m0e_n00b.spatialworkbench.core.ComponentId
import com.m0e_n00b.spatialworkbench.core.ComponentKind
import com.m0e_n00b.spatialworkbench.core.ComponentNode
import com.m0e_n00b.spatialworkbench.core.PanelClass
import com.m0e_n00b.spatialworkbench.core.PanelSlot
import com.m0e_n00b.spatialworkbench.core.SpatialTheme
import com.m0e_n00b.spatialworkbench.core.ThemeComponentDescriptor
import com.m0e_n00b.spatialworkbench.core.ThemeComponentRegistry

/**
 * The app's half of the controlled component directory: the panel *content* components whose
 * implementation lives in `:app`, composed with the ones `:spatial-workbench-compose` owns.
 *
 * WHY IT LIVES IN `:app` AND NOT IN A LIBRARY MODULE
 * --------------------------------------------------
 * A library module must not claim an implementation it does not own. The video stage, the transport
 * XML layout, both rails, the nav bar and the input console are all `:app` artefacts, so they are
 * declared here. This is also the honest shape for portability: a second host on a different XR runtime
 * would supply its own half, and the library half would be reused unchanged.
 *
 * WHAT THE IDS ACTUALLY REVEAL
 * ----------------------------
 * Read [themeComponentsWithoutImplementation] before assuming the theme and the components line up.
 * They do not, and this catalogue is the place where that becomes measurable instead of invisible:
 * the default theme references 17 semantic module ids (`watch-title`, `transport-action-strip`,
 * `system-status-strip`, …) under which **no component is registered by any module**. Those names
 * describe intents the app implements under different names (the nav bar, `controls.xml`, the grab
 * bar), so the vocabulary was never reconciled. Making that gap explicit is the point: the previous
 * state was a registry contract with no instance, where nothing could ever report the mismatch.
 */
internal object AppComponentCatalogue {

  /**
   * Panel content components implemented in `:app`, keyed by the id the theme fixture uses for them.
   *
   * `implementationRef` names the app artefact, not a Kotlin symbol, because most of these are panel
   * registrations or XML layouts rather than composables.
   */
  val hostComponents: List<ThemeComponentDescriptor> =
      listOf(
          hostDescriptor(
              "mediaStage",
              ComponentKind.PANEL,
              "registry:spatialized_video_panel (hand-built compositor PanelSceneObject)",
              panelClass = PanelClass.MEDIA_SURFACE,
              slots = setOf(PanelSlot.MEDIA_STAGE),
          ),
          hostDescriptor(
              "transport",
              ComponentKind.PANEL,
              "registry:controls_id (R.layout.controls)",
              slots = setOf(PanelSlot.TRANSPORT),
          ),
          hostDescriptor(
              "centerContent",
              ComponentKind.PANEL,
              "registry:center_content_panel (ImmersiveCenterContentPanel)",
              panelClass = PanelClass.SCENE_PANEL,
              slots = setOf(PanelSlot.BROWSE),
          ),
          hostDescriptor(
              "detailRail",
              ComponentKind.PANEL,
              "registry:video_selector_panel (ImmersiveLeftPanel / MoviePanel)",
              slots = setOf(PanelSlot.CONTEXT),
          ),
          hostDescriptor(
              "contextRail",
              ComponentKind.PANEL,
              "registry:mode_panel (R.layout.mode_panel)",
              slots = setOf(PanelSlot.CONTEXT),
          ),
          hostDescriptor(
              "systemToolbar",
              ComponentKind.PANEL,
              "registry:mr_panel (GlobalNavigation / MRPanel)",
              slots = setOf(PanelSlot.SYSTEM_TOOLBAR),
          ),
          hostDescriptor(
              "inputConsole",
              ComponentKind.PANEL,
              "registry:input_method_panel (ImmersiveInputMethodPanel)",
              slots = setOf(PanelSlot.ACTION_SHEET),
          ),
          // Declared on purpose even though nothing is registered for them: the theme names these slots,
          // and an id that exists in the theme but not here is exactly what the reconciliation test
          // reports. Keeping them out would hide the gap rather than close it.
          hostDescriptor(
              "focusPanel",
              ComponentKind.FOCUS_PANEL,
              "unimplemented: PanelSlot.FOCUS has no entity (docs/ui-structure-and-terminology.md §6.3)",
              slots = setOf(PanelSlot.FOCUS),
          ),
          hostDescriptor(
              "shortsDetailsRail",
              ComponentKind.PANEL,
              "unimplemented: PanelSlot.SHORTS_DETAILS has no entity",
              slots = setOf(PanelSlot.SHORTS_DETAILS),
          ),
          hostDescriptor(
              "shortsCommentsRail",
              ComponentKind.PANEL,
              "unimplemented: PanelSlot.SHORTS_COMMENTS has no entity",
              slots = setOf(PanelSlot.SHORTS_COMMENTS),
          ),
      )

  /** Library half plus host half. One table, no duplicates expected — asserted by the test. */
  fun descriptors(): List<ThemeComponentDescriptor> =
      LibraryComponents.descriptors + hostComponents

  fun registry(): ThemeComponentRegistry = ThemeComponentRegistry.of(descriptors())

  val ids: Set<ComponentId> = descriptors().mapTo(linkedSetOf()) { it.id }

  /**
   * Theme-referenced component ids that no module registers an implementation for.
   *
   * These are the 18 semantic ids in `CinemaTheme`'s `rootComponent` / `componentGroups` — including
   * `default-cinema-playback-canvas`, which is the composition *root* rather than a leaf. They were
   * written as design vocabulary; the app grew its own names. Either future work reconciles them
   * (by registering real components under these ids) or the theme fixture is rewritten to use the
   * registered vocabulary — but until then the registry must not pretend they exist.
   *
   * The test pins this set, so the gap cannot grow silently and cannot be closed silently either.
   * It already earned its keep once: the first version of this list was missing
   * `default-cinema-playback-canvas`, and the assertion is what surfaced it.
   */
  val themeComponentsWithoutImplementation: Set<ComponentId> =
      setOf(
          "default-cinema-playback-canvas",
          "system-status-strip",
          "content-navigation-slot",
          "media-stage",
          "watch-title",
          "transport-action-strip",
          "seek-timeline",
          "playback-config-popup",
          "grab-handle",
          "shorts-quiet-action-strip",
          "shorts-details-rail",
          "shorts-stage-controls",
          "shorts-comments-rail",
          "browse-panel",
          "context-tabs",
          "playlist-list",
          "related-list",
          "video-details",
      ).mapTo(linkedSetOf()) { ComponentId(it) }

  private fun hostDescriptor(
      id: String,
      kind: ComponentKind,
      implementationRef: String,
      slots: Set<PanelSlot> = emptySet(),
      panelClass: PanelClass = PanelClass.UI_PANEL,
  ): ThemeComponentDescriptor =
      ThemeComponentDescriptor(
          id = ComponentId(id),
          kind = kind,
          allowedSlots = slots,
          compatiblePanelClasses = setOf(panelClass),
          implementationRef = implementationRef,
      )
}

/**
 * Every `ComponentId` a theme references: its component tree, its component groups, and every panel's
 * content component. Used by the reconciliation test, and useful to any adapter that wants to report
 * what a theme is asking for.
 */
internal fun SpatialTheme.referencedComponentIds(): Set<ComponentId> {
  val ids = linkedSetOf<ComponentId>()
  fun visit(node: ComponentNode) {
    ids += ComponentId(node.id)
    node.children.forEach(::visit)
    node.namedSlots.values.flatten().forEach(::visit)
  }
  visit(rootComponent)
  componentGroups.flatMap { it.members }.forEach(::visit)
  panels.values.forEach { ids += it.componentId }
  return ids
}
