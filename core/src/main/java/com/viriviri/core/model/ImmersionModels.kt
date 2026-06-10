package com.viriviri.core.model

enum class ImmersionMode {
    BrowsePanel,
    Immersive,
}

data class ImmersionState(
    val mode: ImmersionMode,
    val selectedVideoId: VideoId? = null,
)
