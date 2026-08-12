package com.m0e_n00b.spatialworkbench.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaStageContractsTest {
  @Test
  fun registryRejectsDuplicateAndMalformedTargets() {
    val build =
        MediaStageTargetRegistry.create(
            listOf(
                MediaStageTargetSpec("video", MediaStageTargetKind.VIDEO_OUTPUT),
                MediaStageTargetSpec("video", MediaStageTargetKind.VIDEO_OUTPUT),
                MediaStageTargetSpec("wrong-video", MediaStageTargetKind.VIDEO_OUTPUT, overlaySurfaceId = "caption"),
                MediaStageTargetSpec("wrong-overlay", MediaStageTargetKind.FLAT_OVERLAY),
            )
        )

    assertTrue(build.issues.any { it is MediaStageRegistryIssue.DuplicateTargetId && it.id == "video" })
    assertTrue(build.issues.any { it is MediaStageRegistryIssue.InvalidTarget && it.id == "wrong-video" })
    assertTrue(build.issues.any { it is MediaStageRegistryIssue.InvalidTarget && it.id == "wrong-overlay" })
    assertFalse("video" in build.registry.targets)
  }

  @Test
  fun videoTargetReplacementDetachesOldBeforeAttachingNew() {
    val state = state(video("two-d"), video("spatial"))
    val first = MediaStageReducer.reduce(state, MediaStageEvent.AttachVideoOutput("two-d"))
    val replacement = MediaStageReducer.reduce(first.state, MediaStageEvent.AttachVideoOutput("spatial"))

    assertEquals("spatial", replacement.state.activeVideoTargetId)
    assertEquals(
        listOf(
            MediaStageEffect.DetachVideoOutput("two-d"),
            MediaStageEffect.AttachVideoOutput("spatial"),
        ),
        replacement.effects,
    )
  }

  @Test
  fun staleVideoDetachCannotRemoveNewerOutput() {
    val state =
        MediaStageReducer.reduce(
                state(video("two-d"), video("spatial")),
                MediaStageEvent.AttachVideoOutput("spatial"),
            )
            .state

    val result = MediaStageReducer.reduce(state, MediaStageEvent.DetachVideoOutput("two-d"))

    assertEquals("spatial", result.state.activeVideoTargetId)
    assertTrue(result.effects.isEmpty())
  }

  @Test
  fun overlayTargetCanCoexistButCannotBecomeVideoOutput() {
    val state = state(video("video"), overlay("flat", OverlayAnchorMode.STAGE_LOCKED))
    val withVideo = MediaStageReducer.reduce(state, MediaStageEvent.AttachVideoOutput("video")).state
    val withOverlay = MediaStageReducer.reduce(withVideo, MediaStageEvent.SetOverlayActive("flat", true))
    val invalidVideoAttach = MediaStageReducer.reduce(withOverlay.state, MediaStageEvent.AttachVideoOutput("flat"))

    assertEquals("video", withOverlay.state.activeVideoTargetId)
    assertEquals(setOf("flat"), withOverlay.state.activeOverlayTargetIds)
    assertEquals(listOf(MediaStageEffect.ActivateOverlayTarget("flat")), withOverlay.effects)
    assertEquals(withOverlay.state, invalidVideoAttach.state)
    assertTrue(invalidVideoAttach.effects.isEmpty())
  }

  @Test
  fun disablingOverlayClearsOnlyThatTargetAndKeepsVideoOutput() {
    val initial =
        MediaStageReducer.reduce(
                MediaStageReducer.reduce(
                        state(video("video"), overlay("flat", OverlayAnchorMode.STAGE_LOCKED)),
                        MediaStageEvent.AttachVideoOutput("video"),
                    )
                    .state,
                MediaStageEvent.SetOverlayActive("flat", true),
            )
            .state

    val result = MediaStageReducer.reduce(initial, MediaStageEvent.SetTargetEnabled("flat", false))

    assertEquals("video", result.state.activeVideoTargetId)
    assertTrue(result.state.activeOverlayTargetIds.isEmpty())
    assertEquals(
        listOf(MediaStageEffect.ClearOverlayTargets(setOf("flat"), OverlayCleanupReason.TARGET_DISABLED)),
        result.effects,
    )
  }

  @Test
  fun pauseResumeAndSeekUseOverlayLifecycleEffectsWithoutChangingVideoOwner() {
    val active = activeState(OverlayAnchorMode.STAGE_LOCKED)
    val playing = MediaStageReducer.reduce(active, MediaStageEvent.UpdateClock(MediaClockSnapshot(positionMs = 100L, isPlaying = true)))
    val paused = MediaStageReducer.reduce(playing.state, MediaStageEvent.UpdateClock(MediaClockSnapshot(positionMs = 200L, isPlaying = false)))
    val seek = MediaStageReducer.reduce(paused.state, MediaStageEvent.Seek(9_000L))

    assertEquals(listOf(MediaStageEffect.ResumeOverlayTargets(setOf("overlay"))), playing.effects)
    assertEquals(listOf(MediaStageEffect.PauseOverlayTargets(setOf("overlay"))), paused.effects)
    assertEquals(
        listOf(MediaStageEffect.ClearOverlayTargets(setOf("overlay"), OverlayCleanupReason.SEEK)),
        seek.effects,
    )
    assertEquals("video", seek.state.activeVideoTargetId)
    assertEquals(9_000L, seek.state.clock.positionMs)
  }

  @Test
  fun stagePresentationChangeClearsStageLockedButNotGazeLockedOverlays() {
    val stageLocked = overlay("stage", OverlayAnchorMode.STAGE_LOCKED)
    val gazeLocked = overlay("gaze", OverlayAnchorMode.GAZE_LOCKED)
    var state = state(video("video"), stageLocked, gazeLocked)
    state = MediaStageReducer.reduce(state, MediaStageEvent.AttachVideoOutput("video")).state
    state = MediaStageReducer.reduce(state, MediaStageEvent.SetOverlayActive("stage", true)).state
    state = MediaStageReducer.reduce(state, MediaStageEvent.SetOverlayActive("gaze", true)).state

    val result =
        MediaStageReducer.reduce(
            state,
            MediaStageEvent.SetPresentation(
                MediaStagePresentation.SHORTS,
                StageGeometry.Flat(widthMeters = 0.7f, heightMeters = 1.2f),
            ),
        )

    assertEquals(MediaStagePresentation.SHORTS, result.state.presentation)
    assertEquals("video", result.state.activeVideoTargetId)
    assertEquals(setOf("stage", "gaze"), result.state.activeOverlayTargetIds)
    assertEquals(
        listOf(MediaStageEffect.ClearOverlayTargets(setOf("stage"), OverlayCleanupReason.STAGE_CHANGED)),
        result.effects,
    )
  }

  @Test
  fun invalidClockAndGeometryAreIgnored() {
    val initial = activeState(OverlayAnchorMode.STAGE_LOCKED)
    val invalidClock =
        MediaStageReducer.reduce(initial, MediaStageEvent.UpdateClock(MediaClockSnapshot(positionMs = -1L, isPlaying = true)))
    val invalidGeometry =
        MediaStageReducer.reduce(
            initial,
            MediaStageEvent.SetPresentation(
                MediaStagePresentation.SHORTS,
                StageGeometry.Cylinder(radiusMeters = 1f, arcDegrees = 361f, heightMeters = 1f),
            ),
        )

    assertEquals(initial, invalidClock.state)
    assertTrue(invalidClock.effects.isEmpty())
    assertEquals(initial, invalidGeometry.state)
    assertTrue(invalidGeometry.effects.isEmpty())
  }

  @Test
  fun registryReplacementCleansRemovedTargetsWithoutDetachingSurvivingVideo() {
    val active = activeState(OverlayAnchorMode.STAGE_LOCKED)
    val replacement = MediaStageTargetRegistry.create(listOf(video("video"))).registry

    val result = MediaStageReducer.reduce(active, MediaStageEvent.ReplaceRegistry(replacement))

    assertEquals("video", result.state.activeVideoTargetId)
    assertTrue(result.state.activeOverlayTargetIds.isEmpty())
    assertEquals(
        listOf(MediaStageEffect.ClearOverlayTargets(setOf("overlay"), OverlayCleanupReason.TARGET_REMOVED)),
        result.effects,
    )
  }

  private fun activeState(anchorMode: OverlayAnchorMode): MediaStageState {
    var state = state(video("video"), overlay("overlay", anchorMode))
    state = MediaStageReducer.reduce(state, MediaStageEvent.AttachVideoOutput("video")).state
    return MediaStageReducer.reduce(state, MediaStageEvent.SetOverlayActive("overlay", true)).state
  }

  private fun state(vararg targets: MediaStageTargetSpec): MediaStageState =
      MediaStageState(registry = MediaStageTargetRegistry.create(targets.toList()).registry)

  private fun video(id: String) = MediaStageTargetSpec(id, MediaStageTargetKind.VIDEO_OUTPUT)

  private fun overlay(id: String, anchorMode: OverlayAnchorMode) =
      MediaStageTargetSpec(
          id = id,
          kind = MediaStageTargetKind.FLAT_OVERLAY,
          overlaySurfaceId = "$id-surface",
          overlayAnchorMode = anchorMode,
      )
}
