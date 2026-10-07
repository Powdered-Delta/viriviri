package com.m0e_n00b.spatialworkbench.core

sealed interface MediaOverlayValidationIssue {
  data class DuplicateSurfaceId(val id: String) : MediaOverlayValidationIssue

  data class InvalidSurface(val id: String, val reason: String) : MediaOverlayValidationIssue

  data class DuplicateGroupId(val id: String) : MediaOverlayValidationIssue

  data class InvalidGroup(val id: String, val reason: String) : MediaOverlayValidationIssue

  data class InvalidLaneSet(val groupId: String, val laneSetId: String, val reason: String) : MediaOverlayValidationIssue

  data class InvalidLayer(val groupId: String, val layerId: String, val reason: String) : MediaOverlayValidationIssue

  data class InvalidCaptionCue(val id: String, val reason: String) : MediaOverlayValidationIssue

  data class InvalidTranslationSettings(val reason: String) : MediaOverlayValidationIssue

  /** `layers[].surfaceIds` names a surface that is not registered at all (R5). */
  data class UnknownOverlaySurface(
      val groupId: String,
      val layerId: String,
      val surfaceId: String,
  ) : MediaOverlayValidationIssue

  /** `laneSets[].laneCount` outside `1..64` (R5). */
  data class InvalidLaneCount(
      val groupId: String,
      val laneSetId: String,
      val laneCount: Int,
  ) : MediaOverlayValidationIssue

  /**
   * `ISOLATED` lane state combined with multiple surfaces makes capacity ambiguous unless a
   * per-surface quota is declared (R5/R7.5, design §4.4).
   */
  data class AmbiguousLaneSharing(
      val groupId: String,
      val layerId: String,
      val reason: String,
  ) : MediaOverlayValidationIssue
}

object MediaOverlayValidator {
  /** Inclusive lane-count range per R5. */
  const val MIN_LANE_COUNT: Int = 1
  const val MAX_LANE_COUNT: Int = 64

  fun validateTopology(
      surfaces: Collection<OverlaySurfaceSpec>,
      groups: Collection<DanmakuSurfaceGroup>,
      domains: Collection<DanmakuOcclusionDomain> = emptyList(),
  ): List<MediaOverlayValidationIssue> = buildList {
    surfaces.groupBy(OverlaySurfaceSpec::id).forEach { (id, duplicates) ->
      if (id.isBlank() || duplicates.size > 1) add(MediaOverlayValidationIssue.DuplicateSurfaceId(id))
    }
    surfaces.forEach { surface -> validateSurface(surface, this) }
    val surfacesById = surfaces.associateBy(OverlaySurfaceSpec::id)

    groups.groupBy(DanmakuSurfaceGroup::id).forEach { (id, duplicates) ->
      if (id.isBlank() || duplicates.size > 1) add(MediaOverlayValidationIssue.DuplicateGroupId(id))
    }
    val domainIds = domains.mapTo(linkedSetOf(), DanmakuOcclusionDomain::id)
    groups.forEach { group -> validateGroup(group, surfacesById, domainIds, this) }
    val groupIds = groups.map(DanmakuSurfaceGroup::id).toSet()

    domains.forEach { domain ->
      if (domain.id.isBlank() || domain.groupIds.isEmpty() || !groupIds.containsAll(domain.groupIds)) {
        add(MediaOverlayValidationIssue.InvalidGroup(domain.id, "occlusion-domain"))
      }
    }
  }

  fun validateCaption(cue: CaptionCue): List<MediaOverlayValidationIssue> = buildList {
    if (cue.id.isBlank()) add(MediaOverlayValidationIssue.InvalidCaptionCue(cue.id, "id"))
    if (cue.startMs < 0L || cue.endMs <= cue.startMs) {
      add(MediaOverlayValidationIssue.InvalidCaptionCue(cue.id, "time-range"))
    }
    if (cue.originalText.isBlank()) add(MediaOverlayValidationIssue.InvalidCaptionCue(cue.id, "original-text"))
  }

  fun validateTranslation(settings: TranslationSettings): List<MediaOverlayValidationIssue> = buildList {
    if (settings.maxConcurrentRequests !in 1..8) {
      add(MediaOverlayValidationIssue.InvalidTranslationSettings("max-concurrency"))
    }
    if (settings.timeoutMs !in 100L..60_000L) {
      add(MediaOverlayValidationIssue.InvalidTranslationSettings("timeout"))
    }
    if (settings.prefetchWindowMs !in 0L..120_000L) {
      add(MediaOverlayValidationIssue.InvalidTranslationSettings("prefetch-window"))
    }
    if (settings.enabled &&
        (settings.providerId.isNullOrBlank() || settings.modelId.isNullOrBlank() || settings.targetLanguage.isNullOrBlank())) {
      add(MediaOverlayValidationIssue.InvalidTranslationSettings("enabled-provider-model-language"))
    }
  }

  private fun validateSurface(
      surface: OverlaySurfaceSpec,
      issues: MutableList<MediaOverlayValidationIssue>,
  ) {
    if (surface.supportedKinds.isEmpty()) issues += MediaOverlayValidationIssue.InvalidSurface(surface.id, "supported-kinds")
    if (surface.capacity <= 0) issues += MediaOverlayValidationIssue.InvalidSurface(surface.id, "capacity")
    validateStyle(surface.id, surface.basicStyle, issues)
  }

  private fun validateGroup(
      group: DanmakuSurfaceGroup,
      surfacesById: Map<String, OverlaySurfaceSpec>,
      domainIds: Set<String>,
      issues: MutableList<MediaOverlayValidationIssue>,
  ) {
    if (!group.allocationWeight.isFinite() || group.allocationWeight <= 0f) {
      issues += MediaOverlayValidationIssue.InvalidGroup(group.id, "allocation-weight")
    }
    if (!group.sharedCoordinateSpace.widthUnits.isFinite() ||
        !group.sharedCoordinateSpace.heightUnits.isFinite() ||
        group.sharedCoordinateSpace.widthUnits <= 0f ||
        group.sharedCoordinateSpace.heightUnits <= 0f) {
      issues += MediaOverlayValidationIssue.InvalidGroup(group.id, "coordinate-space")
    }
    if (group.laneSets.isEmpty()) issues += MediaOverlayValidationIssue.InvalidGroup(group.id, "lane-sets")
    if (group.layers.isEmpty()) issues += MediaOverlayValidationIssue.InvalidGroup(group.id, "layers")
    if (group.maxActiveItems != null && group.maxActiveItems <= 0) {
      issues += MediaOverlayValidationIssue.InvalidGroup(group.id, "max-active-items")
    }
    if (group.occlusionDomainId != null && group.occlusionDomainId !in domainIds) {
      issues += MediaOverlayValidationIssue.InvalidGroup(group.id, "unknown-occlusion-domain")
    }

    group.laneSets.groupBy(DanmakuLaneSetSpec::id).forEach { (id, duplicates) ->
      if (id.isBlank() || duplicates.size > 1) {
        issues += MediaOverlayValidationIssue.InvalidLaneSet(group.id, id, "duplicate-id")
      }
    }
    group.laneSets.forEach { laneSet ->
      if (laneSet.laneCount !in MIN_LANE_COUNT..MAX_LANE_COUNT) {
        issues += MediaOverlayValidationIssue.InvalidLaneCount(group.id, laneSet.id, laneSet.laneCount)
      }
      if (laneSet.laneFamily == DanmakuLaneFamily.SCROLLING && laneSet.emissionDirection == null) {
        issues += MediaOverlayValidationIssue.InvalidLaneSet(group.id, laneSet.id, "scrolling-direction")
      }
      if (laneSet.laneFamily != DanmakuLaneFamily.SCROLLING && laneSet.emissionDirection != null) {
        issues += MediaOverlayValidationIssue.InvalidLaneSet(group.id, laneSet.id, "fixed-direction")
      }
    }

    group.layers.groupBy(DanmakuDepthLayer::id).forEach { (id, duplicates) ->
      if (id.isBlank() || duplicates.size > 1) {
        issues += MediaOverlayValidationIssue.InvalidLayer(group.id, id, "duplicate-id")
      }
    }
    group.layers.forEach { layer -> validateLayer(group, layer, surfacesById, issues) }
  }

  private fun validateLayer(
      group: DanmakuSurfaceGroup,
      layer: DanmakuDepthLayer,
      surfacesById: Map<String, OverlaySurfaceSpec>,
      issues: MutableList<MediaOverlayValidationIssue>,
  ) {
    if (layer.surfaceIds.isEmpty()) {
      // `surfaceIds` is the allocation domain and must name at least one target (design §4.4).
      issues += MediaOverlayValidationIssue.InvalidLayer(group.id, layer.id, "surface-ids-empty")
    }
    if (layer.surfaceIds.any { it in layer.duplicateTo }) {
      // Declaring the same surface as both allocation domain and duplication target is ambiguous.
      issues += MediaOverlayValidationIssue.InvalidLayer(group.id, layer.id, "duplicate-allocation-domain")
    }
    layer.allSurfaceIds().forEach { surfaceId ->
      val surface = surfacesById[surfaceId]
      when {
        surface == null ->
            issues += MediaOverlayValidationIssue.UnknownOverlaySurface(group.id, layer.id, surfaceId)
        OverlayKind.DANMAKU !in surface.supportedKinds ->
            issues += MediaOverlayValidationIssue.InvalidLayer(group.id, layer.id, "surface-does-not-support-danmaku")
      }
    }
    if (!layer.depthOffsetMeters.isFinite()) issues += MediaOverlayValidationIssue.InvalidLayer(group.id, layer.id, "depth")
    if (layer.maxActiveItems <= 0) issues += MediaOverlayValidationIssue.InvalidLayer(group.id, layer.id, "capacity")
    if (layer.surfaceQuotas.values.any { it <= 0 }) {
      issues += MediaOverlayValidationIssue.InvalidLayer(group.id, layer.id, "surface-quota")
    }
    if (group.laneStateSharing == LaneStateSharing.ISOLATED && layer.surfaceIds.size > 1) {
      val declared = layer.surfaceQuotas.keys
      if (!declared.containsAll(layer.surfaceIds)) {
        issues +=
            MediaOverlayValidationIssue.AmbiguousLaneSharing(
                group.id,
                layer.id,
                "isolated-lane-state-requires-per-surface-quota",
            )
      }
    }
    validateStyleOverride("${group.id}.${layer.id}", layer.basicStyle, issues)
  }

  private fun validateStyle(
      id: String,
      style: OverlayBasicStyle,
      issues: MutableList<MediaOverlayValidationIssue>,
  ) =
      validateStyleValues(
          id = id,
          fontScale = style.fontScale,
          opacity = style.opacity,
          speedScale = style.speedScale,
          outlineWidthDp = style.outlineWidthDp,
          textLayout = style.textLayout,
          issues = issues,
      )

  private fun validateStyleOverride(
      id: String,
      style: OverlayStyleOverride,
      issues: MutableList<MediaOverlayValidationIssue>,
  ) =
      validateStyleValues(
          id = id,
          fontScale = style.fontScale,
          opacity = style.opacity,
          speedScale = style.speedScale,
          outlineWidthDp = style.outlineWidthDp,
          textLayout = style.textLayout,
          issues = issues,
      )

  private fun validateStyleValues(
      id: String,
      fontScale: Float?,
      opacity: Float?,
      speedScale: Float?,
      outlineWidthDp: Float?,
      textLayout: OverlayTextLayoutStyle?,
      issues: MutableList<MediaOverlayValidationIssue>,
  ) {
    if (fontScale != null && (!fontScale.isFinite() || fontScale !in 0.25f..4f)) {
      issues += MediaOverlayValidationIssue.InvalidSurface(id, "font-scale")
    }
    if (opacity != null && (!opacity.isFinite() || opacity !in 0f..1f)) {
      issues += MediaOverlayValidationIssue.InvalidSurface(id, "opacity")
    }
    if (speedScale != null && (!speedScale.isFinite() || speedScale !in 0.25f..4f)) {
      issues += MediaOverlayValidationIssue.InvalidSurface(id, "speed-scale")
    }
    if (outlineWidthDp != null && (!outlineWidthDp.isFinite() || outlineWidthDp !in 0f..16f)) {
      issues += MediaOverlayValidationIssue.InvalidSurface(id, "outline-width")
    }
    if (textLayout != null &&
        (textLayout.maxLines !in 1..4 ||
            !textLayout.lineSpacingRatio.isFinite() ||
            textLayout.lineSpacingRatio !in 0.5f..3f)) {
      issues += MediaOverlayValidationIssue.InvalidSurface(id, "text-layout")
    }
  }
}

object DanmakuAllocator {
  fun allocate(request: DanmakuAllocationRequest): DanmakuAllocationResult {
    val topologyIssues = MediaOverlayValidator.validateTopology(request.surfaces.values, request.groups)
    if (topologyIssues.isNotEmpty()) return DanmakuAllocationResult.Dropped(DanmakuDropReason.INVALID_TOPOLOGY)

    val candidates =
        request.groups.mapNotNull { group ->
          val layerCandidates = eligibleLayerSurfaces(group, request.surfaces, request.activeItemsBySurface)
          val laneSet = selectLaneSet(group, request.event) ?: return@mapNotNull null
          val lane = selectLane(laneSet, request.event.id, request.unavailableLaneIds) ?: return@mapNotNull null
          if (layerCandidates.isEmpty()) return@mapNotNull null
          GroupCandidate(group, laneSet, lane, layerCandidates)
        }
    if (candidates.isEmpty()) {
      return DanmakuAllocationResult.Dropped(
          if (request.groups.any { selectLaneSet(it, request.event) != null }) {
            DanmakuDropReason.NO_ELIGIBLE_SURFACE
          } else {
            DanmakuDropReason.NO_ELIGIBLE_LANE
          }
      )
    }

    val groupCandidate = selectGroup(candidates, request)
    // Exactly one surface is selected per event: `surfaceIds` is an allocation domain, not a
    // duplication domain (design §4.4).
    val selected = selectLayerSurface(groupCandidate.candidates, request)
    val surface = selected.surface
    val layer = selected.layer
    val style = resolveStyle(surface.basicStyle, layer.basicStyle, request.event.styleOverride)

    return DanmakuAllocationResult.Assigned(
        ScheduledDanmakuAssignment(
            eventId = request.event.id,
            groupId = groupCandidate.group.id,
            laneSetId = groupCandidate.laneSet.id,
            laneIndex = groupCandidate.lane,
            layerId = layer.id,
            surfaceId = surface.id,
            emissionDirection = groupCandidate.laneSet.emissionDirection,
            styleSnapshot = style,
        )
    )
  }

  private data class LayerSurfaceCandidate(
      val layer: DanmakuDepthLayer,
      val surface: OverlaySurfaceSpec,
  )

  private data class GroupCandidate(
      val group: DanmakuSurfaceGroup,
      val laneSet: DanmakuLaneSetSpec,
      val lane: Int,
      val candidates: List<LayerSurfaceCandidate>,
  )

  private fun eligibleLayerSurfaces(
      group: DanmakuSurfaceGroup,
      surfaces: Map<String, OverlaySurfaceSpec>,
      activeItemsBySurface: Map<String, Int>,
  ): List<LayerSurfaceCandidate> =
      group.layers.flatMap { layer ->
        layer.surfaceIds.mapNotNull { surfaceId ->
          val surface = surfaces[surfaceId]
          if (surface == null || !surface.enabled || OverlayKind.DANMAKU !in surface.supportedKinds) {
            return@mapNotNull null
          }
          val capacity = layer.effectiveCapacity(surface.id, surface.capacity)
          if (activeItemsBySurface.getOrDefault(surface.id, 0) < capacity) {
            LayerSurfaceCandidate(layer, surface)
          } else {
            null
          }
        }
      }

  private fun selectLaneSet(
      group: DanmakuSurfaceGroup,
      event: DanmakuEvent,
  ): DanmakuLaneSetSpec? =
      group.laneSets.firstOrNull {
        it.laneFamily == event.laneFamily &&
            (event.laneFamily != DanmakuLaneFamily.SCROLLING || it.emissionDirection == event.emissionDirection)
      }

  private fun selectLane(
      laneSet: DanmakuLaneSetSpec,
      eventId: String,
      unavailableLaneIds: Set<String>,
  ): Int? {
    val start = stableIndex(eventId, laneSet.laneCount)
    return (0 until laneSet.laneCount)
        .map { (start + it) % laneSet.laneCount }
        .firstOrNull { "${laneSet.id}:$it" !in unavailableLaneIds }
  }

  private fun selectGroup(
      candidates: List<GroupCandidate>,
      request: DanmakuAllocationRequest,
  ): GroupCandidate =
      candidates.minWith(
          compareBy<GroupCandidate> {
                val active = request.activeItemsByGroup.getOrDefault(it.group.id, 0)
                val capacity = it.group.layers.sumOf(DanmakuDepthLayer::maxActiveItems).coerceAtLeast(1)
                active.toFloat() / capacity / it.group.allocationWeight
              }
              .thenBy { stableIndex("${request.event.id}:${it.group.id}", Int.MAX_VALUE) }
              .thenBy { it.group.id }
      )

  /**
   * The single, deterministic surface-selection step for a layer bound to several surfaces (design
   * §4.4). Selection uses current load, then a stable event/layer/surface hash so that the same
   * event always lands on the same surface for equal inputs.
   */
  private fun selectLayerSurface(
      candidates: List<LayerSurfaceCandidate>,
      request: DanmakuAllocationRequest,
  ): LayerSurfaceCandidate =
      candidates.minWith(
          compareBy<LayerSurfaceCandidate> {
                val capacity = it.layer.effectiveCapacity(it.surface.id, it.surface.capacity).coerceAtLeast(1)
                request.activeItemsBySurface.getOrDefault(it.surface.id, 0).toFloat() / capacity
              }
              .thenBy { stableIndex("${request.event.id}:${it.layer.id}:${it.surface.id}", Int.MAX_VALUE) }
              .thenBy { it.layer.id }
              .thenBy { it.surface.id }
      )

  private fun resolveStyle(
      surface: OverlayBasicStyle,
      layer: OverlayStyleOverride,
      event: OverlayStyleOverride?,
  ): ResolvedDanmakuStyle {
    val resolved =
        OverlayBasicStyle(
            fontScale = event?.fontScale ?: layer.fontScale ?: surface.fontScale,
            opacity = event?.opacity ?: layer.opacity ?: surface.opacity,
            speedScale = event?.speedScale ?: layer.speedScale ?: surface.speedScale,
            outlineWidthDp = event?.outlineWidthDp ?: layer.outlineWidthDp ?: surface.outlineWidthDp,
            textLayout = event?.textLayout ?: layer.textLayout ?: surface.textLayout,
        )
    return ResolvedDanmakuStyle(surface, layer, event, resolved)
  }

  private fun stableIndex(value: String, modulo: Int): Int =
      (value.fold(17L) { hash, char -> hash * 31L + char.code } and Long.MAX_VALUE).rem(modulo).toInt()
}
