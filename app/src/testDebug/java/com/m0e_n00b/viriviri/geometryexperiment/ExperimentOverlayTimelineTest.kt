package com.m0e_n00b.viriviri.geometryexperiment

import org.junit.Assert.*
import org.junit.Test

class ExperimentOverlayTimelineTest {
  @Test fun danmakuMovesAcrossTheScreenWithStaggeredLanesAndLoops() {
    assertEquals(0f, ExperimentOverlayTimeline.danmakuProgress(0, 0), 0f)
    assertEquals(0.5f, ExperimentOverlayTimeline.danmakuProgress(4000, 0), 0f)
    assertEquals(0.2125f, ExperimentOverlayTimeline.danmakuProgress(0, 1), 0f)
    for (lane in ExperimentOverlayTimeline.danmaku.indices) {
      assertEquals(ExperimentOverlayTimeline.danmakuProgress(1234, lane),
          ExperimentOverlayTimeline.danmakuProgress(9234, lane), 0f)
    }
  }

  @Test fun subtitlesFollowTheMediaPositionAcrossReplacementAndLooping() {
    val beforeReplacement = ExperimentOverlayTimeline.subtitle(4500)
    assertEquals(ExperimentOverlayTimeline.subtitles[1], beforeReplacement)
    assertEquals(ExperimentOverlayTimeline.subtitles[0], ExperimentOverlayTimeline.subtitle(3999))
    assertEquals(ExperimentOverlayTimeline.subtitles[1], ExperimentOverlayTimeline.subtitle(4000))
    assertEquals(ExperimentOverlayTimeline.subtitles[2], ExperimentOverlayTimeline.subtitle(8000))
    assertEquals(ExperimentOverlayTimeline.subtitles[0], ExperimentOverlayTimeline.subtitle(0))
    assertEquals(ExperimentOverlayTimeline.subtitle(0), ExperimentOverlayTimeline.subtitle(12000))
    assertEquals(ExperimentOverlayTimeline.subtitle(0), ExperimentOverlayTimeline.subtitle(-1))
    for (lane in ExperimentOverlayTimeline.danmaku.indices) {
      assertEquals(ExperimentOverlayTimeline.danmakuProgress(0, lane),
          ExperimentOverlayTimeline.danmakuProgress(-1, lane), 0f)
      assertTrue(ExperimentOverlayTimeline.danmakuProgress(Long.MAX_VALUE, lane) in 0f..1f)
    }
  }
}
