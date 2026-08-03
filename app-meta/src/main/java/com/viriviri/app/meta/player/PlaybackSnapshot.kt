package com.viriviri.app.meta.player

data class PlaybackSnapshot(
    val mediaUri: String,
    val positionMs: Long,
    val playWhenReady: Boolean,
    val wasPlaying: Boolean,
    val capturedAtElapsedMs: Long,
)
