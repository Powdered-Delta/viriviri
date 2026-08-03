package com.viriviri.core.state

enum class TransitionPlaybackPolicy(val label: String) {
    AUTO("Auto"),
    CONTINUE_PLAYBACK("Continue playback"),
    PAUSE_AND_RESUME("Pause and resume"),
}

enum class HandoffExperimentMode(val label: String) {
    DIRECT_RECOVERY("Direct + recovery"),
    CLEAR_RECOVERY("Clear + recovery"),
    REPREPARE_BASELINE("Reprepare baseline"),
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

enum class HandoffRouteState(val label: String) {
    IDLE("Idle"),
    WAITING_FOR_SOURCE_DESTROY("Waiting for source Activity shutdown"),
    READY_TO_LAUNCH_DESTINATION("Source destroyed; destination launch ready"),
    WAITING_FOR_DESTINATION_SURFACE("Waiting for destination Surface"),
    WAITING_FOR_FIRST_FRAME("Waiting for destination first frame"),
    COMPLETED("Completed"),
    FAILED("Failed"),
}

data class SurfaceHandoffMetrics(
    val prepareCalls: Int = 0,
    val videoDecoderInitializations: Int = 0,
    val handoffDecoderRecoveries: Int = 0,
    val surfaceHandoffs: Int = 0,
    val playbackPositionMs: Long = 0,
    val lastHandoffDurationMs: Long? = null,
    val activePolicy: TransitionPlaybackPolicy = TransitionPlaybackPolicy.CONTINUE_PLAYBACK,
    val experimentMode: HandoffExperimentMode = HandoffExperimentMode.DIRECT_RECOVERY,
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
    val routeState: HandoffRouteState = HandoffRouteState.IDLE,
    val sourceDestroyedAfterMs: Long? = null,
    val transitionFailureReason: String? = null,
)
