package com.viriviri.app.meta.player

import com.viriviri.core.state.HandoffTarget
import com.viriviri.core.state.HandoffExperimentMode

internal enum class SurfaceReplacementMode {
    CLEAR_THEN_SET,
    DIRECT_SPATIAL_TO_TEXTURE,
}

internal fun surfaceReplacementMode(
    currentTarget: HandoffTarget?,
    destinationTarget: HandoffTarget,
    protectedHandoff: Boolean,
    experimentMode: HandoffExperimentMode = HandoffExperimentMode.DIRECT_RECOVERY,
): SurfaceReplacementMode =
    if (
        protectedHandoff &&
        currentTarget == HandoffTarget.IMMERSIVE &&
        destinationTarget == HandoffTarget.SYSTEM_2D_PANEL &&
        experimentMode == HandoffExperimentMode.DIRECT_RECOVERY
    ) {
        SurfaceReplacementMode.DIRECT_SPATIAL_TO_TEXTURE
    } else {
        SurfaceReplacementMode.CLEAR_THEN_SET
    }

internal val SurfaceReplacementMode.logName: String
    get() = when (this) {
        SurfaceReplacementMode.CLEAR_THEN_SET -> "clear_then_set"
        SurfaceReplacementMode.DIRECT_SPATIAL_TO_TEXTURE -> "direct_spatial_to_texture"
    }
