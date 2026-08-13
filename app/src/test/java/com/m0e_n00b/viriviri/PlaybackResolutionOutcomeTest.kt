package com.m0e_n00b.viriviri

import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Test

class PlaybackResolutionOutcomeTest {
  @Test
  fun timeoutMapsToStableRetryableMessage() {
    val error =
        runCatching {
          runBlocking {
            withTimeout(1L) { delay(1_000L) }
          }
        }.exceptionOrNull()!!
    assertEquals("Video source resolution timed out", playbackResolutionError(error))
  }

  @Test
  fun otherErrorsKeepMessageOrSafeFallback() {
    assertEquals("Bilibili unavailable", playbackResolutionError(IllegalStateException("Bilibili unavailable")))
    assertEquals("Unable to play this video", playbackResolutionError(IllegalStateException()))
  }
}
