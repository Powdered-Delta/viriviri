package com.viriviri.core.state

data class SurfaceHandoffMetrics(
    val prepareCalls: Int = 0,
    val videoDecoderInitializations: Int = 0,
    val surfaceHandoffs: Int = 0,
    val playbackPositionMs: Long = 0,
    val lastHandoffDurationMs: Long? = null,
)
