package com.m0e_n00b.spatialworkbench.compose

import com.m0e_n00b.spatialworkbench.core.ComponentId
import com.m0e_n00b.spatialworkbench.core.ComponentKind
import com.m0e_n00b.spatialworkbench.core.PanelSlot
import com.m0e_n00b.spatialworkbench.core.ThemeComponentDescriptor
import com.m0e_n00b.spatialworkbench.core.ThemeComponentRegistry

/**
 * The component library's own table: `ComponentId -> descriptor`.
 *
 * WHY THIS EXISTS
 * ---------------
 * `ThemeComponentRegistry` was delivered by the theme-runtime Phase 1 as a contract with no instance, so
 * the "controlled component directory" the theme is only allowed to draw from was, in practice, empty —
 * the validator's `UnknownComponent` check could never fire against a real table. This is that table, for
 * the components that actually exist in this module.
 *
 * It is also the **mapping target for a design tool**: a Penpot component library has to land on stable,
 * single-source ids, and these are they.
 *
 * WHAT IS DELIBERATELY NOT HERE
 * -----------------------------
 * Panel *content* components whose implementation lives in `:app` (the video stage, the transport XML,
 * the rails, the input console, the nav bar) are NOT declared here — a contract module must not claim an
 * implementation it does not own. `:app` extends this table via its own catalogue; the reconciliation
 * between "what the theme references" and "what is implemented" is asserted in
 * `AppComponentCatalogueTest`.
 *
 * ID CONVENTION
 * -------------
 * Library components use kebab-case (`workbench-title`). Note that the theme fixture's *panel content*
 * ids use camelCase (`mediaStage`, `detailRail`) because they name a panel's content rather than a module
 * inside one. Both live in the same `ComponentId` space (design §4.5 defines it as one space), so the
 * catalogue test also asserts no two ids collide once case and dashes are normalised away — that footgun
 * is cheap to prevent and expensive to discover later.
 */
object LibraryComponents {

  /** Every component this module implements, in one place. */
  val descriptors: List<ThemeComponentDescriptor> =
      listOf(
          // ---- panel shells and layout primitives ----
          descriptor("spatial-panel-shell", ComponentKind.SEMANTIC_MODULE, "SpatialPanelShell"),
          descriptor("workbench-panel-shell", ComponentKind.SEMANTIC_MODULE, "WorkbenchPanelShell"),
          descriptor("workbench-section", ComponentKind.STACK, "WorkbenchSection"),
          descriptor(
              "workbench-full-height-collapse",
              ComponentKind.OVERLAY,
              "WorkbenchFullHeightCollapse",
              slots = setOf(PanelSlot.CONTEXT),
          ),

          // ---- workbench atoms (detail / context rail content) ----
          descriptor(
              "workbench-title",
              ComponentKind.SEMANTIC_MODULE,
              "WorkbenchTitle",
              slots = setOf(PanelSlot.CONTEXT, PanelSlot.BROWSE),
          ),
          descriptor(
              "workbench-secondary-text",
              ComponentKind.SEMANTIC_MODULE,
              "WorkbenchSecondaryText",
              slots = setOf(PanelSlot.CONTEXT, PanelSlot.BROWSE),
          ),
          descriptor(
              "workbench-action-strip",
              ComponentKind.SEMANTIC_MODULE,
              "WorkbenchActionStrip",
              slots = setOf(PanelSlot.CONTEXT, PanelSlot.SHORTS_DETAILS, PanelSlot.SHORTS_COMMENTS),
          ),
          descriptor(
              "workbench-creator-row",
              ComponentKind.SEMANTIC_MODULE,
              "WorkbenchCreatorRow",
              slots = setOf(PanelSlot.CONTEXT),
          ),
          descriptor(
              "workbench-footer-action",
              ComponentKind.SEMANTIC_MODULE,
              "WorkbenchFooterAction",
              slots = setOf(PanelSlot.CONTEXT),
          ),

          // ---- media cards ----
          descriptor("content-access-badge", ComponentKind.SEMANTIC_MODULE, "ContentAccessBadge"),
          descriptor("media-thumbnail-frame", ComponentKind.SEMANTIC_MODULE, "MediaThumbnailFrame"),

          // ---- transient / overlay surfaces ----
          descriptor("transient-message-host", ComponentKind.OVERLAY, "TransientMessageHost"),

          // ---- search and the input console ----
          descriptor(
              "search-query-field",
              ComponentKind.SEMANTIC_MODULE,
              "SearchQueryField",
              slots = setOf(PanelSlot.BROWSE),
          ),
          descriptor(
              "cinema-input-console",
              ComponentKind.SEMANTIC_MODULE,
              "CinemaInputConsole",
              slots = setOf(PanelSlot.ACTION_SHEET),
          ),
          descriptor(
              "search-candidate-strip",
              ComponentKind.SEMANTIC_MODULE,
              "SearchCandidateStrip",
              slots = setOf(PanelSlot.ACTION_SHEET),
          ),
          descriptor(
              "search-input-method-board",
              ComponentKind.GRID,
              "SearchInputMethodBoard",
              slots = setOf(PanelSlot.ACTION_SHEET),
          ),
          descriptor(
              "system-ime-action-button",
              ComponentKind.SEMANTIC_MODULE,
              "SystemImeActionButton",
              slots = setOf(PanelSlot.ACTION_SHEET),
          ),
          descriptor(
              "search-actions",
              ComponentKind.ROW,
              "SearchActions",
              slots = setOf(PanelSlot.ACTION_SHEET),
          ),
      )

  /** The registry form, ready to hand to `ThemeValidator`/`ComponentCompositionResolver`. */
  fun registry(): ThemeComponentRegistry = ThemeComponentRegistry.of(descriptors)

  /** Ids only, for the collision assertion and for callers that just need the vocabulary. */
  val ids: Set<ComponentId> = descriptors.mapTo(linkedSetOf()) { it.id }

  private fun descriptor(
      id: String,
      kind: ComponentKind,
      implementation: String,
      slots: Set<PanelSlot> = emptySet(),
  ): ThemeComponentDescriptor =
      ThemeComponentDescriptor(
          id = ComponentId(id),
          kind = kind,
          allowedSlots = slots,
          // Every component here is a Compose surface, so it can be rendered by any UI panel class.
          // `MEDIA_SURFACE` is excluded because that panel is the compositor video output, which never
          // hosts library UI (design §2.8 type C).
          compatiblePanelClasses =
              com.m0e_n00b.spatialworkbench.core.PanelClass.entries
                  .filterTo(linkedSetOf()) { it != com.m0e_n00b.spatialworkbench.core.PanelClass.MEDIA_SURFACE },
          implementationRef = "com.m0e_n00b.spatialworkbench.compose.$implementation",
      )
}
