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
}

object ThemeValidator {
  private val bindingPattern = Regex("[a-z][A-Za-z0-9]*(\\.[a-z][A-Za-z0-9]*)*")

  fun validate(theme: SpatialTheme): List<ThemeValidationIssue> = buildList {
    if (theme.id.isBlank()) add(ThemeValidationIssue.InvalidThemeIdentity("id"))
    if (theme.displayName.isBlank()) add(ThemeValidationIssue.InvalidThemeIdentity("displayName"))
    if (theme.sceneReference.isBlank()) add(ThemeValidationIssue.InvalidThemeIdentity("sceneReference"))

    listOf(ImmersiveLayoutMode.WATCH, ImmersiveLayoutMode.FOCUS).forEach { mode ->
      if (theme.layouts[mode] == null) add(ThemeValidationIssue.MissingLayout(mode))
    }

    theme.layouts.forEach { (mode, layout) -> validateLayout(mode, layout, this) }
    theme.toolbarModules.groupBy(ToolbarModule::id).forEach { (id, modules) ->
      if (id.isBlank() || modules.size > 1) add(ThemeValidationIssue.DuplicateToolbarModuleId(id))
    }
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
