package com.viriviri.app.meta.player

import com.viriviri.core.state.HandoffTarget
import com.viriviri.core.state.HandoffExperimentMode
import org.junit.Assert.assertEquals
import org.junit.Test

class SurfaceReplacementModeTest {
    @Test
    fun protectedImmersiveToPanelUsesDirectReplacement() {
        assertEquals(
            SurfaceReplacementMode.DIRECT_SPATIAL_TO_TEXTURE,
            surfaceReplacementMode(
                currentTarget = HandoffTarget.IMMERSIVE,
                destinationTarget = HandoffTarget.SYSTEM_2D_PANEL,
                protectedHandoff = true,
            ),
        )
    }

    @Test
    fun unprotectedImmersiveToPanelUsesClearThenSet() {
        assertEquals(
            SurfaceReplacementMode.CLEAR_THEN_SET,
            surfaceReplacementMode(
                currentTarget = HandoffTarget.IMMERSIVE,
                destinationTarget = HandoffTarget.SYSTEM_2D_PANEL,
                protectedHandoff = false,
            ),
        )
    }

    @Test
    fun directReplacementDoesNotApplyToOtherDirections() {
        assertEquals(
            SurfaceReplacementMode.CLEAR_THEN_SET,
            surfaceReplacementMode(
                currentTarget = HandoffTarget.SYSTEM_2D_PANEL,
                destinationTarget = HandoffTarget.IMMERSIVE,
                protectedHandoff = true,
            ),
        )
    }

    @Test
    fun nonDirectExperimentModesUseClearThenSet() {
        listOf(
            HandoffExperimentMode.CLEAR_RECOVERY,
            HandoffExperimentMode.REPREPARE_BASELINE,
        ).forEach { mode ->
            assertEquals(
                SurfaceReplacementMode.CLEAR_THEN_SET,
                surfaceReplacementMode(
                    currentTarget = HandoffTarget.IMMERSIVE,
                    destinationTarget = HandoffTarget.SYSTEM_2D_PANEL,
                    protectedHandoff = true,
                    experimentMode = mode,
                ),
            )
        }
    }
}
