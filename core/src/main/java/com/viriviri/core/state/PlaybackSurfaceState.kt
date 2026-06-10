package com.viriviri.core.state

import com.viriviri.core.model.VideoId

data class PlaybackSurfaceState(
    val activeVideoId: VideoId? = null,
    val isPlaying: Boolean = false,
    val positionSeconds: Int = 0,
)
