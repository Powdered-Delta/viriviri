package com.m0e_n00b.viriviri

internal const val PLAYBACK_RESOLUTION_TIMEOUT_MS = 45_000L

internal fun playbackResolutionError(error: Throwable): String =
    if (error is kotlinx.coroutines.TimeoutCancellationException) {
      "Video source resolution timed out"
    } else {
      error.message ?: "Unable to play this video"
    }
