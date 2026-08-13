package com.m0e_n00b.viriviri

import com.m0e_n00b.spatialworkbench.core.MediaClockSnapshot
import com.m0e_n00b.spatialworkbench.core.MediaStageEffect
import com.m0e_n00b.spatialworkbench.core.MediaStageEvent
import com.m0e_n00b.spatialworkbench.core.MediaStageReducer
import com.m0e_n00b.spatialworkbench.core.MediaStageState
import com.m0e_n00b.spatialworkbench.core.MediaStageTargetKind
import com.m0e_n00b.spatialworkbench.core.MediaStageTargetRegistry
import com.m0e_n00b.spatialworkbench.core.MediaStageTargetSpec

/**
 * Spatial host adapter for the existing SDK-owned video panel output. The generic output handle
 * stays in the app layer; MediaStageReducer only sees [IMMERSIVE_VIDEO_TARGET_ID].
 */
internal class ImmersiveMediaStageHost<Output : Any>(
    private val attachVideoOutput: (Output) -> Unit,
    private val onEffect: (MediaStageEffect) -> Unit = {},
    private val reattachVideoOutput: (Output) -> Unit = attachVideoOutput,
) {
  private var output: Output? = null
  var state: MediaStageState =
    MediaStageState(
        registry =
          MediaStageTargetRegistry.create(
              listOf(
                  MediaStageTargetSpec(
                      id = IMMERSIVE_VIDEO_TARGET_ID,
                      kind = MediaStageTargetKind.VIDEO_OUTPUT,
                  )
              )
          ).registry
    )
    private set

  fun attachOutput(newOutput: Output) = attachOutput(newOutput, afterHandoff = false)

  /**
   * Reattaches a retained SDK output after an explicit route handoff cleared the player Surface.
   * Normal duplicate callbacks stay no-ops; only the route signal bypasses that optimization.
   */
  fun attachOutputAfterHandoff(newOutput: Output) = attachOutput(newOutput, afterHandoff = true)

  private fun attachOutput(newOutput: Output, afterHandoff: Boolean) {
    val previousOutput = output
    output = newOutput
    val transition = dispatch(MediaStageEvent.AttachVideoOutput(IMMERSIVE_VIDEO_TARGET_ID))
    val attachedByReducer = transition.effects.any { it is MediaStageEffect.AttachVideoOutput }

    // A recreated PanelSceneObject can supply a new SDK-owned Surface for the same semantic
    // target. A route handoff must rebind even when the SDK returns the same Surface object.
    if (!attachedByReducer && (afterHandoff || previousOutput !== newOutput)) {
      if (afterHandoff) reattachVideoOutput(newOutput) else attachVideoOutput(newOutput)
    }
  }

  fun updateClock(positionMs: Long, durationMs: Long?, isPlaying: Boolean) {
    dispatch(MediaStageEvent.UpdateClock(MediaClockSnapshot(positionMs, durationMs, isPlaying)))
  }

  fun reportSeek(positionMs: Long) {
    dispatch(MediaStageEvent.Seek(positionMs))
  }

  /** Drops only the host's SDK handle reference. The host must never release or clear it here. */
  fun close() {
    output = null
  }

  private fun dispatch(event: MediaStageEvent) =
      MediaStageReducer.reduce(state, event).also { transition ->
        state = transition.state
        transition.effects.forEach { effect ->
          onEffect(effect)
          if (effect is MediaStageEffect.AttachVideoOutput) {
            output?.let(attachVideoOutput)
          }
        }
      }

  companion object {
    const val IMMERSIVE_VIDEO_TARGET_ID = "immersive-video"
  }
}
