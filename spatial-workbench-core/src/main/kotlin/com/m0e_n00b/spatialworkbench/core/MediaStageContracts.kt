package com.m0e_n00b.spatialworkbench.core

enum class MediaStagePresentation {
  WATCH,
  SHORTS,
  FOCUS_PIP,
}

sealed interface StageGeometry {
  data class Flat(
      val widthMeters: Float,
      val heightMeters: Float,
  ) : StageGeometry

  data class Cylinder(
      val radiusMeters: Float,
      val arcDegrees: Float,
      val heightMeters: Float,
  ) : StageGeometry
}

enum class MediaStageTargetKind {
  VIDEO_OUTPUT,
  FLAT_OVERLAY,
  SPATIAL_OVERLAY,
}

/** A platform-neutral renderer target. Platform handles remain in the host adapter. */
data class MediaStageTargetSpec(
    val id: String,
    val kind: MediaStageTargetKind,
    val enabled: Boolean = true,
    val overlaySurfaceId: String? = null,
    val overlayAnchorMode: OverlayAnchorMode? = null,
) {
  val isOverlay: Boolean
    get() = kind != MediaStageTargetKind.VIDEO_OUTPUT
}

sealed interface MediaStageRegistryIssue {
  data class DuplicateTargetId(val id: String) : MediaStageRegistryIssue

  data class InvalidTarget(val id: String, val reason: String) : MediaStageRegistryIssue
}

data class MediaStageRegistryBuild(
    val registry: MediaStageTargetRegistry,
    val issues: List<MediaStageRegistryIssue>,
)

/**
 * Immutable target registry. A video target represents a host-owned video output handle;
 * overlay targets only reference semantic OverlaySurfaceSpec IDs and are never video surfaces.
 */
class MediaStageTargetRegistry private constructor(
    val targets: Map<String, MediaStageTargetSpec>,
) {
  operator fun get(id: String): MediaStageTargetSpec? = targets[id]

  companion object {
    fun create(targets: List<MediaStageTargetSpec>): MediaStageRegistryBuild {
      val duplicateIds = targets.groupingBy(MediaStageTargetSpec::id).eachCount().filterValues { it > 1 }.keys
      val issues = mutableListOf<MediaStageRegistryIssue>()
      duplicateIds.sorted().forEach { issues += MediaStageRegistryIssue.DuplicateTargetId(it) }

      val validTargets = linkedMapOf<String, MediaStageTargetSpec>()
      targets.forEach { target ->
        val issue = validate(target)
        if (issue != null) {
          issues += issue
        } else if (target.id !in duplicateIds) {
          validTargets[target.id] = target
        }
      }
      return MediaStageRegistryBuild(MediaStageTargetRegistry(validTargets), issues)
    }

    fun empty(): MediaStageTargetRegistry = MediaStageTargetRegistry(emptyMap())

    private fun validate(target: MediaStageTargetSpec): MediaStageRegistryIssue.InvalidTarget? =
        when {
          target.id.isBlank() -> MediaStageRegistryIssue.InvalidTarget(target.id, "blank-id")
          target.kind == MediaStageTargetKind.VIDEO_OUTPUT && target.overlaySurfaceId != null ->
              MediaStageRegistryIssue.InvalidTarget(target.id, "video-output-has-overlay-surface")
          target.kind == MediaStageTargetKind.VIDEO_OUTPUT && target.overlayAnchorMode != null ->
              MediaStageRegistryIssue.InvalidTarget(target.id, "video-output-has-overlay-anchor")
          target.isOverlay && target.overlaySurfaceId.isNullOrBlank() ->
              MediaStageRegistryIssue.InvalidTarget(target.id, "overlay-missing-surface-id")
          target.isOverlay && target.overlayAnchorMode == null ->
              MediaStageRegistryIssue.InvalidTarget(target.id, "overlay-missing-anchor-mode")
          else -> null
        }
  }
}

data class MediaClockSnapshot(
    val positionMs: Long = 0L,
    val durationMs: Long? = null,
    val isPlaying: Boolean = false,
)

data class MediaStageState(
    val registry: MediaStageTargetRegistry = MediaStageTargetRegistry.empty(),
    val presentation: MediaStagePresentation = MediaStagePresentation.WATCH,
    val geometry: StageGeometry = StageGeometry.Flat(widthMeters = 1f, heightMeters = 1f),
    val clock: MediaClockSnapshot = MediaClockSnapshot(),
    val activeVideoTargetId: String? = null,
    val activeOverlayTargetIds: Set<String> = emptySet(),
)

sealed interface MediaStageEvent {
  data class AttachVideoOutput(val targetId: String) : MediaStageEvent

  data class DetachVideoOutput(val targetId: String) : MediaStageEvent

  data class SetOverlayActive(
      val targetId: String,
      val active: Boolean,
  ) : MediaStageEvent

  data class SetTargetEnabled(
      val targetId: String,
      val enabled: Boolean,
  ) : MediaStageEvent

  data class UpdateClock(val clock: MediaClockSnapshot) : MediaStageEvent

  data class Seek(val positionMs: Long) : MediaStageEvent

  data class SetPresentation(
      val presentation: MediaStagePresentation,
      val geometry: StageGeometry,
  ) : MediaStageEvent

  data class ReplaceRegistry(val registry: MediaStageTargetRegistry) : MediaStageEvent
}

enum class OverlayCleanupReason {
  TARGET_DEACTIVATED,
  TARGET_DISABLED,
  TARGET_REMOVED,
  SEEK,
  STAGE_CHANGED,
}

sealed interface MediaStageEffect {
  data class AttachVideoOutput(val targetId: String) : MediaStageEffect

  data class DetachVideoOutput(val targetId: String) : MediaStageEffect

  data class ActivateOverlayTarget(val targetId: String) : MediaStageEffect

  data class ClearOverlayTargets(
      val targetIds: Set<String>,
      val reason: OverlayCleanupReason,
  ) : MediaStageEffect

  data class PauseOverlayTargets(val targetIds: Set<String>) : MediaStageEffect

  data class ResumeOverlayTargets(val targetIds: Set<String>) : MediaStageEffect
}

data class MediaStageTransition(
    val state: MediaStageState,
    val effects: List<MediaStageEffect> = emptyList(),
)

/**
 * Pure lifecycle reducer shared by future 2D and Spatial host adapters. It never receives a
 * player, Surface, Entity, or renderer instance; adapters execute the returned effects.
 */
object MediaStageReducer {
  fun reduce(state: MediaStageState, event: MediaStageEvent): MediaStageTransition =
      when (event) {
        is MediaStageEvent.AttachVideoOutput -> attachVideoOutput(state, event.targetId)
        is MediaStageEvent.DetachVideoOutput -> detachVideoOutput(state, event.targetId)
        is MediaStageEvent.SetOverlayActive -> setOverlayActive(state, event.targetId, event.active)
        is MediaStageEvent.SetTargetEnabled -> setTargetEnabled(state, event.targetId, event.enabled)
        is MediaStageEvent.UpdateClock -> updateClock(state, event.clock)
        is MediaStageEvent.Seek -> seek(state, event.positionMs)
        is MediaStageEvent.SetPresentation -> setPresentation(state, event.presentation, event.geometry)
        is MediaStageEvent.ReplaceRegistry -> replaceRegistry(state, event.registry)
      }

  private fun attachVideoOutput(state: MediaStageState, targetId: String): MediaStageTransition {
    val target = state.registry[targetId]
    if (target?.enabled != true || target.kind != MediaStageTargetKind.VIDEO_OUTPUT) return MediaStageTransition(state)
    if (state.activeVideoTargetId == targetId) return MediaStageTransition(state)

    val effects = buildList {
      state.activeVideoTargetId?.let { add(MediaStageEffect.DetachVideoOutput(it)) }
      add(MediaStageEffect.AttachVideoOutput(targetId))
    }
    return MediaStageTransition(state.copy(activeVideoTargetId = targetId), effects)
  }

  private fun detachVideoOutput(state: MediaStageState, targetId: String): MediaStageTransition {
    if (state.activeVideoTargetId != targetId) return MediaStageTransition(state)
    return MediaStageTransition(
        state.copy(activeVideoTargetId = null),
        listOf(MediaStageEffect.DetachVideoOutput(targetId)),
    )
  }

  private fun setOverlayActive(
      state: MediaStageState,
      targetId: String,
      active: Boolean,
  ): MediaStageTransition {
    val target = state.registry[targetId]
    if (target?.enabled != true || target.isOverlay.not()) return MediaStageTransition(state)
    val currentlyActive = targetId in state.activeOverlayTargetIds
    if (currentlyActive == active) return MediaStageTransition(state)

    return if (active) {
      MediaStageTransition(
          state.copy(activeOverlayTargetIds = state.activeOverlayTargetIds + targetId),
          listOf(MediaStageEffect.ActivateOverlayTarget(targetId)),
      )
    } else {
      MediaStageTransition(
          state.copy(activeOverlayTargetIds = state.activeOverlayTargetIds - targetId),
          listOf(MediaStageEffect.ClearOverlayTargets(setOf(targetId), OverlayCleanupReason.TARGET_DEACTIVATED)),
      )
    }
  }

  private fun setTargetEnabled(
      state: MediaStageState,
      targetId: String,
      enabled: Boolean,
  ): MediaStageTransition {
    val current = state.registry[targetId] ?: return MediaStageTransition(state)
    if (current.enabled == enabled) return MediaStageTransition(state)

    val targets = state.registry.targets + (targetId to current.copy(enabled = enabled))
    val registry = MediaStageTargetRegistry.create(targets.values.toList()).registry
    val next = state.copy(registry = registry)
    if (enabled) return MediaStageTransition(next)

    return when {
      current.kind == MediaStageTargetKind.VIDEO_OUTPUT && state.activeVideoTargetId == targetId ->
          MediaStageTransition(next.copy(activeVideoTargetId = null), listOf(MediaStageEffect.DetachVideoOutput(targetId)))
      current.isOverlay && targetId in state.activeOverlayTargetIds ->
          MediaStageTransition(
              next.copy(activeOverlayTargetIds = state.activeOverlayTargetIds - targetId),
              listOf(MediaStageEffect.ClearOverlayTargets(setOf(targetId), OverlayCleanupReason.TARGET_DISABLED)),
          )
      else -> MediaStageTransition(next)
    }
  }

  private fun updateClock(state: MediaStageState, clock: MediaClockSnapshot): MediaStageTransition {
    if (!clock.isValid()) return MediaStageTransition(state)
    if (clock.isPlaying == state.clock.isPlaying) return MediaStageTransition(state.copy(clock = clock))

    val effect =
        if (clock.isPlaying) {
          MediaStageEffect.ResumeOverlayTargets(state.activeOverlayTargetIds)
        } else {
          MediaStageEffect.PauseOverlayTargets(state.activeOverlayTargetIds)
        }
    return MediaStageTransition(state.copy(clock = clock), listOf(effect).filterNot { it.targetIds().isEmpty() })
  }

  private fun seek(state: MediaStageState, positionMs: Long): MediaStageTransition {
    if (positionMs < 0L) return MediaStageTransition(state)
    val nextClock = state.clock.copy(positionMs = positionMs)
    val activeTargets = state.activeOverlayTargetIds
    val effects =
        if (activeTargets.isEmpty()) emptyList()
        else listOf(MediaStageEffect.ClearOverlayTargets(activeTargets, OverlayCleanupReason.SEEK))
    return MediaStageTransition(state.copy(clock = nextClock), effects)
  }

  private fun setPresentation(
      state: MediaStageState,
      presentation: MediaStagePresentation,
      geometry: StageGeometry,
  ): MediaStageTransition {
    if (!geometry.isValid()) return MediaStageTransition(state)
    if (state.presentation == presentation && state.geometry == geometry) return MediaStageTransition(state)

    val stageLockedTargets =
        state.activeOverlayTargetIds.filterTo(linkedSetOf()) {
          state.registry[it]?.overlayAnchorMode == OverlayAnchorMode.STAGE_LOCKED
        }
    val effects =
        if (stageLockedTargets.isEmpty()) emptyList()
        else listOf(MediaStageEffect.ClearOverlayTargets(stageLockedTargets, OverlayCleanupReason.STAGE_CHANGED))
    return MediaStageTransition(state.copy(presentation = presentation, geometry = geometry), effects)
  }

  private fun replaceRegistry(
      state: MediaStageState,
      registry: MediaStageTargetRegistry,
  ): MediaStageTransition {
    val removedOverlayTargets =
        state.activeOverlayTargetIds.filterTo(linkedSetOf()) { targetId ->
          registry[targetId]?.let { it.enabled && it.isOverlay } != true
        }
    val activeVideo = state.activeVideoTargetId?.takeIf { targetId ->
      registry[targetId]?.let { it.enabled && it.kind == MediaStageTargetKind.VIDEO_OUTPUT } == true
    }
    val effects = buildList {
      if (activeVideo == null) state.activeVideoTargetId?.let { add(MediaStageEffect.DetachVideoOutput(it)) }
      if (removedOverlayTargets.isNotEmpty()) {
        add(MediaStageEffect.ClearOverlayTargets(removedOverlayTargets, OverlayCleanupReason.TARGET_REMOVED))
      }
    }
    return MediaStageTransition(
        state.copy(
            registry = registry,
            activeVideoTargetId = activeVideo,
            activeOverlayTargetIds = state.activeOverlayTargetIds - removedOverlayTargets,
        ),
        effects,
    )
  }

  private fun MediaClockSnapshot.isValid(): Boolean =
      positionMs >= 0L && (durationMs == null || durationMs >= 0L) && (durationMs == null || positionMs <= durationMs)

  private fun MediaStageEffect.targetIds(): Set<String> =
      when (this) {
        is MediaStageEffect.AttachVideoOutput -> emptySet()
        is MediaStageEffect.DetachVideoOutput -> emptySet()
        is MediaStageEffect.ActivateOverlayTarget -> emptySet()
        is MediaStageEffect.ClearOverlayTargets -> targetIds
        is MediaStageEffect.PauseOverlayTargets -> targetIds
        is MediaStageEffect.ResumeOverlayTargets -> targetIds
      }

  private fun StageGeometry.isValid(): Boolean =
      when (this) {
        is StageGeometry.Flat -> widthMeters.isFinite() && widthMeters > 0f && heightMeters.isFinite() && heightMeters > 0f
        is StageGeometry.Cylinder ->
            radiusMeters.isFinite() &&
                radiusMeters > 0f &&
                arcDegrees.isFinite() &&
                arcDegrees > 0f &&
                arcDegrees <= 360f &&
                heightMeters.isFinite() &&
                heightMeters > 0f
      }
}
