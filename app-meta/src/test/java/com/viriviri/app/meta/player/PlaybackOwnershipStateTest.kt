package com.viriviri.app.meta.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackOwnershipStateTest {
    @Test
    fun loadOnceRunsMediaLoadOnlyOnce() {
        val state = PlaybackOwnershipState<Any>()
        var loads = 0

        assertTrue(state.loadOnce { loads++ })
        assertFalse(state.loadOnce { loads++ })

        assertEquals(1, loads)
    }

    @Test
    fun staleOutputRemovalDoesNotRemoveReplacement() {
        val state = PlaybackOwnershipState<Any>()
        val oldOutput = Any()
        val newOutput = Any()
        val removed = mutableListOf<Any>()

        assertTrue(state.ensureOutput(oldOutput) { _, _ -> })
        assertTrue(state.ensureOutput(newOutput) { _, _ -> })
        assertFalse(state.removeOutput(oldOutput, removed::add))
        assertTrue(state.removeOutput(newOutput, removed::add))

        assertEquals(listOf(newOutput), removed)
    }

    @Test
    fun existingOutputIsVerifiedWithoutReplacingIt() {
        val state = PlaybackOwnershipState<Any>()
        val output = Any()
        var replacements = 0

        assertTrue(state.ensureOutput(output) { _, _ -> replacements++ })
        assertTrue(state.ensureOutput(output) { _, _ -> replacements++ })

        assertEquals(1, replacements)
    }

    @Test
    fun replacementBecomesTheOnlyCurrentOutput() {
        val state = PlaybackOwnershipState<Any>()
        val oldOutput = Any()
        val replacement = Any()

        state.ensureOutput(oldOutput) { _, _ -> }
        state.ensureOutput(replacement) { _, _ -> }

        assertFalse(state.isCurrentOutput(oldOutput))
        assertTrue(state.isCurrentOutput(replacement))
    }
}
