package com.m0e_n00b.spatialworkbench.core

/**
 * Shared builders for the Phase 1 theme-contract tests. They keep every test focused on one rule by
 * reusing the real default fixture ([CinemaTheme]) instead of inventing a parallel theme.
 */
internal object ThemeTestKit {

  fun cinema(): SpatialTheme = CinemaTheme.create()

  fun panel(id: String): PanelSpec = cinema().panels.getValue(PanelId(id))

  fun panelId(id: String): PanelId = PanelId(id)

  fun placement(
      anchor: SpatialAnchor?,
      width: Float = 1f,
      height: Float = 0.6f,
      offset: Meters3 = Meters3(),
      rotation: EulerDegrees = EulerDegrees(),
      parentSlot: PanelSlot? = null,
      depthRelation: SpatialDepthRelation = SpatialDepthRelation.ROOT,
      shape: PanelShape = PanelShape.Quad,
      visibility: PanelVisibility = PanelVisibility.VISIBLE,
  ): SlotPlacement =
      SlotPlacement(
          visibility = visibility,
          anchor = anchor,
          offsetMeters = offset,
          rotationDegrees = rotation,
          sizeMeters = PanelSizeMeters(width, height),
          shape = shape,
          parentSlot = parentSlot,
          depthRelation = depthRelation,
      )

  fun sceneAnchor(name: String): SpatialAnchor = SpatialAnchor.SceneAnchor(name)

  fun danmakuSurface(id: String, kind: OverlayKind = OverlayKind.DANMAKU): OverlaySurfaceSpec =
      OverlaySurfaceSpec(
          id = id,
          enabled = true,
          supportedKinds = setOf(kind),
          anchorMode = OverlayAnchorMode.STAGE_LOCKED,
          capacity = 64,
      )

  fun danmakuLayer(
      id: String = "layer",
      surfaceIds: Set<String> = setOf(CinemaTheme.DEFAULT_DANMAKU_SURFACE_ID),
      maxActiveItems: Int = 32,
      duplicateTo: Set<String> = emptySet(),
      surfaceQuotas: Map<String, Int> = emptyMap(),
  ): DanmakuDepthLayer =
      DanmakuDepthLayer(
          id = id,
          surfaceIds = surfaceIds,
          depthOffsetMeters = -0.01f,
          maxActiveItems = maxActiveItems,
          duplicateTo = duplicateTo,
          surfaceQuotas = surfaceQuotas,
      )

  fun danmakuGroup(
      id: String = "group",
      layers: List<DanmakuDepthLayer> = listOf(danmakuLayer()),
      laneCount: Int = 12,
      laneStateSharing: LaneStateSharing = LaneStateSharing.SHARED,
      maxActiveItems: Int? = 240,
      occlusionDomainId: String? = null,
  ): DanmakuSurfaceGroup =
      DanmakuSurfaceGroup(
          id = id,
          laneSets =
              listOf(
                  DanmakuLaneSetSpec(
                      id = "scroll",
                      laneFamily = DanmakuLaneFamily.SCROLLING,
                      emissionDirection = DanmakuEmissionDirection.RIGHT_TO_LEFT,
                      laneCount = laneCount,
                  )
              ),
          layers = layers,
          laneStateSharing = laneStateSharing,
          maxActiveItems = maxActiveItems,
          occlusionDomainId = occlusionDomainId,
      )

  /** Every anchor `CinemaTheme` declares: the catalog a Phase 2.1 scene export must eventually provide. */
  fun declaredAnchorCatalog(): SceneAnchorCatalog = SceneAnchorCatalog.of(cinema().requiredAnchors)

  fun emptyAnchorCatalog(): SceneAnchorCatalog = SceneAnchorCatalog.of(emptySet())

  /**
   * The controlled component directory the current app could satisfy: every panel component plus
   * every UI composition member of the default cinema group.
   */
  fun cinemaComponentRegistry(): ThemeComponentRegistry {
    val panels =
        listOf(
            Triple("mediaStage", ComponentKind.PANEL, PanelClass.MEDIA_SURFACE),
            Triple("transport", ComponentKind.PANEL, PanelClass.UI_PANEL),
            Triple("centerContent", ComponentKind.PANEL, PanelClass.SCENE_PANEL),
            Triple("detailRail", ComponentKind.PANEL, PanelClass.UI_PANEL),
            Triple("contextRail", ComponentKind.PANEL, PanelClass.UI_PANEL),
            Triple("systemToolbar", ComponentKind.PANEL, PanelClass.UI_PANEL),
            Triple("inputConsole", ComponentKind.PANEL, PanelClass.UI_PANEL),
            Triple("focusPanel", ComponentKind.PANEL, PanelClass.UI_PANEL),
            Triple("shortsDetailsRail", ComponentKind.PANEL, PanelClass.UI_PANEL),
            Triple("shortsCommentsRail", ComponentKind.PANEL, PanelClass.UI_PANEL),
        ).map { (id, kind, panelClass) ->
          ThemeComponentDescriptor(
              id = ComponentId(id),
              kind = kind,
              compatiblePanelClasses = setOf(panelClass),
              implementationRef = "app://panel/$id",
          )
        }
    val members =
        DefaultCinemaPlaybackCanvasGroup.create().members.map { node ->
          ThemeComponentDescriptor(
              id = ComponentId(node.id),
              kind = node.kind,
              implementationRef = "app://module/${node.id}",
          )
        }
    return ThemeComponentRegistry.of(panels + members)
  }

  fun cinemaEntityCatalog(): EntityCatalog =
      EntityCatalog.ofNames(listOf("workbenchRoot", "grabBar", "mediaStage"))

  fun cinemaOverlayCatalog(): OverlaySurfaceCatalog =
      OverlaySurfaceCatalog.of(listOf(danmakuSurface(CinemaTheme.DEFAULT_DANMAKU_SURFACE_ID)))

  /**
   * The headset verification record Phase 3.0 must obtain on device before `NATIVE_PARENT` is treated
   * as a verified mechanism (design §4.3). It does not exist today, which is exactly why the default
   * catalog below reports no record at all.
   */
  fun headsetVerifiedGrabMechanism(): GrabMechanismVerificationCatalog =
      GrabMechanismVerificationCatalog.ofVerified(
          listOf(DefaultWorkbenchGrabGroup.ID to GrabMechanism.NATIVE_PARENT)
      )

  /**
   * Catalogs that can express the current app: scene anchors, entities, components, overlay surfaces.
   * No headset verification record exists, so `NATIVE_PARENT` is reported as unverified by default.
   */
  fun fullCatalogs(
      grabVerification: GrabMechanismVerificationCatalog = GrabMechanismVerificationCatalog.NONE,
  ): ThemeCatalogs =
      ThemeCatalogs(
          sceneAnchors = declaredAnchorCatalog(),
          entities = cinemaEntityCatalog(),
          components = cinemaComponentRegistry(),
          overlaySurfaces = cinemaOverlayCatalog(),
          grabVerification = grabVerification,
      )
}

internal fun SpatialTheme.withPanel(spec: PanelSpec): SpatialTheme = copy(panels = panels + (spec.id to spec))

internal fun SpatialTheme.withoutPanel(id: String): SpatialTheme = copy(panels = panels - PanelId(id))

internal fun SpatialTheme.withBindings(bindings: List<SlotBinding>): SpatialTheme = copy(slotBindings = bindings)

internal fun SpatialTheme.withGrabGroups(groups: List<GrabGroupSpec>): SpatialTheme = copy(grabGroups = groups)

internal fun SpatialTheme.withGrabGroup(group: GrabGroupSpec): SpatialTheme =
    copy(grabGroups = grabGroups.filterNot { it.id == group.id } + group)

internal fun SpatialTheme.withDanmakuGroups(
    groups: List<DanmakuSurfaceGroup>,
    domains: List<DanmakuOcclusionDomain> = danmakuOcclusionDomains,
): SpatialTheme = copy(danmakuGroups = groups, danmakuOcclusionDomains = domains)

internal fun SpatialTheme.withBindingsFor(slot: SlotId, vararg bindings: SlotBinding): SpatialTheme =
    copy(slotBindings = slotBindings.filterNot { it.slot == slot } + bindings)
