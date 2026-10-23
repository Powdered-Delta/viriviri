package com.m0e_n00b.viriviri

import org.json.JSONObject

internal data class VideoPart(val contentId: Long, val number: Int, val title: String, val durationSeconds: Int?)
internal data class VideoCollectionSection(val title: String, val videos: List<Recommendation>)
internal data class VideoContextDetails(
    val parts: List<VideoPart> = emptyList(),
    val collectionTitle: String? = null,
    val sections: List<VideoCollectionSection> = emptyList(),
)
internal data class VideoContextState(
    val videoId: String? = null,
    val details: VideoContextDetails = VideoContextDetails(),
    val related: List<Recommendation> = emptyList(),
    val loadingDetails: Boolean = false,
    val loadingRelated: Boolean = false,
    val detailsError: String? = null,
    val relatedError: String? = null,
)

/** Two parts of one BV are distinct media and danmaku identities. */
internal fun Recommendation.matchesPart(part: VideoPart): Boolean =
    contentId?.let { it == part.contentId } ?: (part.number == 1)

internal fun Recommendation.withPart(part: VideoPart): Recommendation = copy(
    contentId = part.contentId,
    partNumber = part.number,
    partTitle = part.title,
    durationSeconds = part.durationSeconds,
    videoUrl = "https://www.bilibili.com/video/$videoId?p=${part.number}",
)

internal fun parseVideoContext(data: JSONObject): VideoContextDetails {
  val pages = data.optJSONArray("pages")
  val parts = buildList {
    if (pages != null) for (i in 0 until pages.length()) {
      val page = pages.optJSONObject(i) ?: continue
      val cid = page.optLong("cid").takeIf { it > 0 } ?: continue
      add(VideoPart(cid, page.optInt("page", i + 1).coerceAtLeast(1),
          page.optString("part").ifBlank { "P${i + 1}" }, page.optInt("duration").takeIf { it > 0 }))
    }
  }.distinctBy { it.contentId }
  val season = data.optJSONObject("ugc_season")
  val rawSections = season?.optJSONArray("sections")
  val sections = buildList {
    if (rawSections != null) for (i in 0 until rawSections.length()) {
      val section = rawSections.optJSONObject(i) ?: continue
      val episodes = section.optJSONArray("episodes") ?: continue
      val videos = buildList {
        for (j in 0 until episodes.length()) {
          val episode = episodes.optJSONObject(j) ?: continue
          val arc = episode.optJSONObject("arc") ?: episode
          val bvid = episode.optString("bvid").ifBlank { arc.optString("bvid") }
          if (bvid.isBlank()) continue
          val owner = arc.optJSONObject("owner") ?: arc.optJSONObject("author") ?: data.optJSONObject("owner")
          val stat = arc.optJSONObject("stat")
          add(Recommendation(videoId = bvid,
              title = episode.optString("title").ifBlank { arc.optString("title", bvid) },
              authorName = owner?.optString("name").orEmpty(),
              coverUrl = arc.optString("pic").ifBlank { episode.optString("pic") }
                  .ifBlank { if (bvid == data.optString("bvid")) data.optString("pic") else "" }
                  .takeIf { it.isNotBlank() },
              durationSeconds = arc.optInt("duration").takeIf { it > 0 },
              viewCount = stat?.optLong("view", -1L)?.takeIf { it >= 0 },
              danmakuCount = stat?.optLong("danmaku", -1L)?.takeIf { it >= 0 },
              likeCount = stat?.optLong("like", -1L)?.takeIf { it >= 0 },
              favoriteCount = stat?.optLong("favorite", -1L)?.takeIf { it >= 0 },
              publishedAtEpochSeconds = arc.optLong("pubdate").takeIf { it > 0 },
              displayLabel = null, videoUrl = "https://www.bilibili.com/video/$bvid",
              contentId = episode.optLong("cid").takeIf { it > 0 }))
        }
      }.distinctBy { it.videoId }
      if (videos.isNotEmpty()) add(VideoCollectionSection(section.optString("title"), videos))
    }
  }
  return VideoContextDetails(parts, season?.optString("title")?.takeIf { it.isNotBlank() }, sections)
}
