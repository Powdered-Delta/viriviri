package com.viriviri.app.meta

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppActivityOwnershipRegistryTest {
    @Test
    fun releaseRequiresNoLiveAppActivityAndNoVrHost() {
        val registry = AppActivityOwnershipRegistry<Any>()
        val panel = Any()
        val immersive = Any()

        assertTrue(PlayerReleaseEligibility.shouldRelease(registry.snapshot()))

        registry.register(panel, isVrHost = false)
        assertFalse(PlayerReleaseEligibility.shouldRelease(registry.snapshot()))

        registry.register(immersive, isVrHost = true)
        assertFalse(PlayerReleaseEligibility.shouldRelease(registry.snapshot()))

        registry.unregister(panel)
        assertFalse(PlayerReleaseEligibility.shouldRelease(registry.snapshot()))

        registry.unregister(immersive)
        assertTrue(PlayerReleaseEligibility.shouldRelease(registry.snapshot()))
    }

    @Test
    fun duplicateRegistrationAndThreeCyclesReturnToZero() {
        val registry = AppActivityOwnershipRegistry<Any>()

        repeat(3) {
            val immersive = Any()
            registry.register(immersive, isVrHost = true)
            registry.register(immersive, isVrHost = true)
            assertEquals(AppActivityOwnershipSnapshot(1, 1), registry.snapshot())
            registry.unregister(immersive)
            registry.unregister(immersive)
            assertEquals(AppActivityOwnershipSnapshot(0, 0), registry.snapshot())

            val panel = Any()
            registry.register(panel, isVrHost = false)
            assertEquals(AppActivityOwnershipSnapshot(1, 0), registry.snapshot())
            registry.unregister(panel)
            assertEquals(AppActivityOwnershipSnapshot(0, 0), registry.snapshot())
        }
    }
}
