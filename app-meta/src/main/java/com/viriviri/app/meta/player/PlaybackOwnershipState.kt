package com.viriviri.app.meta.player

internal class PlaybackOwnershipState<T> {
    private var mediaLoaded = false
    private var currentOutput: T? = null

    fun loadOnce(load: () -> Unit): Boolean {
        if (mediaLoaded) return false

        load()
        mediaLoaded = true
        return true
    }

    /** Returns whether [output] is the current output after this call. */
    fun ensureOutput(output: T, replace: (previous: T?, next: T) -> Unit): Boolean {
        if (currentOutput === output) return true

        replace(currentOutput, output)
        currentOutput = output
        return true
    }

    fun isCurrentOutput(output: T): Boolean = currentOutput === output

    fun removeOutput(output: T, remove: (T) -> Unit): Boolean {
        if (currentOutput !== output) return false

        remove(output)
        currentOutput = null
        return true
    }
}
