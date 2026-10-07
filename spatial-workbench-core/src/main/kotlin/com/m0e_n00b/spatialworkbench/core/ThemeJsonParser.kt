package com.m0e_n00b.spatialworkbench.core

/*
 * `ThemeJsonParser` (R3.1): JSON document -> normalized [SpatialTheme].
 *
 * It maps the schema of design §5.2 onto the existing contract types (`SlotPlacement`, `ThemeLayout`,
 * `ComponentNode`, `DanmakuSurfaceGroup`) — there is no second theme model. Every problem it reports
 * is locatable: either a [ThemeValidationIssue.MalformedField] with a JSON field path such as
 * `panels.transport.offsetMeters.y`, or one of the regular issues (`UnknownPanelClass`, ...) carrying
 * the same path.
 *
 * Sources: the caller passes a `String`. There is no file, asset, network or reflection access here
 * (R2.4), so a theme can only ever come from a locally bundled resource.
 */

data class ThemeParseResult(
    val theme: SpatialTheme?,
    val issues: List<ThemeValidationIssue> = emptyList(),
) {
  /** True when the document parsed and produced no parser-level issue. */
  val isUsable: Boolean
    get() = theme != null && issues.none { it.severity == ThemeIssueSeverity.ERROR }
}

object ThemeJsonParser {
  fun parse(text: String): ThemeParseResult = parse(text, ThemeSchemaMigration.DEFAULT)

  fun parse(
      text: String,
      migration: ThemeSchemaMigrationChain,
  ): ThemeParseResult {
    val read = ThemeJsonReader.read(text)
    if (read is ThemeJsonReadResult.Malformed) {
      return ThemeParseResult(
          null,
          listOf(
              ThemeValidationIssue.MalformedField(
                  "$",
                  "malformed-json:${read.reason}@${read.offset}",
              )
          ),
      )
    }
    val root = (read as ThemeJsonReadResult.Parsed).node as? ThemeJsonNode.Obj
        ?: return ThemeParseResult(null, listOf(ThemeValidationIssue.MalformedField("$", "expected-object")))

    val issues = mutableListOf<ThemeValidationIssue>()
    val mapper = Mapper(issues)
    val declaredVersion = mapper.declaredSchemaVersion(root)
    if (declaredVersion == null) {
      return ThemeParseResult(null, issues)
    }

    // Migration runs before validation; an unsupported version is rejected, never downgraded (R2.2/R2.3).
    val migrated = migration.migrate(root, declaredVersion)
    issues += migrated.issues
    val document = migrated.document ?: return ThemeParseResult(null, issues)
    val migratedVersionNode = document.get("schemaVersion")
    val reachedVersion =
        (migratedVersionNode as? ThemeJsonNode.Num)?.value?.toInt() ?: declaredVersion
    if (!ThemeSchema.isSupported(reachedVersion)) {
      issues +=
          ThemeValidationIssue.UnsupportedSchemaVersion(
              reachedVersion,
              "unsupported-after-migration",
          )
      return ThemeParseResult(null, issues)
    }

    return ThemeParseResult(mapper.theme(document), issues)
  }

  private class Mapper(private val issues: MutableList<ThemeValidationIssue>) {

    /** Reads the required `schemaVersion`; reports a locatable issue when it is missing or not an int. */
    fun declaredSchemaVersion(document: ThemeJsonNode.Obj): Int? {
      val node = document.get("schemaVersion")
      if (node == null || node is ThemeJsonNode.Null) {
        issues += ThemeValidationIssue.MalformedField("schemaVersion", "missing")
        return null
      }
      return intAt(node, "schemaVersion")
    }

    fun theme(document: ThemeJsonNode.Obj): SpatialTheme {
      val scene = objectAt(document.get("scene"), "scene")
      val layouts = linkedMapOf<ImmersiveLayoutMode, ThemeLayout>()
      objectAt(document.get("layouts"), "layouts")?.values?.forEach { (name, node) ->
        val path = "layouts.$name"
        val mode = enumOrNull<ImmersiveLayoutMode>(name, path) ?: return@forEach
        val layoutObj = objectAt(node, path) ?: return@forEach
        layouts[mode] = layout(layoutObj, path)
      }
      // Top-level `panels`/`slotBindings` are the theme-level panel registry of design §5.2.
      val panels = linkedMapOf<PanelId, PanelSpec>()
      objectAt(document.get("panels"), "panels")?.values?.forEach { (id, node) ->
        val path = "panels.$id"
        val panelObj = objectAt(node, path) ?: return@forEach
        panel(id, panelObj, path)?.let { panels[it.id] = it }
      }
      val bindings = mutableListOf<SlotBinding>()
      arrayAt(document.get("slotBindings"), "slotBindings")?.values?.forEachIndexed { index, node ->
        val path = "slotBindings[$index]"
        slotBinding(objectAt(node, path) ?: return@forEachIndexed, path)?.let { bindings += it }
      }
      return SpatialTheme(
          id = stringAt(document.get("id"), "id") ?: "",
          displayName = stringAt(document.get("displayName"), "displayName") ?: "",
          sceneReference = scene?.let { stringAt(it.get("reference"), "scene.reference") } ?: "",
          layouts = layouts,
          toolbarModules = toolbarModules(document.get("toolbarModules")),
          rootComponent =
              componentNode(objectAt(document.get("rootComponent"), "rootComponent"), "rootComponent")
                  ?: ComponentNode("root", ComponentKind.PANEL),
          componentGroups = componentGroups(document.get("componentGroups")),
          canvases = canvases(document.get("canvases")),
          presentationPolicies = presentationPolicies(document.get("presentationPolicies")),
          palette = palette(document.get("palette")),
          schemaVersion =
              intAt(document.get("schemaVersion"), "schemaVersion") ?: ThemeSchema.CURRENT_SCHEMA_VERSION,
          requiredAnchors = stringSet(scene?.get("requiredAnchors"), "scene.requiredAnchors"),
          panels = panels,
          slotBindings = bindings,
          grabGroups = grabGroups(document.get("grabGroups")),
          danmakuGroups = danmakuGroups(document.get("danmakuGroups")),
          danmakuOcclusionDomains = occlusionDomains(document.get("danmakuOcclusionDomains")),
      )
    }

    private fun layout(source: ThemeJsonNode.Obj, path: String): ThemeLayout {
      val placements = linkedMapOf<PanelSlot, SlotPlacement>()
      objectAt(source.get("placements"), "$path.placements")?.values?.forEach { (name, node) ->
        val placementPath = "$path.placements.$name"
        val slot = enumOrNull<PanelSlot>(name, placementPath) ?: return@forEach
        val placementObj = objectAt(node, placementPath) ?: return@forEach
        placements[slot] = placement(placementObj, placementPath)
      }
      val docks = linkedMapOf<String, PipDock>()
      objectAt(source.get("pipDocks"), "$path.pipDocks")?.values?.forEach { (id, node) ->
        val dockPath = "$path.pipDocks.$id"
        val dockObj = objectAt(node, dockPath) ?: return@forEach
        docks[id] =
            PipDock(
                id = id,
                anchor = anchor(dockObj.get("anchor"), "$dockPath.anchor") ?: SpatialAnchor.SceneAnchor(""),
                sizeMeters = size(dockObj.get("sizeMeters"), "$dockPath.sizeMeters"),
                exclusion =
                    objectAt(dockObj.get("exclusion"), "$dockPath.exclusion")?.let { exclusion ->
                      ContentExclusion(
                          id = stringAt(exclusion.get("id"), "$dockPath.exclusion.id") ?: "",
                          anchor =
                              anchor(exclusion.get("anchor"), "$dockPath.exclusion.anchor")
                                  ?: SpatialAnchor.SceneAnchor(""),
                      )
                    } ?: ContentExclusion("", SpatialAnchor.SceneAnchor("")),
            )
      }
      return ThemeLayout(placements = placements, pipDocks = docks)
    }

    private fun placement(source: ThemeJsonNode.Obj, path: String): SlotPlacement {
      val visibility =
          enumOrNull<PanelVisibility>(stringAt(source.get("visibility"), "$path.visibility"), "$path.visibility")
              ?: PanelVisibility.VISIBLE
      val depth =
          enumOrNull<SpatialDepthRelation>(
              stringAt(source.get("depthRelation"), "$path.depthRelation"),
              "$path.depthRelation",
          ) ?: SpatialDepthRelation.ROOT
      return SlotPlacement(
          visibility = visibility,
          anchor = anchor(source.get("anchor"), "$path.anchor"),
          offsetMeters = meters(source.get("offsetMeters"), "$path.offsetMeters"),
          rotationDegrees = euler(source.get("rotationDegrees"), "$path.rotationDegrees"),
          sizeMeters = size(source.get("sizeMeters"), "$path.sizeMeters"),
          shape = shape(source.get("shape"), "$path.shape"),
          parentSlot =
              stringAt(source.get("parentSlot"), "$path.parentSlot")?.let { name ->
                enumOrNull<PanelSlot>(name, "$path.parentSlot")
              },
          depthRelation = depth,
      )
    }

    private fun panel(id: String, source: ThemeJsonNode.Obj, path: String): PanelSpec? {
      val rawClass = stringAt(source.get("panelClass"), "$path.panelClass")
      val panelClass = enumOrNull<PanelClass>(rawClass, "$path.panelClass")
      if (panelClass == null) {
        // The class decides which geometry path can apply, so a panel without a known class cannot
        // be represented at all (R7.4).
        issues +=
            ThemeValidationIssue.UnknownPanelClass(id, rawClass ?: "", "$path.panelClass")
        return null
      }
      val component = stringAt(source.get("component"), "$path.component")
      if (component == null) issues += ThemeValidationIssue.MalformedField("$path.component", "missing")
      val parentObj = objectAt(source.get("parent"), "$path.parent")
      return PanelSpec(
          id = PanelId(id),
          panelClass = panelClass,
          componentId = ComponentId(component ?: ""),
          placement =
              SlotPlacement(
                  visibility = PanelVisibility.VISIBLE,
                  anchor = anchor(source.get("anchor"), "$path.anchor"),
                  offsetMeters = meters(source.get("offsetMeters"), "$path.offsetMeters"),
                  rotationDegrees = euler(source.get("rotationDegrees"), "$path.rotationDegrees"),
                  sizeMeters = size(source.get("sizeMeters"), "$path.sizeMeters"),
                  shape = shape(source.get("shape"), "$path.shape"),
                  depthRelation =
                      enumOrNull<SpatialDepthRelation>(
                          stringAt(source.get("depthRelation"), "$path.depthRelation"),
                          "$path.depthRelation",
                      ) ?: SpatialDepthRelation.ROOT,
              ),
          zOrder = intAt(source.get("zOrder"), "$path.zOrder") ?: 0,
          hitTest =
              enumOrNull<PanelHitTest>(stringAt(source.get("hitTest"), "$path.hitTest"), "$path.hitTest")
                  ?: panelClass.defaultHitTest,
          overflow =
              enumOrNull<CanvasOverflowPolicy>(
                  stringAt(source.get("overflow"), "$path.overflow"),
                  "$path.overflow",
              ) ?: panelClass.defaultOverflow,
          themeableGeometry = geometrySet(source.get("themeableGeometry"), "$path.themeableGeometry"),
          userAdjustable = userAdjustableSet(source.get("userAdjustable"), "$path.userAdjustable"),
          parentPanelId =
              parentObj?.let { parent ->
                stringAt(parent.get("panel"), "$path.parent.panel")?.let(::PanelId)
              },
          parentAttach =
              parentObj?.let { parent ->
                stringAt(parent.get("attach"), "$path.parent.attach")?.let { name ->
                  AttachPoint.fromWireName(name)
                      ?: run {
                        issues += ThemeValidationIssue.MalformedField("$path.parent.attach", "unknown-attach:$name")
                        null
                      }
                }
              },
      )
    }

    private fun slotBinding(source: ThemeJsonNode.Obj, path: String): SlotBinding? {
      val slotName = stringAt(source.get("slot"), "$path.slot")
      val slot = enumOrNull<PanelSlot>(slotName, "$path.slot")
      if (slot == null) {
        issues += ThemeValidationIssue.MalformedField("$path.slot", "missing")
        return null
      }
      val panelName = stringAt(source.get("panel"), "$path.panel")
      if (panelName == null) issues += ThemeValidationIssue.MalformedField("$path.panel", "missing")
      val policy =
          enumOrNull<SlotSharePolicy>(stringAt(source.get("sharePolicy"), "$path.sharePolicy"), "$path.sharePolicy")
              ?: SlotSharePolicy.EXCLUSIVE
      return SlotBinding(
          slot = slot,
          panelId = PanelId(panelName ?: ""),
          sharePolicy = policy,
          mirrorGroup = stringAt(source.get("mirrorGroup"), "$path.mirrorGroup"),
      )
    }

    private fun toolbarModules(node: ThemeJsonNode?): List<ToolbarModule> =
        arrayAt(node, "toolbarModules")?.values?.mapIndexedNotNull { index, item ->
          val path = "toolbarModules[$index]"
          val obj = objectAt(item, path) ?: return@mapIndexedNotNull null
          val id = stringAt(obj.get("id"), "$path.id") ?: return@mapIndexedNotNull null
          val module =
              enumOrNull<SystemToolbarModule>(stringAt(obj.get("module"), "$path.module"), "$path.module")
                  ?: return@mapIndexedNotNull null
          ToolbarModule(id, module)
        } ?: emptyList()

    private fun componentGroups(node: ThemeJsonNode?): List<ComponentGroup> =
        arrayAt(node, "componentGroups")?.values?.mapIndexedNotNull { index, item ->
          val path = "componentGroups[$index]"
          val obj = objectAt(item, path) ?: return@mapIndexedNotNull null
          val id = stringAt(obj.get("id"), "$path.id") ?: return@mapIndexedNotNull null
          ComponentGroup(
              id = id,
              members = componentList(obj.get("members"), "$path.members"),
              label = stringAt(obj.get("label"), "$path.label") ?: "",
              allowedSlots =
                  stringSet(obj.get("allowedSlots"), "$path.allowedSlots").mapNotNullTo(linkedSetOf()) { name ->
                    enumOrNull<PanelSlot>(name, "$path.allowedSlots")
                  },
              reusable = booleanAt(obj.get("reusable"), "$path.reusable") ?: true,
          )
        } ?: emptyList()

    private fun componentList(node: ThemeJsonNode?, path: String): List<ComponentNode> =
        arrayAt(node, path)?.values?.mapIndexedNotNull { index, item ->
          componentNode(objectAt(item, "$path[$index]"), "$path[$index]")
        } ?: emptyList()

    private fun componentNode(source: ThemeJsonNode.Obj?, path: String): ComponentNode? {
      if (source == null) return null
      val id = stringAt(source.get("id"), "$path.id") ?: return null
      val kind = enumOrNull<ComponentKind>(stringAt(source.get("kind"), "$path.kind"), "$path.kind")
      if (kind == null) issues += ThemeValidationIssue.MalformedField("$path.kind", "missing")
      val namedSlots = linkedMapOf<String, List<ComponentNode>>()
      objectAt(source.get("namedSlots"), "$path.namedSlots")?.values?.forEach { (name, slotNode) ->
        namedSlots[name] = componentList(slotNode, "$path.namedSlots.$name")
      }
      return ComponentNode(
          id = id,
          kind = kind ?: ComponentKind.PANEL,
          properties = stringMap(source.get("properties"), "$path.properties"),
          dataBindings = stringMap(source.get("dataBindings"), "$path.dataBindings"),
          actions = actions(source.get("actions"), "$path.actions"),
          children = componentList(source.get("children"), "$path.children"),
          namedSlots = namedSlots,
      )
    }

    private fun actions(node: ThemeJsonNode?, path: String): List<ThemeAction> =
        arrayAt(node, path)?.values?.mapIndexedNotNull { index, item ->
          val actionPath = "$path[$index]"
          val obj = objectAt(item, actionPath) ?: return@mapIndexedNotNull null
          when (val type = stringAt(obj.get("type"), "$actionPath.type")) {
            "OpenRoute" ->
                enumOrNull<ThemeRoute>(stringAt(obj.get("route"), "$actionPath.route"), "$actionPath.route")
                    ?.let(ThemeAction::OpenRoute)
            "OpenFocus" ->
                enumOrNull<FocusTarget>(stringAt(obj.get("target"), "$actionPath.target"), "$actionPath.target")
                    ?.let(ThemeAction::OpenFocus)
            "SetPlaybackSpeed" ->
                numberAt(obj.get("speed"), "$actionPath.speed")?.let { ThemeAction.SetPlaybackSpeed(it.toFloat()) }
            "SetDanmakuMode" ->
                enumOrNull<DanmakuMode>(stringAt(obj.get("mode"), "$actionPath.mode"), "$actionPath.mode")
                    ?.let(ThemeAction::SetDanmakuMode)
            "SwitchTo2D" -> ThemeAction.SwitchTo2D
            "TogglePassthrough" -> ThemeAction.TogglePassthrough
            else -> {
              issues += ThemeValidationIssue.MalformedField("$actionPath.type", "unknown-action:${type ?: ""}")
              null
            }
          }
        } ?: emptyList()

    private fun canvases(node: ThemeJsonNode?): List<WorkbenchCanvas> =
        arrayAt(node, "canvases")?.values?.mapIndexedNotNull { index, item ->
          val path = "canvases[$index]"
          val obj = objectAt(item, path) ?: return@mapIndexedNotNull null
          val id = stringAt(obj.get("id"), "$path.id") ?: return@mapIndexedNotNull null
          val mode =
              enumOrNull<ImmersiveLayoutMode>(stringAt(obj.get("mode"), "$path.mode"), "$path.mode")
                  ?: return@mapIndexedNotNull null
          WorkbenchCanvas(
              id = id,
              mode = mode,
              visibleSlots =
                  stringSet(obj.get("visibleSlots"), "$path.visibleSlots").mapNotNullTo(linkedSetOf()) { name ->
                    enumOrNull<PanelSlot>(name, "$path.visibleSlots")
                  },
              visibleComponentIds = stringSet(obj.get("visibleComponentIds"), "$path.visibleComponentIds"),
              overflowPolicy =
                  enumOrNull<CanvasOverflowPolicy>(
                      stringAt(obj.get("overflowPolicy"), "$path.overflowPolicy"),
                      "$path.overflowPolicy",
                  ) ?: CanvasOverflowPolicy.CLIP,
              preservesHitTesting = booleanAt(obj.get("preservesHitTesting"), "$path.preservesHitTesting") ?: true,
          )
        } ?: emptyList()

    private fun presentationPolicies(node: ThemeJsonNode?): Map<PanelSlot, PanelPresentationPolicy> {
      val policies = linkedMapOf<PanelSlot, PanelPresentationPolicy>()
      objectAt(node, "presentationPolicies")?.values?.forEach { (name, value) ->
        val path = "presentationPolicies.$name"
        val slot = enumOrNull<PanelSlot>(name, path) ?: return@forEach
        val policy =
            enumOrNull<PanelPresentationPolicy>(stringAt(value, path), path) ?: return@forEach
        policies[slot] = policy
      }
      return policies
    }

    private fun palette(node: ThemeJsonNode?): CinemaPalette {
      val obj = objectAt(node, "palette") ?: return CinemaPalette.DARK
      stringAt(obj.get("preset"), "palette.preset")?.let { preset ->
        return when (preset.uppercase()) {
          "DARK" -> CinemaPalette.DARK
          "LIGHT" -> CinemaPalette.LIGHT
          "HIGH_CONTRAST" -> CinemaPalette.HIGH_CONTRAST
          else -> {
            issues += ThemeValidationIssue.MalformedField("palette.preset", "unknown-preset:$preset")
            CinemaPalette.DARK
          }
        }
      }
      fun color(key: String, fallback: RgbColor): RgbColor {
        val path = "palette.$key"
        val colorObj = objectAt(obj.get(key), path) ?: return fallback
        return RgbColor(
            red = intAt(colorObj.get("red"), "$path.red") ?: fallback.red,
            green = intAt(colorObj.get("green"), "$path.green") ?: fallback.green,
            blue = intAt(colorObj.get("blue"), "$path.blue") ?: fallback.blue,
        )
      }
      val defaults = CinemaPalette.DARK
      return CinemaPalette(
          background = color("background", defaults.background),
          surface = color("surface", defaults.surface),
          normalText = color("normalText", defaults.normalText),
          secondaryText = color("secondaryText", defaults.secondaryText),
          highlightText = color("highlightText", defaults.highlightText),
          primaryButton = color("primaryButton", defaults.primaryButton),
          primaryButtonLabel = color("primaryButtonLabel", defaults.primaryButtonLabel),
          secondaryButton = color("secondaryButton", defaults.secondaryButton),
          secondaryButtonLabel = color("secondaryButtonLabel", defaults.secondaryButtonLabel),
          border = color("border", defaults.border),
          danger = color("danger", defaults.danger),
          chargingBadge = color("chargingBadge", defaults.chargingBadge),
          chargingBadgeLabel = color("chargingBadgeLabel", defaults.chargingBadgeLabel),
          surfaceOpacity = numberAt(obj.get("surfaceOpacity"), "palette.surfaceOpacity")?.toFloat()
              ?: defaults.surfaceOpacity,
      )
    }

    private fun grabGroups(node: ThemeJsonNode?): List<GrabGroupSpec> =
        arrayAt(node, "grabGroups")?.values?.mapIndexedNotNull { index, item ->
          val path = "grabGroups[$index]"
          val obj = objectAt(item, path) ?: return@mapIndexedNotNull null
          val id = stringAt(obj.get("id"), "$path.id") ?: return@mapIndexedNotNull null
          val handle = stringAt(obj.get("handleRef"), "$path.handleRef")
          if (handle == null) issues += ThemeValidationIssue.MalformedField("$path.handleRef", "missing")
          GrabGroupSpec(
              id = id,
              handleRef = EntityRef(handle ?: ""),
              parentGroupId = stringAt(obj.get("parentGroupId"), "$path.parentGroupId"),
              mechanism =
                  enumOrNull<GrabMechanism>(stringAt(obj.get("mechanism"), "$path.mechanism"), "$path.mechanism")
                      ?: GrabMechanism.NATIVE_PARENT,
              members =
                  arrayAt(obj.get("members"), "$path.members")?.values?.mapIndexedNotNull { memberIndex, member ->
                    val memberPath = "$path.members[$memberIndex]"
                    val memberObj = objectAt(member, memberPath) ?: return@mapIndexedNotNull null
                    val ref = stringAt(memberObj.get("entityRef"), "$memberPath.entityRef")
                    if (ref == null) {
                      issues += ThemeValidationIssue.MalformedField("$memberPath.entityRef", "missing")
                    }
                    GrabMemberSpec(
                        entityRef = EntityRef(ref ?: ""),
                        follow =
                            enumOrNull<GrabFollowMode>(
                                stringAt(memberObj.get("follow"), "$memberPath.follow"),
                                "$memberPath.follow",
                            ) ?: GrabFollowMode.TRANSLATION_AND_ROTATION,
                        independentlyGrabbable =
                            booleanAt(memberObj.get("independentlyGrabbable"), "$memberPath.independentlyGrabbable")
                                ?: false,
                        lockRotation = booleanAt(memberObj.get("lockRotation"), "$memberPath.lockRotation") ?: false,
                        lockScale = booleanAt(memberObj.get("lockScale"), "$memberPath.lockScale") ?: true,
                    )
                  } ?: emptyList(),
              restPose =
                  objectAt(obj.get("restPose"), "$path.restPose")?.let { restPose ->
                    PoseSpec(
                        offsetMeters = meters(restPose.get("offsetMeters"), "$path.restPose.offsetMeters"),
                        rotationDegrees = euler(restPose.get("rotationDegrees"), "$path.restPose.rotationDegrees"),
                    )
                  },
              lifecycle =
                  enumOrNull<GrabGroupLifecycle>(
                      stringAt(obj.get("lifecycle"), "$path.lifecycle"),
                      "$path.lifecycle",
                  ) ?: GrabGroupLifecycle.ALWAYS,
              lostTrackingPolicy =
                  enumOrNull<GrabLostTrackingPolicy>(
                      stringAt(obj.get("lostTrackingPolicy"), "$path.lostTrackingPolicy"),
                      "$path.lostTrackingPolicy",
                  ) ?: GrabLostTrackingPolicy.FREEZE_AT_LAST_POSE,
              exclusiveWith = stringSet(obj.get("exclusiveWith"), "$path.exclusiveWith"),
              suppressOthersWhileGrabbed =
                  booleanAt(obj.get("suppressOthersWhileGrabbed"), "$path.suppressOthersWhileGrabbed") ?: true,
          )
        } ?: emptyList()

    private fun danmakuGroups(node: ThemeJsonNode?): List<DanmakuSurfaceGroup> =
        arrayAt(node, "danmakuGroups")?.values?.mapIndexedNotNull { index, item ->
          val path = "danmakuGroups[$index]"
          val obj = objectAt(item, path) ?: return@mapIndexedNotNull null
          val id = stringAt(obj.get("id"), "$path.id") ?: return@mapIndexedNotNull null
          DanmakuSurfaceGroup(
              id = id,
              allocationWeight = numberAt(obj.get("allocationWeight"), "$path.allocationWeight")?.toFloat() ?: 1f,
              sharedCoordinateSpace =
                  objectAt(obj.get("sharedCoordinateSpace"), "$path.sharedCoordinateSpace")?.let { space ->
                    DanmakuCoordinateSpace(
                        widthUnits = numberAt(space.get("widthUnits"), "$path.sharedCoordinateSpace.widthUnits")
                            ?.toFloat() ?: 1f,
                        heightUnits = numberAt(space.get("heightUnits"), "$path.sharedCoordinateSpace.heightUnits")
                            ?.toFloat() ?: 1f,
                    )
                  } ?: DanmakuCoordinateSpace(),
              laneSets =
                  arrayAt(obj.get("laneSets"), "$path.laneSets")?.values?.mapIndexedNotNull { laneIndex, laneSet ->
                    val lanePath = "$path.laneSets[$laneIndex]"
                    val laneObj = objectAt(laneSet, lanePath) ?: return@mapIndexedNotNull null
                    val laneId = stringAt(laneObj.get("id"), "$lanePath.id") ?: return@mapIndexedNotNull null
                    val family =
                        enumOrNull<DanmakuLaneFamily>(stringAt(laneObj.get("laneFamily"), "$lanePath.laneFamily"), "$lanePath.laneFamily")
                            ?: return@mapIndexedNotNull null
                    DanmakuLaneSetSpec(
                        id = laneId,
                        laneFamily = family,
                        emissionDirection =
                            enumOrNull<DanmakuEmissionDirection>(
                                stringAt(laneObj.get("emissionDirection"), "$lanePath.emissionDirection"),
                                "$lanePath.emissionDirection",
                            ),
                        laneCount = intAt(laneObj.get("laneCount"), "$lanePath.laneCount") ?: 0,
                    )
                  } ?: emptyList(),
              layers =
                  arrayAt(obj.get("layers"), "$path.layers")?.values?.mapIndexedNotNull { layerIndex, layer ->
                    val layerPath = "$path.layers[$layerIndex]"
                    val layerObj = objectAt(layer, layerPath) ?: return@mapIndexedNotNull null
                    val layerId = stringAt(layerObj.get("id"), "$layerPath.id") ?: return@mapIndexedNotNull null
                    DanmakuDepthLayer(
                        id = layerId,
                        surfaceIds = stringSet(layerObj.get("surfaceIds"), "$layerPath.surfaceIds"),
                        depthOffsetMeters =
                            numberAt(layerObj.get("depthOffsetMeters"), "$layerPath.depthOffsetMeters")?.toFloat() ?: 0f,
                        maxActiveItems = intAt(layerObj.get("maxActiveItems"), "$layerPath.maxActiveItems") ?: 0,
                        basicStyle = overlayStyle(layerObj.get("basicStyle"), "$layerPath.basicStyle"),
                        duplicateTo = stringSet(layerObj.get("duplicateTo"), "$layerPath.duplicateTo"),
                        surfaceQuotas =
                            objectAt(layerObj.get("surfaceQuotas"), "$layerPath.surfaceQuotas")?.values?.mapNotNull {
                                (surface, quota) ->
                              val value = intAt(quota, "$layerPath.surfaceQuotas.$surface") ?: return@mapNotNull null
                              surface to value
                            }?.toMap() ?: emptyMap(),
                    )
                  } ?: emptyList(),
              occlusionPolicy =
                  enumOrNull<DanmakuOcclusionPolicy>(
                      stringAt(obj.get("occlusionPolicy"), "$path.occlusionPolicy"),
                      "$path.occlusionPolicy",
                  ) ?: DanmakuOcclusionPolicy.AVOID_PROJECTED_OVERLAP,
              laneStateSharing =
                  enumOrNull<LaneStateSharing>(
                      stringAt(obj.get("laneStateSharing"), "$path.laneStateSharing"),
                      "$path.laneStateSharing",
                  ) ?: LaneStateSharing.SHARED,
              maxActiveItems = intAt(obj.get("maxActiveItems"), "$path.maxActiveItems"),
              occlusionDomainId = stringAt(obj.get("occlusionDomainId"), "$path.occlusionDomainId"),
          )
        } ?: emptyList()

    private fun overlayStyle(node: ThemeJsonNode?, path: String): OverlayStyleOverride {
      val obj = objectAt(node, path) ?: return OverlayStyleOverride()
      return OverlayStyleOverride(
          fontScale = numberAt(obj.get("fontScale"), "$path.fontScale")?.toFloat(),
          textColorArgb = numberAt(obj.get("textColorArgb"), "$path.textColorArgb")?.toLong(),
          opacity = numberAt(obj.get("opacity"), "$path.opacity")?.toFloat(),
          speedScale = numberAt(obj.get("speedScale"), "$path.speedScale")?.toFloat(),
          outlineWidthDp = numberAt(obj.get("outlineWidthDp"), "$path.outlineWidthDp")?.toFloat(),
          textLayout = objectAt(obj.get("textLayout"), "$path.textLayout")?.let { layout ->
            OverlayTextLayoutStyle(
                writingMode =
                    enumOrNull<TextWritingMode>(
                        stringAt(layout.get("writingMode"), "$path.textLayout.writingMode"),
                        "$path.textLayout.writingMode",
                    ) ?: TextWritingMode.HORIZONTAL_TB,
                direction =
                    enumOrNull<TextDirection>(
                        stringAt(layout.get("direction"), "$path.textLayout.direction"),
                        "$path.textLayout.direction",
                    ) ?: TextDirection.AUTO,
                maxLines = intAt(layout.get("maxLines"), "$path.textLayout.maxLines") ?: 1,
                lineSpacingRatio = numberAt(layout.get("lineSpacingRatio"), "$path.textLayout.lineSpacingRatio")?.toFloat() ?: 1f,
                overflow =
                    enumOrNull<TextOverflowPolicy>(
                        stringAt(layout.get("overflow"), "$path.textLayout.overflow"),
                        "$path.textLayout.overflow",
                    ) ?: TextOverflowPolicy.CLIP,
            )
          },
      )
    }

    private fun occlusionDomains(node: ThemeJsonNode?): List<DanmakuOcclusionDomain> =
        arrayAt(node, "danmakuOcclusionDomains")?.values?.mapIndexedNotNull { index, item ->
          val path = "danmakuOcclusionDomains[$index]"
          val obj = objectAt(item, path) ?: return@mapIndexedNotNull null
          val id = stringAt(obj.get("id"), "$path.id") ?: return@mapIndexedNotNull null
          DanmakuOcclusionDomain(
              id = id,
              groupIds = stringSet(obj.get("groupIds"), "$path.groupIds"),
              requireCrossGroupChecks = booleanAt(obj.get("requireCrossGroupChecks"), "$path.requireCrossGroupChecks") ?: true,
          )
        } ?: emptyList()

    private fun anchor(node: ThemeJsonNode?, path: String): SpatialAnchor? {
      if (node == null || node is ThemeJsonNode.Null) return null
      val obj = objectAt(node, path) ?: return null
      stringAt(obj.get("sceneNode"), "$path.sceneNode")?.let { return SpatialAnchor.SceneAnchor(it) }
      stringAt(obj.get("slot"), "$path.slot")?.let { name ->
        val slot = enumOrNull<PanelSlot>(name, "$path.slot")
        if (slot != null) return SpatialAnchor.SlotAnchor(slot)
      }
      issues += ThemeValidationIssue.MalformedField(path, "expected-anchor")
      return null
    }

    private fun meters(node: ThemeJsonNode?, path: String): Meters3 {
      val obj = objectAt(node, path) ?: return Meters3()
      return Meters3(
          x = numberAt(obj.get("x"), "$path.x")?.toFloat() ?: 0f,
          y = numberAt(obj.get("y"), "$path.y")?.toFloat() ?: 0f,
          z = numberAt(obj.get("z"), "$path.z")?.toFloat() ?: 0f,
      )
    }

    private fun euler(node: ThemeJsonNode?, path: String): EulerDegrees {
      val obj = objectAt(node, path) ?: return EulerDegrees()
      return EulerDegrees(
          pitch = numberAt(obj.get("pitch"), "$path.pitch")?.toFloat() ?: 0f,
          yaw = numberAt(obj.get("yaw"), "$path.yaw")?.toFloat() ?: 0f,
          roll = numberAt(obj.get("roll"), "$path.roll")?.toFloat() ?: 0f,
      )
    }

    private fun size(node: ThemeJsonNode?, path: String): PanelSizeMeters {
      if (node == null || node is ThemeJsonNode.Null) {
        issues += ThemeValidationIssue.MalformedField(path, "missing")
        return PanelSizeMeters(1f, 1f)
      }
      val obj = objectAt(node, path) ?: return PanelSizeMeters(1f, 1f)
      val width = numberAt(obj.get("width"), "$path.width")
      val height = numberAt(obj.get("height"), "$path.height")
      if (width == null) issues += ThemeValidationIssue.MalformedField("$path.width", "missing")
      if (height == null) issues += ThemeValidationIssue.MalformedField("$path.height", "missing")
      return PanelSizeMeters(width?.toFloat() ?: 1f, height?.toFloat() ?: 1f)
    }

    private fun shape(node: ThemeJsonNode?, path: String): PanelShape {
      val obj = objectAt(node, path) ?: return PanelShape.Quad
      return when (val kind = stringAt(obj.get("kind"), "$path.kind")) {
        null, "quad" -> PanelShape.Quad
        "cylinder" ->
            PanelShape.Cylinder(
                radiusMeters = numberAt(obj.get("radiusMeters"), "$path.radiusMeters")?.toFloat() ?: 0f,
                arcDegrees = numberAt(obj.get("arcDegrees"), "$path.arcDegrees")?.toFloat() ?: 0f,
            )
        else -> {
          issues += ThemeValidationIssue.MalformedField("$path.kind", "unknown-shape:$kind")
          PanelShape.Quad
        }
      }
    }

    private fun geometrySet(node: ThemeJsonNode?, path: String): Set<ThemeableGeometry> =
        stringSet(node, path).mapNotNullTo(linkedSetOf()) { name ->
          ThemeableGeometry.fromWireName(name)
              ?: run {
                issues += ThemeValidationIssue.MalformedField(path, "unknown-geometry:$name")
                null
              }
        }

    private fun userAdjustableSet(node: ThemeJsonNode?, path: String): Set<UserAdjustableProperty> =
        stringSet(node, path).mapNotNullTo(linkedSetOf()) { name ->
          UserAdjustableProperty.fromWireName(name)
              ?: run {
                issues += ThemeValidationIssue.MalformedField(path, "unknown-user-adjustable:$name")
                null
              }
        }

    private fun stringMap(node: ThemeJsonNode?, path: String): Map<String, String> {
      val obj = objectAt(node, path) ?: return emptyMap()
      return obj.values.mapNotNull { (key, value) ->
        val text = stringAt(value, "$path.$key")
        if (text == null) {
          issues += ThemeValidationIssue.MalformedField("$path.$key", "expected-string")
        }
        text?.let { key to it }
      }.toMap()
    }

    private fun stringSet(node: ThemeJsonNode?, path: String): Set<String> {
      if (node == null || node is ThemeJsonNode.Null) return emptySet()
      val array = arrayAt(node, path) ?: return emptySet()
      return array.values.mapIndexedNotNull { index, item ->
        val text = stringAt(item, "$path[$index]")
        if (text == null) issues += ThemeValidationIssue.MalformedField("$path[$index]", "expected-string")
        text
      }.toSet()
    }

    // An explicit JSON `null` means "absent" for every optional field.
    private fun objectAt(node: ThemeJsonNode?, path: String): ThemeJsonNode.Obj? {
      if (node == null || node is ThemeJsonNode.Null) return null
      if (node is ThemeJsonNode.Obj) return node
      issues += ThemeValidationIssue.MalformedField(path, "expected-object")
      return null
    }

    private fun arrayAt(node: ThemeJsonNode?, path: String): ThemeJsonNode.Arr? {
      if (node == null || node is ThemeJsonNode.Null) return null
      if (node is ThemeJsonNode.Arr) return node
      issues += ThemeValidationIssue.MalformedField(path, "expected-array")
      return null
    }

    private fun stringAt(node: ThemeJsonNode?, path: String): String? {
      if (node == null || node is ThemeJsonNode.Null) return null
      if (node is ThemeJsonNode.Str) return node.value
      issues += ThemeValidationIssue.MalformedField(path, "expected-string")
      return null
    }

    private fun numberAt(node: ThemeJsonNode?, path: String): Double? {
      if (node == null || node is ThemeJsonNode.Null) return null
      if (node is ThemeJsonNode.Num) return node.value
      issues += ThemeValidationIssue.MalformedField(path, "expected-number")
      return null
    }

    private fun intAt(node: ThemeJsonNode?, path: String): Int? {
      val value = numberAt(node, path) ?: return null
      if (value % 1.0 != 0.0) {
        issues += ThemeValidationIssue.MalformedField(path, "expected-integer")
        return null
      }
      return value.toInt()
    }

    private fun booleanAt(node: ThemeJsonNode?, path: String): Boolean? {
      if (node == null || node is ThemeJsonNode.Null) return null
      if (node is ThemeJsonNode.Bool) return node.value
      issues += ThemeValidationIssue.MalformedField(path, "expected-boolean")
      return null
    }

    private inline fun <reified T : Enum<T>> enumOrNull(name: String?, path: String): T? {
      if (name == null) return null
      val match = enumValues<T>().firstOrNull { it.name.equals(name, ignoreCase = true) }
      if (match == null) issues += ThemeValidationIssue.MalformedField(path, "unknown-enum-value:$name")
      return match
    }
  }
}
