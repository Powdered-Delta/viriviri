package com.viriviri.app.meta.player

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackLifecycleOwnershipTest {
    @Test
    fun releasesWhenStoppedWithoutConfigurationChangeOrHandoff() {
        assertTrue(
            PlaybackLifecycleOwnership.shouldReleasePlayer(
                isChangingConfigurations = false,
                isHandoffProtected = false,
            ),
        )
    }

    @Test
    fun retainsPlayerForConfigurationChange() {
        assertFalse(
            PlaybackLifecycleOwnership.shouldReleasePlayer(
                isChangingConfigurations = true,
                isHandoffProtected = false,
            ),
        )
    }

    @Test
    fun retainsPlayerForPendingOrCompletingHandoff() {
        assertFalse(
            PlaybackLifecycleOwnership.shouldReleasePlayer(
                isChangingConfigurations = false,
                isHandoffProtected = true,
            ),
        )
    }
}
