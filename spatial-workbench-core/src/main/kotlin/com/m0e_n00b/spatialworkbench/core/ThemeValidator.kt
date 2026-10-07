package com.m0e_n00b.spatialworkbench.core

/*
 * `ThemeValidator` owns structural, range, reference-integrity and cross-group validation of a theme
 * (design §5.3). It is a pure function: everything that depends on the host (scene anchors, entity
 * references, the controlled component directory, overlay surfaces, headset verification records) is
 * injected through [ThemeCatalogs]. The reference checks (anchors, entity refs, components, overlay
 * surfaces) are only reported when their catalog is supplied, which is why
 * `validate(CinemaTheme.create())` stays valid even though the scene does not contain the anchors
 * `CinemaTheme` declares. The grab-verification catalog is the exception: it carries positive
 * evidence, so its absence reports `UnverifiedGrabMechanism` rather than skipping the check.
 *
 * Error/warning split: [ThemeValidationIssue.severity]. `NATIVE_PARENT` grab propagation cannot be
 * verified on device from a JVM test, so it is a warning (`UnverifiedGrabMechanism`), never a silent
 * pass and never an error.
 *
 * TODO(R8.1): a theme may only recompose controlled components inside the registered panel set,
 * configure their geometry, bind slots and declare grab/danmaku groups. It may never create or
 * destroy Spatial panels at runtime (design §13.2) — the schema has no field able to express that.
 */

enum class ThemeIssueSeverity {
  ERROR,
  WARNING,
}

sealed interface ThemeValidationIssue {
  val severity: ThemeIssueSeverity
    get() = ThemeIssueSeverity.ERROR

  /** Field path for a locatable report, e.g. `panels.transport.offsetMeters.y`. */
  val path: String
    get() = ""

  data class InvalidThemeIdentity(val field: String) : ThemeValidationIssue

  data class MissingLayout(val mode: ImmersiveLayoutMode) : ThemeValidationIssue

  data class MissingSlotReference(
      val mode: ImmersiveLayoutMode,
      val owner: PanelSlot,
      val reference: PanelSlot,
  ) : ThemeValidationIssue

  data class InvalidAnchor(
      val context: String,
      override val path: String = "",
  ) : ThemeValidationIssue

  data class InvalidPanelSize(
      val context: String,
      override val path: String = "",
  ) : ThemeValidationIssue

  data class InvalidCylinderGeometry(
      val context: String,
      override val path: String = "",
  ) : ThemeValidationIssue

  data class DuplicateToolbarModuleId(val id: String) : ThemeValidationIssue

  data class DuplicateComponentId(val id: String) : ThemeValidationIssue

  data class InvalidDataBinding(val componentId: String, val binding: String) : ThemeValidationIssue

  data class InvalidAction(val componentId: String, val action: ThemeAction) : ThemeValidationIssue

  data class InvalidPipDock(val id: String) : ThemeValidationIssue

  data class InvalidTransportOverlay(val mode: ImmersiveLayoutMode) : ThemeValidationIssue

  data class InvalidCanvas(val id: String) : ThemeValidationIssue

  data class DuplicateComponentGroupId(val id: String) : ThemeValidationIssue

  data class InvalidPalette(val reason: String) : ThemeValidationIssue

  /** Schema revision outside [ThemeSchema.SUPPORTED_SCHEMA_VERSIONS]; never silently downgraded. */
  data class UnsupportedSchemaVersion(
      val version: Int,
      val reason: String = "",
  ) : ThemeValidationIssue

  data class MissingSceneAnchor(
      val anchor: String,
      val context: String,
      override val path: String = "",
  ) : ThemeValidationIssue

  /** A referenced component is not in the controlled component directory (R4.1/R5). */
  data class UnknownComponent(
      val owner: String,
      val componentId: String,
      override val path: String = "",
  ) : ThemeValidationIssue

  data class ComponentClassMismatch(
      val panelId: String,
      val componentId: String,
      val panelClass: PanelClass,
      override val path: String = "",
  ) : ThemeValidationIssue

  /** Non-blank when the parser saw a panel class name the schema does not define. */
  data class UnknownPanelClass(
      val panelId: String,
      val rawClass: String,
      override val path: String = "",
  ) : ThemeValidationIssue

  data class ComponentSlotNotAllowed(
      val groupId: String,
      val componentId: String,
      val slot: SlotId,
      override val path: String = "",
  ) : ThemeValidationIssue

  /** A binding (or panel parent) points at a panel that is not declared. */
  data class UnknownPanelRef(
      val slot: SlotId?,
      val panelId: String,
      override val path: String = "",
  ) : ThemeValidationIssue

  /**
   * Conditional slot-ownership rule (R7.2). Never an absolute "one slot, one panel" rule: several
   * panels may claim one slot with an explicit share policy. Reported when a claim is undeclared
   * (any claim is `EXCLUSIVE`, or the claims mix policies) and when the same panel claims one slot
   * more than once — the slot is bound by panels, so a repeated self-claim is not a sharing
   * declaration.
   */
  data class DuplicateSlotOwner(
      val slot: SlotId,
      val panelIds: List<String>,
      override val path: String = "",
  ) : ThemeValidationIssue

  data class IncompleteMirrorGroup(
      val slot: SlotId,
      val mirrorGroup: String,
      val reason: String,
      override val path: String = "",
  ) : ThemeValidationIssue

  data class ParentCycle(
      val panelIds: List<String>,
      override val path: String = "",
  ) : ThemeValidationIssue

  data class AmbiguousPlacement(
      val panelId: String,
      override val path: String = "",
  ) : ThemeValidationIssue

  data class DepthRelationWithoutParent(
      val panelId: String,
      override val path: String = "",
  ) : ThemeValidationIssue

  data class OffsetOutOfRange(
      val owner: String,
      val axis: String,
      val value: Float,
      override val path: String = "",
  ) : ThemeValidationIssue

  data class RotationOutOfRange(
      val owner: String,
      val axis: String,
      val value: Float,
      override val path: String = "",
  ) : ThemeValidationIssue

  data class ZOrderOutOfRange(
      val panelId: String,
      val zOrder: Int,
      override val path: String = "",
  ) : ThemeValidationIssue

  data class HitTestConflict(
      val panelId: String,
      override val path: String = "",
  ) : ThemeValidationIssue

  data class GeometryNotThemeable(
      val panelId: String,
      val property: ThemeableGeometry,
      val panelClass: PanelClass,
      override val path: String = "",
  ) : ThemeValidationIssue

  data class UnknownEntityRef(
      val owner: String,
      val ref: String,
      override val path: String = "",
  ) : ThemeValidationIssue

  data class DuplicateGrabGroupId(val id: String) : ThemeValidationIssue

  data class UnknownGrabGroupRef(
      val groupId: String,
      val parentGroupId: String,
      override val path: String = "",
  ) : ThemeValidationIssue

  data class GrabGroupParentCycle(
      val groupIds: List<String>,
      override val path: String = "",
  ) : ThemeValidationIssue

  data class GrabMemberOverlap(
      val childGroupId: String,
      val parentGroupId: String,
      val ref: String,
      override val path: String = "",
  ) : ThemeValidationIssue

  data class GrabHandleSelfReference(
      val groupId: String,
      val ref: String,
      override val path: String = "",
  ) : ThemeValidationIssue

  data class AsymmetricExclusion(
      val groupId: String,
      val otherGroupId: String,
      override val path: String = "",
  ) : ThemeValidationIssue

  /** `NATIVE_PARENT` has no headset verification record (warning, see design §4.3 / Phase 3.0). */
  data class UnverifiedGrabMechanism(
      val groupId: String,
      val mechanism: GrabMechanism,
      override val path: String = "",
  ) : ThemeValidationIssue {
    override val severity: ThemeIssueSeverity
      get() = ThemeIssueSeverity.WARNING
  }

  /** Parser-level problem: missing, wrong-typed, or unreadable field at [path]. */
  data class MalformedField(
      override val path: String,
      val reason: String,
  ) : ThemeValidationIssue
}

data class ThemeValidationReport(
    val issues: List<ThemeValidationIssue>,
    val overlayIssues: List<MediaOverlayValidationIssue> = emptyList(),
) {
  val errors: List<ThemeValidationIssue>
    get() = issues.filter { it.severity == ThemeIssueSeverity.ERROR }

  val warnings: List<ThemeValidationIssue>
    get() = issues.filter { it.severity == ThemeIssueSeverity.WARNING }

  val isValid: Boolean
    get() = errors.isEmpty() && overlayIssues.isEmpty()

  fun isEmpty(): Boolean = issues.isEmpty() && overlayIssues.isEmpty()
}

object ThemeValidator {
  /** Primer 3D hard limits (PRD R5, design §5.3). */
  const val MAX_OFFSET_METERS: Float = 3.0f

  const val MAX_PITCH_ROLL_DEGREES: Float = 80f
  const val MAX_YAW_DEGREES: Float = 180f
  const val MIN_PANEL_SIZE_METERS: Float = 0.15f
  const val MAX_PANEL_SIZE_METERS: Float = 4.0f
  const val MIN_CYLINDER_RADIUS_METERS: Float = 1.5f
  const val MAX_CYLINDER_RADIUS_METERS: Float = 8.0f
  const val MIN_CYLINDER_ARC_DEGREES: Float = 0.1f
  const val MAX_CYLINDER_ARC_DEGREES: Float = 359.9f
  const val MIN_Z_ORDER: Int = -100
  const val MAX_Z_ORDER: Int = 100

  private val bindingPattern = Regex("[a-z][A-Za-z0-9]*(\\.[a-z][A-Za-z0-9]*)*")

  /** Backwards-compatible entry point: no catalogs, errors only. */
  fun validate(theme: SpatialTheme): List<ThemeValidationIssue> = validate(theme, ThemeCatalogs.NONE)

  /** Catalog-aware validation. Returns errors only; use [inspect] for warnings as well. */
  fun validate(theme: SpatialTheme, catalogs: ThemeCatalogs): List<ThemeValidationIssue> =
      Validation(theme, catalogs, includeWarnings = false).run()

  /** Full report: errors, warnings and media-overlay topology issues (when a surface catalog exists). */
  fun inspect(
      theme: SpatialTheme,
      catalogs: ThemeCatalogs = ThemeCatalogs.NONE,
  ): ThemeValidationReport {
    val validation = Validation(theme, catalogs, includeWarnings = true)
    val issues = validation.run()
    val overlayIssues =
        catalogs.overlaySurfaces?.let { surfaces ->
          MediaOverlayValidator.validateTopology(
              surfaces.all(),
              theme.danmakuGroups,
              theme.danmakuOcclusionDomains,
          )
        } ?: emptyList()
    return ThemeValidationReport(issues, overlayIssues)
  }

  private class Validation(
      private val theme: SpatialTheme,
      private val catalogs: ThemeCatalogs,
      private val includeWarnings: Boolean,
  ) {
    private val issues = mutableListOf<ThemeValidationIssue>()

    fun run(): List<ThemeValidationIssue> {
      validateSchemaVersion()
      validateIdentity()
      validateLayouts()
      validateToolbarModules()
      validateComponentGroups()
      val componentIds = collectComponentIds(theme.rootComponent, theme.componentGroups)
      theme.canvases.forEach { validateCanvas(it, componentIds) }
      validatePalette(theme.palette)
      validateComponentTree(theme.rootComponent)
      validateRequiredAnchors()
      validatePanels()
      validateSlotBindings()
      validateGrabGroups()
      return issues
    }

    private fun add(issue: ThemeValidationIssue) {
      if (issue.severity == ThemeIssueSeverity.WARNING && !includeWarnings) return
      issues += issue
    }

    private fun validateSchemaVersion() {
      if (!ThemeSchema.isSupported(theme.schemaVersion)) {
        add(ThemeValidationIssue.UnsupportedSchemaVersion(theme.schemaVersion, "unsupported-version"))
      }
    }

    private fun validateIdentity() {
      if (theme.id.isBlank()) add(ThemeValidationIssue.InvalidThemeIdentity("id"))
      if (theme.displayName.isBlank()) add(ThemeValidationIssue.InvalidThemeIdentity("displayName"))
      if (theme.sceneReference.isBlank()) add(ThemeValidationIssue.InvalidThemeIdentity("sceneReference"))
    }

    private fun validateLayouts() {
      ImmersiveLayoutMode.entries.forEach { mode ->
        if (theme.layouts[mode] == null) add(ThemeValidationIssue.MissingLayout(mode))
      }
      theme.layouts.forEach { (mode, layout) -> validateLayout(mode, layout) }
    }

    private fun validateLayout(mode: ImmersiveLayoutMode, layout: ThemeLayout) {
      layout.placements.forEach { (slot, placement) ->
        val path = "layouts.${mode.name}.placements.${slot.name}"
        validateAnchor(mode, "$mode.$slot", placement.anchor, layout.placements, slot, "$path.anchor")
        if (placement.parentSlot == slot ||
            (placement.parentSlot != null && placement.parentSlot !in layout.placements)) {
          add(ThemeValidationIssue.MissingSlotReference(mode, slot, placement.parentSlot ?: slot))
        }
        validateSize("$mode.$slot", placement.sizeMeters, "$path.sizeMeters")
        validateShape("$mode.$slot", placement.shape, "$path.shape")
        validateOffsetAndRotation("$mode.$slot", placement.offsetMeters, placement.rotationDegrees, path)
        if (slot == PanelSlot.TRANSPORT &&
            (placement.parentSlot != PanelSlot.MEDIA_STAGE ||
                placement.depthRelation != SpatialDepthRelation.FRONT_OF_PARENT)) {
          add(ThemeValidationIssue.InvalidTransportOverlay(mode))
        }
      }
      layout.pipDocks.forEach { (id, dock) ->
        val path = "layouts.${mode.name}.pipDocks.$id"
        if (id.isBlank() || dock.id != id || !dock.sizeMeters.isValidSize()) {
          add(ThemeValidationIssue.InvalidPipDock(id))
        }
        validateAnchor(mode, "$mode.pipDocks.$id", dock.anchor, layout.placements, null, "$path.anchor")
        validateAnchor(
            mode,
            "$mode.pipDocks.$id.exclusion",
            dock.exclusion.anchor,
            layout.placements,
            null,
            "$path.exclusion.anchor",
        )
        if (dock.exclusion.id.isBlank()) add(ThemeValidationIssue.InvalidPipDock(id))
      }
    }

    private fun validateToolbarModules() {
      theme.toolbarModules.groupBy(ToolbarModule::id).forEach { (id, modules) ->
        if (id.isBlank() || modules.size > 1) add(ThemeValidationIssue.DuplicateToolbarModuleId(id))
      }
    }

    private fun validateComponentGroups() {
      theme.componentGroups.groupBy(ComponentGroup::id).forEach { (id, groups) ->
        if (id.isBlank() || groups.size > 1) add(ThemeValidationIssue.DuplicateComponentGroupId(id))
        groups.forEach { group ->
          group.members.forEach { validateComponentTree(it) }
          addAll(ComponentCompositionResolver.resolve(group, catalogs.components).issues)
        }
      }
    }

    private fun validateRequiredAnchors() {
      val catalog = catalogs.sceneAnchors ?: return
      theme.requiredAnchors.forEach { anchor ->
        if (!catalog.contains(anchor)) {
          add(
              ThemeValidationIssue.MissingSceneAnchor(
                  anchor,
                  "scene.requiredAnchors",
                  "scene.requiredAnchors[$anchor]",
              )
          )
        }
      }
    }

    private fun validatePanels() {
      theme.panels.forEach { (key, panel) ->
        val path = "panels.${key.value}"
        if (key != panel.id) add(ThemeValidationIssue.InvalidThemeIdentity("$path.id"))
        validatePanelComponent(panel, path)
        validatePanelPlacement(panel, path)
        validatePanelGeometry(panel, path)
      }
      validatePanelParentCycles()
    }

    private fun validatePanelComponent(panel: PanelSpec, path: String) {
      val registry = catalogs.components ?: return
      val descriptor = registry.descriptor(panel.componentId)
      if (descriptor == null) {
        add(ThemeValidationIssue.UnknownComponent(path, panel.componentId.value, "$path.component"))
      } else if (panel.panelClass !in descriptor.compatiblePanelClasses) {
        add(
            ThemeValidationIssue.ComponentClassMismatch(
                panel.id.value,
                panel.componentId.value,
                panel.panelClass,
                "$path.component",
            )
        )
      }
    }

    private fun validatePanelPlacement(panel: PanelSpec, path: String) {
      val placement = panel.placement
      if (placement.anchor == null && panel.parentPanelId == null && placement.parentSlot == null) {
        add(ThemeValidationIssue.InvalidAnchor(path, "$path.anchor"))
      }
      if (panel.parentPanelId != null && placement.anchor != null) {
        // `anchor` and `parent.panel` are two competing reference frames for the same placement.
        add(ThemeValidationIssue.AmbiguousPlacement(panel.id.value, "$path.parent"))
      }
      if (panel.parentPanelId != null && panel.parentPanelId !in theme.panels) {
        add(ThemeValidationIssue.UnknownPanelRef(null, panel.parentPanelId.value, "$path.parent.panel"))
      }
      if (placement.depthRelation != SpatialDepthRelation.ROOT &&
          panel.parentPanelId == null &&
          placement.parentSlot == null) {
        add(ThemeValidationIssue.DepthRelationWithoutParent(panel.id.value, "$path.depthRelation"))
      }
      validateAnchorRef(placement.anchor, "$path.anchor")
    }

    private fun validatePanelGeometry(panel: PanelSpec, path: String) {
      val placement = panel.placement
      validateSize(panel.id.value, placement.sizeMeters, "$path.sizeMeters")
      validateShape(panel.id.value, placement.shape, "$path.shape")
      validateOffsetAndRotation(panel.id.value, placement.offsetMeters, placement.rotationDegrees, path)
      if (panel.zOrder !in MIN_Z_ORDER..MAX_Z_ORDER) {
        add(ThemeValidationIssue.ZOrderOutOfRange(panel.id.value, panel.zOrder, "$path.zOrder"))
      }
      if (panel.hitTest == PanelHitTest.NONE && panel.userAdjustable.isNotEmpty()) {
        // A non-hittable panel cannot be resized by the user (design §5.3).
        add(ThemeValidationIssue.HitTestConflict(panel.id.value, "$path.hitTest"))
      }
      panel.themeableGeometry.forEach { property ->
        if (!panel.canTheme(property)) {
          add(
              ThemeValidationIssue.GeometryNotThemeable(
                  panel.id.value,
                  property,
                  panel.panelClass,
                  "$path.themeableGeometry",
              )
          )
        }
      }
      // A declared non-default geometry value must be inside the themeable whitelist, otherwise the
      // theme would advertise geometry that its panel class silently ignores.
      val offset = placement.offsetMeters
      if (offset != Meters3() && !panel.isThemeStatic(ThemeableGeometry.OFFSET_METERS)) {
        add(geometryIssue(panel, ThemeableGeometry.OFFSET_METERS, "$path.offsetMeters"))
      }
      if (placement.rotationDegrees != EulerDegrees() && !panel.isThemeStatic(ThemeableGeometry.ROTATION_DEGREES)) {
        add(geometryIssue(panel, ThemeableGeometry.ROTATION_DEGREES, "$path.rotationDegrees"))
      }
      if (placement.shape != PanelShape.Quad && !panel.isThemeStatic(ThemeableGeometry.SHAPE)) {
        add(geometryIssue(panel, ThemeableGeometry.SHAPE, "$path.shape"))
      }
      if (panel.zOrder != 0 && !panel.isThemeStatic(ThemeableGeometry.Z_ORDER)) {
        add(geometryIssue(panel, ThemeableGeometry.Z_ORDER, "$path.zOrder"))
      }
      if (panel.hitTest != panel.panelClass.defaultHitTest &&
          !panel.isThemeStatic(ThemeableGeometry.HIT_TEST)) {
        add(geometryIssue(panel, ThemeableGeometry.HIT_TEST, "$path.hitTest"))
      }
      if (panel.overflow != panel.panelClass.defaultOverflow &&
          !panel.isThemeStatic(ThemeableGeometry.OVERFLOW)) {
        add(geometryIssue(panel, ThemeableGeometry.OVERFLOW, "$path.overflow"))
      }
    }

    private fun geometryIssue(
        panel: PanelSpec,
        property: ThemeableGeometry,
        path: String,
    ): ThemeValidationIssue =
        ThemeValidationIssue.GeometryNotThemeable(panel.id.value, property, panel.panelClass, path)

    private fun validatePanelParentCycles() {
      val visiting = linkedSetOf<PanelId>()
      val settled = mutableSetOf<PanelId>()
      val reported = mutableSetOf<String>()

      fun visit(id: PanelId, chain: List<PanelId>) {
        if (id in settled) return
        if (!visiting.add(id)) {
          val cycle = chain.dropWhile { it != id } + id
          val key = cycle.map(PanelId::value).sorted().joinToString(",")
          if (reported.add(key)) {
            add(
                ThemeValidationIssue.ParentCycle(
                    cycle.map(PanelId::value),
                    "panels.${id.value}.parent.panel",
                )
            )
          }
          return
        }
        theme.panels[id]?.parentPanelId?.let { parent -> visit(parent, chain + id) }
        visiting.remove(id)
        settled += id
      }

      theme.panels.keys.forEach { visit(it, emptyList()) }
    }

    private fun validateSlotBindings() {
      val bindingsBySlot = theme.slotBindings.groupBy(SlotBinding::slot)
      bindingsBySlot.forEach { (slot, bindings) ->
        bindings.forEach { binding ->
          if (binding.panelId !in theme.panels) {
            add(
                ThemeValidationIssue.UnknownPanelRef(
                    slot,
                    binding.panelId.value,
                    "slotBindings[${slot.name}->${binding.panelId.value}].panel",
                )
            )
          }
        }

        // A slot is bound by *panels*, not by binding entries. Declaring the same panel twice is a
        // redundant claim that no share policy can legitimise, and it must never be usable to satisfy
        // the "MIRRORED needs a pair" rule (R7.2/A8). Only the first claim per panel takes part in the
        // policy checks below.
        if (bindings.groupBy(SlotBinding::panelId).any { (_, claims) -> claims.size > 1 }) {
          add(
              ThemeValidationIssue.DuplicateSlotOwner(
                  slot,
                  bindings.map { it.panelId.value },
                  "slotBindings[${slot.name}]",
              )
          )
        }
        val claims = bindings.distinctBy(SlotBinding::panelId)

        if (claims.size == 1) {
          val binding = claims.single()
          if (binding.sharePolicy == SlotSharePolicy.MIRRORED) {
            add(mirrorIssue(binding, "mirrored-requires-at-least-two-bindings"))
          }
          return@forEach
        }

        val exclusive = claims.filter { it.sharePolicy == SlotSharePolicy.EXCLUSIVE }
        if (exclusive.isNotEmpty()) {
          add(
              ThemeValidationIssue.DuplicateSlotOwner(
                  slot,
                  claims.map { it.panelId.value },
                  "slotBindings[${slot.name}]",
              )
          )
          return@forEach
        }

        val policies = claims.map(SlotBinding::sharePolicy).toSet()
        if (policies.size > 1) {
          add(
              ThemeValidationIssue.DuplicateSlotOwner(
                  slot,
                  claims.map { it.panelId.value },
                  "slotBindings[${slot.name}].sharePolicy",
              )
          )
          return@forEach
        }

        if (claims.first().sharePolicy != SlotSharePolicy.MIRRORED) return@forEach

        claims.forEach { binding ->
          if (binding.mirrorGroup.isNullOrBlank()) {
            add(mirrorIssue(binding, "mirrored-requires-mirror-group"))
          }
        }
        val groups = claims.map { it.mirrorGroup }.filterNotNull().distinct()
        if (groups.size != 1) {
          add(
              ThemeValidationIssue.IncompleteMirrorGroup(
                  slot,
                  groups.joinToString(","),
                  "mirrored-bindings-must-share-one-mirror-group",
                  "slotBindings[${slot.name}].mirrorGroup",
              )
          )
        }
      }
    }

    private fun mirrorIssue(
        binding: SlotBinding,
        reason: String,
    ): ThemeValidationIssue =
        ThemeValidationIssue.IncompleteMirrorGroup(
            binding.slot,
            binding.mirrorGroup.orEmpty(),
            reason,
            binding.path + ".mirrorGroup",
        )

    private fun validateGrabGroups() {
      theme.grabGroups.groupBy(GrabGroupSpec::id).forEach { (id, groups) ->
        if (id.isBlank() || groups.size > 1) add(ThemeValidationIssue.DuplicateGrabGroupId(id))
      }
      val byId = theme.grabGroups.associateBy(GrabGroupSpec::id)
      val entityCatalog = catalogs.entities

      theme.grabGroups.forEach { group ->
        val path = "grabGroups.${group.id}"
        if (entityCatalog != null && !entityCatalog.contains(group.handleRef)) {
          add(ThemeValidationIssue.UnknownEntityRef(path, group.handleRef.value, "$path.handleRef"))
        }
        group.members.forEach { member ->
          if (entityCatalog != null && !entityCatalog.contains(member.entityRef)) {
            add(
                ThemeValidationIssue.UnknownEntityRef(
                    path,
                    member.entityRef.value,
                    "$path.members[${member.entityRef.value}]",
                )
            )
          }
          if (member.entityRef == group.handleRef) {
            add(
                ThemeValidationIssue.GrabHandleSelfReference(
                    group.id,
                    member.entityRef.value,
                    "$path.members[${member.entityRef.value}]",
                )
            )
          }
        }
        group.parentGroupId?.let { parentId ->
          if (parentId !in byId) {
            add(ThemeValidationIssue.UnknownGrabGroupRef(group.id, parentId, "$path.parentGroupId"))
          } else {
            val parent = byId.getValue(parentId)
            (parent.memberRefs intersect group.memberRefs).forEach { ref ->
              // The same member in a parent and its child group would apply the delta twice.
              add(
                  ThemeValidationIssue.GrabMemberOverlap(
                      group.id,
                      parentId,
                      ref.value,
                      "$path.members[${ref.value}]",
                  )
              )
            }
          }
        }
        group.exclusiveWith.forEach { other ->
          val peer = byId[other]
          if (peer == null || group.id !in peer.exclusiveWith) {
            add(
                ThemeValidationIssue.AsymmetricExclusion(
                    group.id,
                    other,
                    "$path.exclusiveWith[$other]",
                )
            )
          }
        }
        // Absence of a positive verification record means "unverified", never "verified": the SDK
        // parent-child propagation has no headset verification record (design §4.3, Phase 3.0 gate).
        // `DELTA_MIRROR` does not depend on that propagation, so only `NATIVE_PARENT` is reported.
        val verified = catalogs.grabVerification?.isVerified(group.id, group.mechanism) == true
        if (group.mechanism == GrabMechanism.NATIVE_PARENT && !verified) {
          add(
              ThemeValidationIssue.UnverifiedGrabMechanism(
                  group.id,
                  group.mechanism,
                  "$path.mechanism",
              )
          )
        }
      }

      validateGrabGroupCycles(byId)
    }

    private fun validateGrabGroupCycles(byId: Map<String, GrabGroupSpec>) {
      val visiting = linkedSetOf<String>()
      val settled = mutableSetOf<String>()
      val reported = mutableSetOf<String>()

      fun visit(id: String, chain: List<String>) {
        if (id in settled) return
        if (!visiting.add(id)) {
          val cycle = chain.dropWhile { it != id } + id
          val key = cycle.sorted().joinToString(",")
          if (reported.add(key)) {
            add(ThemeValidationIssue.GrabGroupParentCycle(cycle, "grabGroups.$id.parentGroupId"))
          }
          return
        }
        byId[id]?.parentGroupId?.let { parent -> if (parent in byId) visit(parent, chain + id) }
        visiting.remove(id)
        settled += id
      }

      byId.keys.forEach { visit(it, emptyList()) }
    }

    private fun validateCanvas(canvas: WorkbenchCanvas, componentIds: Set<String>) {
      if (canvas.id.isBlank() || theme.layouts[canvas.mode] == null || canvas.visibleSlots.isEmpty()) {
        add(ThemeValidationIssue.InvalidCanvas(canvas.id))
        return
      }
      if (canvas.overflowPolicy == CanvasOverflowPolicy.VISIBLE && !canvas.preservesHitTesting) {
        add(ThemeValidationIssue.InvalidCanvas(canvas.id))
      }
      val layout = theme.layouts.getValue(canvas.mode)
      if (canvas.visibleSlots.any { it !in layout.placements } ||
          canvas.visibleComponentIds.any { it !in componentIds }) {
        add(ThemeValidationIssue.InvalidCanvas(canvas.id))
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

    private fun validatePalette(palette: CinemaPalette) {
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
      if (colors.any { !it.isValid() }) add(ThemeValidationIssue.InvalidPalette("channel-range"))
      if (!palette.surfaceOpacity.isFinite() || palette.surfaceOpacity !in 0f..1f) {
        add(ThemeValidationIssue.InvalidPalette("surface-opacity"))
      }
      if (palette.normalText.contrastAgainst(palette.surface) < 4.5) {
        add(ThemeValidationIssue.InvalidPalette("normal-text-contrast"))
      }
      if (palette.primaryButtonLabel.contrastAgainst(palette.primaryButton) < 4.5) {
        add(ThemeValidationIssue.InvalidPalette("primary-button-label-contrast"))
      }
      if (palette.secondaryButtonLabel.contrastAgainst(palette.secondaryButton) < 4.5) {
        add(ThemeValidationIssue.InvalidPalette("secondary-button-label-contrast"))
      }
      if (palette.chargingBadgeLabel.contrastAgainst(palette.chargingBadge) < 4.5) {
        add(ThemeValidationIssue.InvalidPalette("charging-badge-label-contrast"))
      }
    }

    private fun validateAnchor(
        mode: ImmersiveLayoutMode,
        context: String,
        anchor: SpatialAnchor?,
        placements: Map<PanelSlot, SlotPlacement>,
        owner: PanelSlot?,
        path: String,
    ) {
      if (anchor == null) {
        // A legacy slot placement must still say where it lives: a scene anchor or a parent slot.
        if (owner != null && placements[owner]?.parentSlot == null) {
          add(ThemeValidationIssue.InvalidAnchor(context, path))
        }
        return
      }
      when (anchor) {
        is SpatialAnchor.SceneAnchor -> {
          if (anchor.name.isBlank()) add(ThemeValidationIssue.InvalidAnchor(context, path))
          validateAnchorRef(anchor, path)
        }
        is SpatialAnchor.SlotAnchor -> {
          if (anchor.slot !in placements || anchor.slot == owner) {
            add(ThemeValidationIssue.MissingSlotReference(mode, owner ?: anchor.slot, anchor.slot))
          }
        }
      }
    }

    private fun validateAnchorRef(anchor: SpatialAnchor?, path: String) {
      val catalog = catalogs.sceneAnchors ?: return
      if (anchor is SpatialAnchor.SceneAnchor && anchor.name.isNotBlank() && !catalog.contains(anchor.name)) {
        add(ThemeValidationIssue.MissingSceneAnchor(anchor.name, path, path))
      }
    }

    private fun validateComponentTree(root: ComponentNode) {
      val ids = mutableSetOf<String>()
      fun visit(node: ComponentNode) {
        if (node.id.isBlank() || !ids.add(node.id)) add(ThemeValidationIssue.DuplicateComponentId(node.id))
        node.dataBindings.values.forEach { binding ->
          if (!bindingPattern.matches(binding)) {
            add(ThemeValidationIssue.InvalidDataBinding(node.id, binding))
          }
        }
        node.actions.forEach { action ->
          if (action is ThemeAction.SetPlaybackSpeed &&
              (!action.speed.isFinite() || action.speed !in 0.25f..4f)) {
            add(ThemeValidationIssue.InvalidAction(node.id, action))
          }
        }
        node.children.forEach(::visit)
        node.namedSlots.values.flatten().forEach(::visit)
      }
      visit(root)
    }

    private fun validateSize(context: String, size: PanelSizeMeters, path: String) {
      if (!size.isValidSize()) add(ThemeValidationIssue.InvalidPanelSize(context, path))
    }

    private fun validateShape(context: String, shape: PanelShape, path: String) {
      if (!shape.isValidShape()) add(ThemeValidationIssue.InvalidCylinderGeometry(context, path))
    }

    private fun validateOffsetAndRotation(
        owner: String,
        offset: Meters3,
        rotation: EulerDegrees,
        basePath: String,
    ) {
      listOf("x" to offset.x, "y" to offset.y, "z" to offset.z).forEach { (axis, value) ->
        if (!value.isFinite() || kotlin.math.abs(value) > MAX_OFFSET_METERS) {
          add(
              ThemeValidationIssue.OffsetOutOfRange(
                  owner,
                  axis,
                  value,
                  "$basePath.offsetMeters.$axis",
              )
          )
        }
      }
      listOf(
              "pitch" to (rotation.pitch to MAX_PITCH_ROLL_DEGREES),
              "yaw" to (rotation.yaw to MAX_YAW_DEGREES),
              "roll" to (rotation.roll to MAX_PITCH_ROLL_DEGREES),
          )
          .forEach { (axis, pair) ->
            val (value, limit) = pair
            if (!value.isFinite() || kotlin.math.abs(value) > limit) {
              add(
                  ThemeValidationIssue.RotationOutOfRange(
                      owner,
                      axis,
                      value,
                      "$basePath.rotationDegrees.$axis",
                  )
              )
            }
          }
    }

    private fun addAll(newIssues: List<ThemeValidationIssue>) {
      newIssues.forEach(::add)
    }

    private fun PanelSizeMeters.isValidSize(): Boolean =
        width.isFinite() &&
            height.isFinite() &&
            width in MIN_PANEL_SIZE_METERS..MAX_PANEL_SIZE_METERS &&
            height in MIN_PANEL_SIZE_METERS..MAX_PANEL_SIZE_METERS

    private fun PanelShape.isValidShape(): Boolean =
        when (this) {
          PanelShape.Quad -> true
          is PanelShape.Cylinder ->
              radiusMeters.isFinite() &&
                  radiusMeters in MIN_CYLINDER_RADIUS_METERS..MAX_CYLINDER_RADIUS_METERS &&
                  arcDegrees.isFinite() &&
                  arcDegrees in MIN_CYLINDER_ARC_DEGREES..MAX_CYLINDER_ARC_DEGREES
        }
  }
}
