package com.viriviri.core.state

enum class TransitionPlaybackPolicy(val label: String) {
    AUTO("Auto"),
    CONTINUE_PLAYBACK("Continue playback"),
    PAUSE_AND_RESUME("Pause and resume"),
}

enum class HandoffTarget(val label: String) {
    IMMERSIVE("Immersive mode"),
    SYSTEM_2D_PANEL("Horizon OS 2D panel"),
}

enum class SourceFinishDisposition(val label: String) {
    NOT_REQUESTED("Not requested"),
    FINISH_ACTIVITY("Finish source Activity"),
    REMOVE_SOURCE_TASK("Remove source task"),
    SOURCE_ALREADY_GONE("Source already gone"),
}

data class SurfaceHandoffMetrics(
    val prepareCalls: Int = 0,
    val videoDecoderInitializations: Int = 0,
    val surfaceHandoffs: Int = 0,
    val playbackPositionMs: Long = 0,
    val lastHandoffDurationMs: Long? = null,
    val activePolicy: TransitionPlaybackPolicy = TransitionPlaybackPolicy.CONTINUE_PLAYBACK,
    val transitionId: Long? = null,
    val sourceTarget: HandoffTarget? = null,
    val destinationTarget: HandoffTarget? = null,
    val currentTarget: HandoffTarget? = null,
    val destinationSurfaceReady: Boolean = false,
    val destinationFirstFrameReady: Boolean = false,
    val destinationSurfaceAttachedAfterMs: Long? = null,
    val destinationFirstFrameAfterMs: Long? = null,
    val playingWithoutVisibleDestinationMs: Long? = null,
    val sourceFinishedAfterMs: Long? = null,
    val sourceFinishDisposition: SourceFinishDisposition = SourceFinishDisposition.NOT_REQUESTED,
    val transitionTimedOut: Boolean = false,
)
