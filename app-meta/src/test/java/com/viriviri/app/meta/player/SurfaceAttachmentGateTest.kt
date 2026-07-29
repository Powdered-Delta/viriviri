package com.viriviri.app.meta.player

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SurfaceAttachmentGateTest {
    @Test
    fun failedAttachmentDoesNotMakeSurfaceOrFirstFrameReady() {
        val gate = SurfaceAttachmentGate()

        assertFalse(gate.markAttachment(verifiedCurrentOutput = false))
        assertFalse(gate.markFirstFrame())
    }

    @Test
    fun verifiedAttachmentPermitsOnlyOneFirstFrame() {
        val gate = SurfaceAttachmentGate()

        assertTrue(gate.markAttachment(verifiedCurrentOutput = true))
        assertTrue(gate.markFirstFrame())
        assertFalse(gate.markFirstFrame())
    }

    @Test
    fun resetRevokesThePreviousAttachmentBeforeTheNextFrame() {
        val gate = SurfaceAttachmentGate()

        assertTrue(gate.markAttachment(verifiedCurrentOutput = true))
        gate.reset()

        assertFalse(gate.markFirstFrame())
    }
}
