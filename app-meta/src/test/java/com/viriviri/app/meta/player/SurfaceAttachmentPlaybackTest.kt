package com.viriviri.app.meta.player

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SurfaceAttachmentPlaybackTest {
    @Test
    fun requestsPlaybackForReplacementOutput() {
        assertTrue(shouldRequestPlaybackAfterAttach(replacedOutput = true))
    }

    @Test
    fun doesNotRequestPlaybackForSameOutputNoOp() {
        assertFalse(shouldRequestPlaybackAfterAttach(replacedOutput = false))
    }
}
