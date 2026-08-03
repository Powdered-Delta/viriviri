package com.viriviri.app.meta

import com.viriviri.core.state.HandoffTarget
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HybridRouteTransitionTest {
    @Test
    fun immersiveToPanelCannotAttachUntilSourceIsDestroyed() {
        val transition = transition(destinationLaunchBeforeSourceDestroy = true)

        assertTrue(transition.markDestinationLaunched())
        assertFalse(transition.canRequestSourceFinish)
        assertTrue(transition.markDestinationCreated())
        assertTrue(transition.canRequestSourceFinish)
        assertEquals(RouteFinishMode.ACTIVITY_ONLY, transition.sourceFinishMode)
        assertTrue(transition.markSourceFinishRequested())
        assertFalse(transition.canAttachDestination)
        assertFalse(transition.markSurfaceAttached())

        assertTrue(transition.markSourceDestroyed())
        assertEquals(HybridRoutePhase.WAITING_FOR_DESTINATION_SURFACE, transition.phase)
        assertTrue(transition.canAttachDestination)
        assertTrue(transition.markSurfaceAttached())
        assertTrue(transition.markFirstFrame())
        assertEquals(HybridRoutePhase.COMPLETED, transition.phase)
    }

    @Test
    fun panelToImmersiveLaunchesOnlyAfterPanelDestruction() {
        val transition = transition(destinationLaunchBeforeSourceDestroy = false)

        assertFalse(transition.markDestinationLaunched())
        assertTrue(transition.markSourceFinishRequested())
        assertTrue(transition.markSourceDestroyed())
        assertEquals(HybridRoutePhase.READY_TO_LAUNCH_DESTINATION, transition.phase)
        assertFalse(transition.canAttachDestination)

        assertTrue(transition.markDestinationLaunched())
        assertEquals(HybridRoutePhase.WAITING_FOR_DESTINATION_SURFACE, transition.phase)
        assertTrue(transition.markSurfaceAttached())
        assertTrue(transition.markFirstFrame())
    }

    @Test
    fun firstFrameRequiresSurfaceAndTerminalStatesAreIdempotent() {
        val transition = transition(destinationLaunchBeforeSourceDestroy = true)
        transition.markDestinationLaunched()
        transition.markSourceDestroyed()

        assertFalse(transition.markFirstFrame())
        assertTrue(transition.markSurfaceAttached())
        assertFalse(transition.markSurfaceAttached())
        assertTrue(transition.markFirstFrame())
        assertFalse(transition.markFirstFrame())
        assertFalse(transition.fail("late_failure"))
    }

    @Test
    fun failureIsExplicitAndPreventsLateCompletion() {
        val transition = transition(destinationLaunchBeforeSourceDestroy = false)

        assertTrue(transition.fail("timeout"))
        assertEquals(HybridRoutePhase.FAILED, transition.phase)
        assertEquals("timeout", transition.failureReason)
        assertFalse(transition.markSourceDestroyed())
        assertFalse(transition.markDestinationLaunched())
        assertFalse(transition.markSurfaceAttached())
        assertFalse(transition.markFirstFrame())
    }

    @Test
    fun targetStartBeforeSourceDestroyKeepsActivityOnlyFinishAndAttachGate() {
        val transition = transition(destinationLaunchBeforeSourceDestroy = true)

        assertTrue(transition.markDestinationLaunched())
        assertFalse(transition.canRequestSourceFinish)
        assertEquals(RouteFinishMode.ACTIVITY_ONLY, transition.sourceFinishMode)
        assertEquals(HybridRoutePhase.WAITING_FOR_SOURCE_DESTROY, transition.phase)
        assertFalse(transition.canAttachDestination)

        assertTrue(transition.markDestinationCreated())
        assertTrue(transition.canRequestSourceFinish)
        assertTrue(transition.markSourceFinishRequested())
        assertTrue(transition.markSourceDestroyed())
        assertEquals(HybridRoutePhase.WAITING_FOR_DESTINATION_SURFACE, transition.phase)
        assertTrue(transition.canAttachDestination)
    }

    @Test
    fun threeCyclesResetToIndependentTerminalState() {
        repeat(3) { cycle ->
            val toPanel = transition(destinationLaunchBeforeSourceDestroy = true, id = cycle * 2L + 1)
            assertTrue(toPanel.markDestinationLaunched())
            assertTrue(toPanel.markDestinationCreated())
            assertTrue(toPanel.markSourceFinishRequested())
            assertTrue(toPanel.markSourceDestroyed())
            assertTrue(toPanel.markSurfaceAttached())
            assertTrue(toPanel.markFirstFrame())
            assertEquals(HybridRoutePhase.COMPLETED, toPanel.phase)

            val toImmersive = transition(destinationLaunchBeforeSourceDestroy = false, id = cycle * 2L + 2)
            assertTrue(toImmersive.markSourceFinishRequested())
            assertTrue(toImmersive.markSourceDestroyed())
            assertEquals(HybridRoutePhase.READY_TO_LAUNCH_DESTINATION, toImmersive.phase)
            assertTrue(toImmersive.markDestinationLaunched())
            assertTrue(toImmersive.markSurfaceAttached())
            assertTrue(toImmersive.markFirstFrame())
            assertEquals(HybridRoutePhase.COMPLETED, toImmersive.phase)
            assertEquals(RouteFinishMode.ACTIVITY_ONLY, toImmersive.sourceFinishMode)
        }
    }

    @Test
    fun immersiveSourceStaysAliveUntilPanelActivityMaterializes() {
        val transition = transition(destinationLaunchBeforeSourceDestroy = true)

        assertTrue(transition.markDestinationLaunched())
        assertFalse(transition.destinationCreated)
        assertFalse(transition.canRequestSourceFinish)
        assertTrue(transition.markDestinationCreated())
        assertTrue(transition.destinationCreated)
        assertTrue(transition.canRequestSourceFinish)
    }

    private fun transition(destinationLaunchBeforeSourceDestroy: Boolean, id: Long = 7) = HybridRouteTransition(
        id = id,
        source = if (destinationLaunchBeforeSourceDestroy) HandoffTarget.IMMERSIVE else HandoffTarget.SYSTEM_2D_PANEL,
        destination = if (destinationLaunchBeforeSourceDestroy) HandoffTarget.SYSTEM_2D_PANEL else HandoffTarget.IMMERSIVE,
        destinationLaunchBeforeSourceDestroy = destinationLaunchBeforeSourceDestroy,
        requestedAtMs = 100L,
        clockMs = { 145L },
    )
}
