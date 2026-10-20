package com.m0e_n00b.viriviri.playbackstage.host

import com.m0e_n00b.viriviri.PlaybackStageCurvature
import com.m0e_n00b.viriviri.ViriViriUiState
import com.m0e_n00b.viriviri.playbackstage.core.StageCoordinator
import com.m0e_n00b.viriviri.playbackstage.core.StageCurvature
import com.m0e_n00b.viriviri.playbackstage.core.StageInput
import com.m0e_n00b.viriviri.playbackstage.core.StageParameters
import com.m0e_n00b.viriviri.playbackstage.core.StagePresentation
import com.m0e_n00b.viriviri.playbackstage.core.StageVideoMetrics

/** M1 adapter only. The immersive Activity still uses the existing production controller. */
internal class PlaybackStageHost(private val coordinator: StageCoordinator) {
  fun submit(state: ViriViriUiState, video: StageVideoMetrics, presentation: StagePresentation): Boolean =
      coordinator.submit(mapInput(state, video, presentation))

  companion object {
    fun mapInput(state: ViriViriUiState, video: StageVideoMetrics, presentation: StagePresentation): StageInput =
        StageInput(
            StageParameters(
                state.playbackStageScale,
                state.playbackDisplayRatio.displayAspectRatio,
                when (val curvature = state.playbackCurvature) {
                  PlaybackStageCurvature.Flat -> StageCurvature.Flat
                  is PlaybackStageCurvature.Cylinder -> StageCurvature.Cylinder(curvature.radiusMeters)
                },
            ),
            video,
            presentation,
        ).normalized()
  }
}
