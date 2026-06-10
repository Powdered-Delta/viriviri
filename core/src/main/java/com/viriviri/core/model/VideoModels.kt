package com.viriviri.core.model

@JvmInline
value class VideoId(val value: String)

data class VideoSummary(
    val id: VideoId,
    val title: String,
    val ownerName: String,
    val durationSeconds: Int?,
    val coverUrl: String? = null,
)

data class VideoDetail(
    val summary: VideoSummary,
    val description: String,
    val playbackUrl: String? = null,
)

object SampleVideoCatalog {
    val videos: List<VideoSummary> = listOf(
        VideoSummary(
            id = VideoId("bv-placeholder-1"),
            title = "Hybrid foundation walkthrough",
            ownerName = "viriviri",
            durationSeconds = 368,
        ),
        VideoSummary(
            id = VideoId("bv-placeholder-2"),
            title = "Immersive viewing room concept",
            ownerName = "viriviri",
            durationSeconds = 612,
        ),
        VideoSummary(
            id = VideoId("bv-placeholder-3"),
            title = "Bilibili feed placeholder",
            ownerName = "viriviri",
            durationSeconds = 245,
        ),
    )
}
