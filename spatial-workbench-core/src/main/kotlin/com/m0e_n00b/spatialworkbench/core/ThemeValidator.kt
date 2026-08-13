package com.m0e_n00b.spatialworkbench.core

sealed interface ThemeValidationIssue {
  data class InvalidThemeIdentity(val field: String) : ThemeValidationIssue

  data class MissingLayout(val mode: ImmersiveLayoutMode) : ThemeValidationIssue

  data class MissingSlotReference(
      val mode: ImmersiveLayoutMode,
      val owner: PanelSlot,
      val reference: PanelSlot,
  ) : ThemeValidationIssue

  data class InvalidAnchor(val context: String) : ThemeValidationIssue

  data class InvalidPanelSize(val context: String) : ThemeValidationIssue

  data class InvalidCylinderGeometry(val context: String) : ThemeValidationIssue

  data class DuplicateToolbarModuleId(val id: String) : ThemeValidationIssue

  data class DuplicateComponentId(val id: String) : ThemeValidationIssue

  data class InvalidDataBinding(val componentId: String, val binding: String) : ThemeValidationIssue

  data class InvalidAction(val componentId: String, val action: ThemeAction) : ThemeValidationIssue

  data class InvalidPipDock(val id: String) : ThemeValidationIssue

  data class InvalidTransportOverlay(val mode: ImmersiveLayoutMode) : ThemeValidationIssue

  data class InvalidCanvas(val id: String) : ThemeValidationIssue

  data class DuplicateComponentGroupId(val id: String) : ThemeValidationIssue

  data class InvalidPalette(val reason: String) : ThemeValidationIssue
}

object ThemeValidator {
  private val bindingPattern = Regex("[a-z][A-Za-z0-9]*(\\.[a-z][A-Za-z0-9]*)*")

  fun validate(theme: SpatialTheme): List<ThemeValidationIssue> = buildList {
    if (theme.id.isBlank()) add(ThemeValidationIssue.InvalidThemeIdentity("id"))
    if (theme.displayName.isBlank()) add(ThemeValidationIssue.InvalidThemeIdentity("displayName"))
    if (theme.sceneReference.isBlank()) add(ThemeValidationIssue.InvalidThemeIdentity("sceneReference"))

    ImmersiveLayoutMode.entries.forEach { mode ->
      if (theme.layouts[mode] == null) add(ThemeValidationIssue.MissingLayout(mode))
    }

    theme.layouts.forEach { (mode, layout) -> validateLayout(mode, layout, this) }
    theme.toolbarModules.groupBy(ToolbarModule::id).forEach { (id, modules) ->
      if (id.isBlank() || modules.size > 1) add(ThemeValidationIssue.DuplicateToolbarModuleId(id))
    }
    theme.componentGroups.groupBy(ComponentGroup::id).forEach { (id, groups) ->
      if (id.isBlank() || groups.size > 1) add(ThemeValidationIssue.DuplicateComponentGroupId(id))
      groups.forEach { group -> group.members.forEach { validateComponentTree(it, this) } }
    }
    val componentIds = collectComponentIds(theme.rootComponent, theme.componentGroups)
    theme.canvases.forEach { canvas -> validateCanvas(theme, canvas, componentIds, this) }
    validatePalette(theme.palette, this)
    validateComponentTree(theme.rootComponent, this)
  }

  private fun validateLayout(
      mode: ImmersiveLayoutMode,
      layout: ThemeLayout,
      issues: MutableList<ThemeValidationIssue>,
  ) {
    layout.placements.forEach { (slot, placement) ->
      validateAnchor(mode, "$mode.$slot", placement.anchor, layout.placements, slot, issues)
      if (placement.parentSlot == slot ||
          (placement.parentSlot != null && placement.parentSlot !in layout.placements)) {
        issues += ThemeValidationIssue.MissingSlotReference(mode, slot, placement.parentSlot ?: slot)
      }
      if (!placement.sizeMeters.isValid()) issues += ThemeValidationIssue.InvalidPanelSize("$mode.$slot")
      if (!placement.shape.isValid()) issues += ThemeValidationIssue.InvalidCylinderGeometry("$mode.$slot")
      if (slot == PanelSlot.TRANSPORT &&
          (placement.parentSlot != PanelSlot.MEDIA_STAGE ||
              placement.depthRelation != SpatialDepthRelation.FRONT_OF_PARENT)) {
        issues += ThemeValidationIssue.InvalidTransportOverlay(mode)
      }
    }
    layout.pipDocks.forEach { (id, dock) ->
      if (id.isBlank() || dock.id != id || !dock.sizeMeters.isValid()) {
        issues += ThemeValidationIssue.InvalidPipDock(id)
      }
      validateAnchor(mode, "$mode.pipDocks.$id", dock.anchor, layout.placements, null, issues)
      validateAnchor(mode, "$mode.pipDocks.$id.exclusion", dock.exclusion.anchor, layout.placements, null, issues)
      if (dock.exclusion.id.isBlank()) issues += ThemeValidationIssue.InvalidPipDock(id)
    }
  }

  private fun validateCanvas(
      theme: SpatialTheme,
      canvas: WorkbenchCanvas,
      componentIds: Set<String>,
      issues: MutableList<ThemeValidationIssue>,
  ) {
    if (canvas.id.isBlank() || theme.layouts[canvas.mode] == null || canvas.visibleSlots.isEmpty()) {
      issues += ThemeValidationIssue.InvalidCanvas(canvas.id)
      return
    }
    if (canvas.overflowPolicy == CanvasOverflowPolicy.VISIBLE && !canvas.preservesHitTesting) {
      issues += ThemeValidationIssue.InvalidCanvas(canvas.id)
    }
    val layout = theme.layouts.getValue(canvas.mode)
    if (canvas.visibleSlots.any { it !in layout.placements } ||
        canvas.visibleComponentIds.any { it !in componentIds }) {
      issues += ThemeValidationIssue.InvalidCanvas(canvas.id)
    }
  }

  private fun collectComponentIds(
      root: ComponentNode,
      groups: List<ComponentGroup>,
  ): Set<String> {
    val ids = mutableSetOf<String>()
    fun visit(node: ComponentNode) {
      ids += node.id
      node.children.forEach(::visit)
      node.namedSlots.values.flatten().forEach(::visit)
    }
    visit(root)
    groups.flatMap(ComponentGroup::members).forEach(::visit)
    return ids
  }

  private fun validatePalette(palette: CinemaPalette, issues: MutableList<ThemeValidationIssue>) {
    val colors =
        listOf(
            palette.background,
            palette.surface,
            palette.normalText,
            palette.secondaryText,
            palette.highlightText,
            palette.primaryButton,
            palette.primaryButtonLabel,
            palette.secondaryButton,
            palette.secondaryButtonLabel,
            palette.border,
            palette.danger,
            palette.chargingBadge,
            palette.chargingBadgeLabel,
        )
    if (colors.any { !it.isValid() }) issues += ThemeValidationIssue.InvalidPalette("channel-range")
    if (!palette.surfaceOpacity.isFinite() || palette.surfaceOpacity !in 0f..1f) {
      issues += ThemeValidationIssue.InvalidPalette("surface-opacity")
    }
    if (palette.normalText.contrastAgainst(palette.surface) < 4.5) {
      issues += ThemeValidationIssue.InvalidPalette("normal-text-contrast")
    }
    if (palette.primaryButtonLabel.contrastAgainst(palette.primaryButton) < 4.5) {
      issues += ThemeValidationIssue.InvalidPalette("primary-button-label-contrast")
    }
    if (palette.secondaryButtonLabel.contrastAgainst(palette.secondaryButton) < 4.5) {
      issues += ThemeValidationIssue.InvalidPalette("secondary-button-label-contrast")
    }
    if (palette.chargingBadgeLabel.contrastAgainst(palette.chargingBadge) < 4.5) {
      issues += ThemeValidationIssue.InvalidPalette("charging-badge-label-contrast")
    }
  }

  private fun validateAnchor(
      mode: ImmersiveLayoutMode,
      context: String,
      anchor: SpatialAnchor,
      placements: Map<PanelSlot, SlotPlacement>,
      owner: PanelSlot?,
      issues: MutableList<ThemeValidationIssue>,
  ) {
    when (anchor) {
      is SpatialAnchor.SceneAnchor -> {
        if (anchor.name.isBlank()) issues += ThemeValidationIssue.InvalidAnchor(context)
      }
      is SpatialAnchor.SlotAnchor -> {
        if (anchor.slot !in placements || anchor.slot == owner) {
          issues += ThemeValidationIssue.MissingSlotReference(
              mode,
              owner ?: anchor.slot,
              anchor.slot,
          )
        }
      }
    }
  }

  private fun validateComponentTree(
      root: ComponentNode,
      issues: MutableList<ThemeValidationIssue>,
  ) {
    val ids = mutableSetOf<String>()
    fun visit(node: ComponentNode) {
      if (node.id.isBlank() || !ids.add(node.id)) issues += ThemeValidationIssue.DuplicateComponentId(node.id)
      node.dataBindings.values.forEach { binding ->
        if (!bindingPattern.matches(binding)) {
          issues += ThemeValidationIssue.InvalidDataBinding(node.id, binding)
        }
      }
      node.actions.forEach { action ->
        if (action is ThemeAction.SetPlaybackSpeed &&
            (!action.speed.isFinite() || action.speed !in 0.25f..4f)) {
          issues += ThemeValidationIssue.InvalidAction(node.id, action)
        }
      }
      node.children.forEach(::visit)
      node.namedSlots.values.flatten().forEach(::visit)
    }
    visit(root)
  }

  private fun PanelSizeMeters.isValid(): Boolean =
      width.isFinite() && height.isFinite() && width > 0f && height > 0f

  private fun PanelShape.isValid(): Boolean =
      when (this) {
        PanelShape.Quad -> true
        is PanelShape.Cylinder ->
            radiusMeters.isFinite() && radiusMeters > 0f && arcDegrees.isFinite() && arcDegrees in 0.1f..359.9f
      }
}
