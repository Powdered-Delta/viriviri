package com.viriviri.core.network

import com.viriviri.core.model.VideoDetail
import com.viriviri.core.model.VideoId
import com.viriviri.core.model.VideoSummary

interface BilibiliContentApi {
    suspend fun fetchHomeFeed(): List<VideoSummary>

    suspend fun fetchVideoDetail(id: VideoId): VideoDetail?
}
