package com.viriviri.core.repository

import com.viriviri.core.model.SampleVideoCatalog
import com.viriviri.core.model.VideoDetail
import com.viriviri.core.model.VideoId
import com.viriviri.core.model.VideoSummary

interface VideoRepository {
    suspend fun homeFeed(): List<VideoSummary>

    suspend fun videoDetail(id: VideoId): VideoDetail?
}

class PlaceholderVideoRepository : VideoRepository {
    override suspend fun homeFeed(): List<VideoSummary> = SampleVideoCatalog.videos

    override suspend fun videoDetail(id: VideoId): VideoDetail? {
        val summary = SampleVideoCatalog.videos.firstOrNull { it.id == id } ?: return null
        return VideoDetail(
            summary = summary,
            description = "Placeholder detail for future Bilibili integration.",
        )
    }
}
