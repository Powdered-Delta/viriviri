package com.viriviri.app.meta.player

internal object PlaybackLifecycleOwnership {
    fun shouldReleasePlayer(
        isChangingConfigurations: Boolean,
        isHandoffProtected: Boolean,
    ): Boolean = !isChangingConfigurations && !isHandoffProtected
}
