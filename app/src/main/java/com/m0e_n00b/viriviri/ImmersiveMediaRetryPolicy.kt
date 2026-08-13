package com.m0e_n00b.viriviri

internal fun canRetryImmersiveMedia(
    destination: ViriViriDestination,
    selected: Recommendation?,
    error: String?,
    isResolvingPlayback: Boolean,
): Boolean =
    destination == ViriViriDestination.VIEWER &&
        selected != null &&
        !error.isNullOrBlank() &&
        !isResolvingPlayback
