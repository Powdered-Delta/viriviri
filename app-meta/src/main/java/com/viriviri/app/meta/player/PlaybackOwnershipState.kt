package com.viriviri.app.meta.player

import com.viriviri.core.state.HandoffTarget

internal data class PlaybackOutput<T>(val value: T, val target: HandoffTarget?)

internal class PlaybackOwnershipState<T> {

    private var mediaLoaded = false
    private var currentOutput: PlaybackOutput<T>? = null

    fun loadOnce(load: () -> Unit): Boolean {
        if (mediaLoaded) return false

        load()
        mediaLoaded = true
        return true
    }

    /** Returns whether [output] is the current output after this call. */
    fun ensureOutput(
        output: T,
        target: HandoffTarget? = null,
        replace: (previous: PlaybackOutput<T>?, next: PlaybackOutput<T>) -> Unit,
    ): Boolean {
        if (currentOutput?.value === output) return true

        val next = PlaybackOutput(output, target)
        replace(currentOutput, next)
        currentOutput = next
        return true
    }

    fun isCurrentOutput(output: T): Boolean = currentOutput?.value === output

    fun removeOutput(output: T, remove: (T) -> Unit): Boolean {
        if (currentOutput?.value !== output) return false

        remove(output)
        currentOutput = null
        return true
    }

    fun clearOutput() {
        currentOutput = null
    }
}
