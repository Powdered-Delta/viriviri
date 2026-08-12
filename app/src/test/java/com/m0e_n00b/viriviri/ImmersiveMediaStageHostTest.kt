package com.m0e_n00b.viriviri

import com.m0e_n00b.spatialworkbench.core.MediaStageEffect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ImmersiveMediaStageHostTest {
  @Test
  fun firstOutputAttachesOnceAndDuplicateCallbackIsIgnored() {
    val attached = mutableListOf<Any>()
    val effects = mutableListOf<MediaStageEffect>()
    val host = ImmersiveMediaStageHost<Any>(attached::add, effects::add)
    val output = Any()

    host.attachOutput(output)
    host.attachOutput(output)

    assertEquals(listOf(output), attached)
    assertEquals(listOf(MediaStageEffect.AttachVideoOutput("immersive-video")), effects)
    assertEquals("immersive-video", host.state.activeVideoTargetId)
  }

  @Test
  fun replacementOutputUsesExistingSemanticTargetAndReattachesPlatformHandle() {
    val attached = mutableListOf<Any>()
    val effects = mutableListOf<MediaStageEffect>()
    val host = ImmersiveMediaStageHost<Any>(attached::add, effects::add)
    val first = Any()
    val replacement = Any()

    host.attachOutput(first)
    host.attachOutput(replacement)

    assertEquals(listOf(first, replacement), attached)
    assertEquals(listOf(MediaStageEffect.AttachVideoOutput("immersive-video")), effects)
    assertEquals("immersive-video", host.state.activeVideoTargetId)
  }

  @Test
  fun clockAndSeekDoNotAttachAdditionalVideoOutput() {
    val attached = mutableListOf<Any>()
    val effects = mutableListOf<MediaStageEffect>()
    val host = ImmersiveMediaStageHost<Any>(attached::add, effects::add)
    host.attachOutput(Any())

    host.updateClock(positionMs = 1_000L, durationMs = 5_000L, isPlaying = true)
    host.reportSeek(2_000L)

    assertEquals(1, attached.size)
    assertEquals(2_000L, host.state.clock.positionMs)
    assertTrue(effects.all { it is MediaStageEffect.AttachVideoOutput })
  }

  @Test
  fun closeDropsHostHandleWithoutDispatchingVideoDetach() {
    val effects = mutableListOf<MediaStageEffect>()
    val host = ImmersiveMediaStageHost<Any>(attachVideoOutput = {}, onEffect = effects::add)
    host.attachOutput(Any())

    host.close()

    assertEquals("immersive-video", host.state.activeVideoTargetId)
    assertTrue(effects.none { it is MediaStageEffect.DetachVideoOutput })
  }
}
