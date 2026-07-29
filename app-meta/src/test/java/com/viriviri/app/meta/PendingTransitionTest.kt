package com.viriviri.app.meta

import android.app.Activity
import com.viriviri.core.state.HandoffTarget
import java.lang.ref.WeakReference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PendingTransitionTest {
    @Test
    fun firstFrameRequiresSurfaceAndMilestonesAreIdempotent() {
        var nowMs = 100L
        val transition = PendingTransition(
            id = 7,
            source = HandoffTarget.IMMERSIVE,
            destination = HandoffTarget.SYSTEM_2D_PANEL,
            requestedAtMs = 100L,
            sourceActivity = WeakReference<Activity>(null),
            clockMs = { nowMs },
        )

        assertFalse(transition.markFirstFrame())
        assertTrue(transition.markSurfaceAttached())
        assertFalse(transition.markSurfaceAttached())
        nowMs = 145L
        assertTrue(transition.markFirstFrame())
        assertFalse(transition.markFirstFrame())
        assertEquals(45L, transition.elapsedMs())
    }

    @Test
    fun timeoutMakesLateSurfaceAndFrameCallbacksStale() {
        val transition = PendingTransition(
            id = 8,
            source = HandoffTarget.SYSTEM_2D_PANEL,
            destination = HandoffTarget.IMMERSIVE,
            requestedAtMs = 100L,
            sourceActivity = WeakReference<Activity>(null),
            clockMs = { 15_100L },
        )

        assertTrue(transition.markTimedOut())
        assertFalse(transition.markTimedOut())
        assertFalse(transition.markSurfaceAttached())
        assertFalse(transition.markFirstFrame())
        assertEquals(15_000L, transition.elapsedMs())
    }
}
