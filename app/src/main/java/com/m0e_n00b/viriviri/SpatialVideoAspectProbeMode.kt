package com.m0e_n00b.viriviri

internal enum class SpatialVideoAspectProbeTarget(
    val label: String,
    val displayAspectRatio: Float?,
) {
  DEFAULT("Default", null),
  WIDESCREEN_16_9("16:9", 16f / 9f),
  STANDARD_4_3("4:3", 4f / 3f),
  SQUARE_1_1("1:1", 1f),
  PORTRAIT_9_16("9:16", 9f / 16f),
}

internal enum class SpatialVideoAspectProbePlan(val label: String) {
  PLAN_1("Plan 1"),
  PLAN_2("Plan 2"),
  PLAN_3("Plan 3"),
}

internal data class SpatialVideoAspectProbeState(
    val pendingTarget: SpatialVideoAspectProbeTarget = SpatialVideoAspectProbeTarget.DEFAULT,
    val pendingPlan: SpatialVideoAspectProbePlan = SpatialVideoAspectProbePlan.PLAN_1,
    val appliedTarget: SpatialVideoAspectProbeTarget = SpatialVideoAspectProbeTarget.DEFAULT,
    val appliedPlan: SpatialVideoAspectProbePlan = SpatialVideoAspectProbePlan.PLAN_1,
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
