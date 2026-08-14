package com.m0e_n00b.viriviri

internal enum class SpatialVideoAspectProbeTarget(
    val label: String,
    val displayRatio: PlaybackDisplayRatio,
) {
  DEFAULT("Default", PlaybackDisplayRatio.AUTO),
  WIDESCREEN_16_9("16:9", PlaybackDisplayRatio.WIDESCREEN_16_9),
  STANDARD_4_3("4:3", PlaybackDisplayRatio.STANDARD_4_3),
  SQUARE_1_1("1:1", PlaybackDisplayRatio.SQUARE_1_1),
  PORTRAIT_9_16("9:16", PlaybackDisplayRatio.PORTRAIT_9_16),
  ;

  val displayAspectRatio: Float?
    get() = displayRatio.displayAspectRatio

  companion object {
    fun from(displayRatio: PlaybackDisplayRatio): SpatialVideoAspectProbeTarget =
        entries.first { it.displayRatio == displayRatio }
  }
}

internal enum class SpatialVideoAspectProbePlan(val label: String) {
  PLAN_1("Plan 1"),
  PANEL_RESHAPE("Panel reshape"),
}

internal data class SpatialVideoAspectProbeState(
    val pendingTarget: SpatialVideoAspectProbeTarget = SpatialVideoAspectProbeTarget.DEFAULT,
    val pendingPlan: SpatialVideoAspectProbePlan = SpatialVideoAspectProbePlan.PANEL_RESHAPE,
    val appliedTarget: SpatialVideoAspectProbeTarget = SpatialVideoAspectProbeTarget.DEFAULT,
    val appliedPlan: SpatialVideoAspectProbePlan = SpatialVideoAspectProbePlan.PANEL_RESHAPE,
)

internal object SpatialVideoAspectProbeReducer {
  fun selectTarget(
      state: SpatialVideoAspectProbeState,
      target: SpatialVideoAspectProbeTarget,
  ): SpatialVideoAspectProbeState = state.copy(pendingTarget = target)

  fun selectPlan(
      state: SpatialVideoAspectProbeState,
      plan: SpatialVideoAspectProbePlan,
  ): SpatialVideoAspectProbeState = state.copy(pendingPlan = plan)

  fun apply(state: SpatialVideoAspectProbeState): SpatialVideoAspectProbeState =
      state.copy(appliedTarget = state.pendingTarget, appliedPlan = state.pendingPlan)
}
