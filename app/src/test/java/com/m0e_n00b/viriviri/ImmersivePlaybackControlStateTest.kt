package com.m0e_n00b.viriviri

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ImmersivePlaybackControlStateTest {
  @Test
  fun iconFollowsPlaybackIntentWhileFadeFollowsActualPlayback() {
    val bufferingWithPlayIntent = immersivePlaybackControlState(playWhenReady = true, isPlaying = false)
    val paused = immersivePlaybackControlState(playWhenReady = false, isPlaying = false)
    val playing = immersivePlaybackControlState(playWhenReady = true, isPlaying = true)

    assertTrue(bufferingWithPlayIntent.showPauseIcon)
    assertFalse(bufferingWithPlayIntent.isActuallyPlaying)
    assertFalse(paused.showPauseIcon)
    assertFalse(paused.isActuallyPlaying)
    assertTrue(playing.showPauseIcon)
    assertTrue(playing.isActuallyPlaying)
  }

  @Test
  fun seekDragRestoresPreviousPlayIntentAndDoesNotInventResume() {
    val policy = SeekDragPlaybackPolicy()

    assertTrue(policy.start(true))
    assertEquals(true, policy.finish())
    assertNull(policy.finish())

    assertFalse(policy.start(false))
    assertEquals(false, policy.finish())
  }
}
