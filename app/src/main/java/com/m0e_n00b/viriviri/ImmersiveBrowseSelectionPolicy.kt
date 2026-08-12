package com.m0e_n00b.viriviri

import com.m0e_n00b.spatialworkbench.core.PlaybackCanvas

internal fun shouldReturnToPlaybackAfterBrowseSelection(
    awaitingSelection: Boolean,
    canvas: PlaybackCanvas,
    baselineVideoId: String?,
    selectedVideoId: String?,
): Boolean =
    awaitingSelection &&
        canvas == PlaybackCanvas.BROWSE &&
        selectedVideoId != null &&
        selectedVideoId != baselineVideoId
