package com.m0e_n00b.viriviri

import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.MergingMediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import java.net.HttpURLConnection
import java.net.URLEncoder
import java.net.URL
import java.security.MessageDigest
import org.json.JSONArray
import org.json.JSONObject
import com.m0e_n00b.spatialworkbench.core.ContentAccess

data class Recommendation(
    val videoId: String,
    val title: String,
    val authorName: String,
    val coverUrl: String?,
    val durationSeconds: Int?,
    val viewCount: Long?,
    val displayLabel: String?,
    val videoUrl: String,
    val access: ContentAccess = ContentAccess.STANDARD,
)

internal fun recommendationAccess(
    isChargeableSeason: Boolean,
): ContentAccess =
    if (isChargeableSeason) ContentAccess.CHARGING_EXCLUSIVE else ContentAccess.STANDARD

internal data class BilibiliRecommendationVideo(
    val bvid: String,
    val title: String,
    val authorName: String,
    val coverUrl: String,
    val durationSeconds: Int,
    val viewCount: Long,
    val displayLabel: String?,
    val isChargeableSeason: Boolean = false,
)

internal data class BilibiliSearchVideo(
    val bvid: String,
    val title: String,
    val author: String,
    val pic: String,
    val duration: String,
    val play: String,
    val pubdate: String?,
    val isChargeableSeason: Boolean = false,
)

class PlaybackProviderException(message: String, cause: Throwable? = null) : Exception(message, cause)

class BilibiliPlaybackProvider(
    private val apiBaseUrl: String = "https://api.bilibili.com",
    private val clockSeconds: () -> Long = { System.currentTimeMillis() / 1000L },
) {
  companion object {
    internal const val RECOMMENDATION_ENDPOINT_PATH = "/x/web-interface/wbi/index/top/feed/rcmd"
    internal const val VIDEO_SEARCH_ENDPOINT_PATH = "/x/web-interface/wbi/search/type"
    private const val NETWORK_TIMEOUT_MS = 15_000
    private const val USER_AGENT =
        "Mozilla/5.0 AppleWebKit/537.36 Chrome/124.0.0.0 Safari/537.36"

    internal fun recommendationEndpointPath(): String = RECOMMENDATION_ENDPOINT_PATH
    internal fun videoSearchEndpointPath(): String = VIDEO_SEARCH_ENDPOINT_PATH

    internal fun recommendationPageUrl(apiBaseUrl: String, freshIndex: Int, pageSize: Int): String =
        "$apiBaseUrl$RECOMMENDATION_ENDPOINT_PATH" +
            "?version=1&feed_version=V8&homepage_ver=1&ps=$pageSize" +
            "&fresh_idx=$freshIndex&brush=$freshIndex&fresh_type=4"

    internal fun mapRecommendationItem(item: JSONObject): Recommendation? =
        mapRecommendationVideo(
            BilibiliRecommendationVideo(
                bvid = item.optString("bvid"),
                title = item.optString("title", "Untitled video"),
                authorName = item.optJSONObject("owner")?.optString("name", "Unknown author") ?: "Unknown author",
                coverUrl = item.optString("pic"),
                durationSeconds = item.optInt("duration"),
                viewCount = item.optJSONObject("stat")?.optLong("view") ?: -1L,
                displayLabel = item.optJSONObject("rcmd_reason")?.optString("content"),
                isChargeableSeason = item.optBoolean("is_chargeable_season"),
            )
        )

    internal fun mapRecommendationVideo(video: BilibiliRecommendationVideo): Recommendation? {
      val bvid = video.bvid.trim().takeIf { it.isNotBlank() } ?: return null
      return Recommendation(
          videoId = bvid,
          title = video.title,
          authorName = video.authorName,
          coverUrl = video.coverUrl.takeIf { it.isNotBlank() },
          durationSeconds = video.durationSeconds.takeIf { it > 0 },
          viewCount = video.viewCount.takeIf { it >= 0 },
          displayLabel = video.displayLabel?.takeIf { it.isNotBlank() },
          videoUrl = "https://www.bilibili.com/video/$bvid",
          access = recommendationAccess(video.isChargeableSeason),
      )
    }

    internal fun mapVideoSearchResults(response: JSONObject): List<Recommendation> {
      val results = response.optJSONObject("data")?.optJSONArray("result") ?: JSONArray()
      return mapVideoSearchResults(
          buildList {
        for (index in 0 until results.length()) {
          val result = results.optJSONObject(index) ?: continue
          add(
              BilibiliSearchVideo(
                  bvid = result.optString("bvid"),
                  title = result.optString("title"),
                  author = result.optString("author"),
                  pic = result.optString("pic"),
                  duration = result.optString("duration"),
                  play = result.optString("play"),
                  pubdate = result.opt("pubdate")?.toString(),
                  isChargeableSeason = result.optBoolean("is_chargeable_season"),
              )
          )
        }
      }
      )
    }

    internal fun mapVideoSearchResults(results: List<BilibiliSearchVideo>): List<Recommendation> =
        results.mapNotNull { result ->
          val bvid = result.bvid.trim().takeIf { it.isNotBlank() } ?: return@mapNotNull null
          Recommendation(
              videoId = bvid,
              title = sanitizeSearchTitle(result.title).ifBlank { "Untitled video" },
              authorName = result.author.ifBlank { "Unknown author" },
              coverUrl = result.pic.takeIf { it.isNotBlank() },
              durationSeconds = parseSearchDuration(result.duration),
              viewCount = parseSearchCount(result.play),
              displayLabel = result.pubdate?.takeIf { it.isNotBlank() },
              videoUrl = "https://www.bilibili.com/video/$bvid",
              access = recommendationAccess(result.isChargeableSeason),
          )
        }

    internal fun sanitizeSearchTitle(title: String): String =
        title
            .replace(Regex("<[^>]+>"), "")
            .replace("&nbsp;", " ")
            .replace("&amp;", "&")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .trim()

    private fun parseSearchDuration(value: String): Int? {
      val parts = value.split(':').map { it.toIntOrNull() ?: return null }
      if (parts.isEmpty() || parts.size > 3) return null
      return parts.fold(0) { seconds, part -> seconds * 60 + part }.takeIf { it > 0 }
    }

    private fun parseSearchCount(value: String): Long? {
      val normalized = value.replace(",", "").trim()
      return when {
        normalized.endsWith("万") -> normalized.removeSuffix("万").toDoubleOrNull()?.times(10_000)?.toLong()
        normalized.endsWith("亿") -> normalized.removeSuffix("亿").toDoubleOrNull()?.times(100_000_000)?.toLong()
        else -> normalized.toLongOrNull()
      }
    }

  }

  fun loadRecommendations(freshIndex: Int = 0, pageSize: Int = RECOMMENDATION_PAGE_SIZE): List<Recommendation> {
    require(freshIndex >= 0) { "freshIndex must not be negative" }
    require(pageSize > 0) { "pageSize must be positive" }
    val response = getJson(recommendationPageUrl(apiBaseUrl, freshIndex, pageSize))
    requireSuccess(response)
    val items = response.optJSONObject("data")?.optJSONArray("item") ?: JSONArray()
    return buildList {
      for (index in 0 until items.length()) {
        mapRecommendationItem(items.optJSONObject(index) ?: continue)?.let(::add)
      }
    }.ifEmpty { throw PlaybackProviderException("Bilibili returned no recommendations") }
  }

  fun searchVideos(
      query: String,
      page: Int = 1,
      pageSize: Int = RECOMMENDATION_PAGE_SIZE,
  ): List<Recommendation> {
    require(page > 0) { "page must be positive" }
    require(pageSize > 0) { "pageSize must be positive" }
    val keyword = normalizeSearchQuery(query)
    if (keyword.isBlank()) return emptyList()
    val nav = getJson("$apiBaseUrl/x/web-interface/nav")
    // Anonymous nav responses use code -101 but still expose the public WBI key material.
    val wbi = nav.optJSONObject("data")?.optJSONObject("wbi_img")
        ?: throw PlaybackProviderException("Bilibili did not provide WBI signing data")
    val mixinKey = BilibiliWbi.mixinKey(wbi.optString("img_url"), wbi.optString("sub_url"))
        ?: throw PlaybackProviderException("Bilibili returned invalid WBI signing data")
    val signedQuery = BilibiliWbi.sign(
        mapOf(
            "search_type" to "video",
            "keyword" to keyword,
            "page" to page.toString(),
            "page_size" to pageSize.toString(),
            "platform" to "pc",
            "web_location" to "1430654",
        ),
        mixinKey,
        clockSeconds(),
    )
    val response = getJson("$apiBaseUrl$VIDEO_SEARCH_ENDPOINT_PATH?$signedQuery")
    requireSuccess(response)
    return mapVideoSearchResults(response)
  }

  fun createMediaSource(videoId: String): MediaSource {
    val detail = getJson("$apiBaseUrl/x/web-interface/view?bvid=${encode(videoId)}")
    requireSuccess(detail)
    val cid = detail.optJSONObject("data")?.optLong("cid") ?: 0L
    if (cid <= 0L) throw PlaybackProviderException("Bilibili did not provide a playable video part")
    val nav = getJson("$apiBaseUrl/x/web-interface/nav")
    // Anonymous nav responses use code -101 but still expose the public WBI key material.
    val wbi = nav.optJSONObject("data")?.optJSONObject("wbi_img")
        ?: throw PlaybackProviderException("Bilibili did not provide WBI signing data")
    val mixinKey = BilibiliWbi.mixinKey(wbi.optString("img_url"), wbi.optString("sub_url"))
        ?: throw PlaybackProviderException("Bilibili returned invalid WBI signing data")
    val query = BilibiliWbi.sign(
        mapOf("bvid" to videoId, "cid" to cid.toString(), "fnval" to "16", "fourk" to "1", "qn" to "80"),
        mixinKey,
        clockSeconds(),
    )
    val playUrl =
        getJson(
            "$apiBaseUrl/x/player/wbi/playurl?$query",
            mapOf(
                "Origin" to "https://www.bilibili.com",
                "Referer" to "https://www.bilibili.com/video/$videoId",
            ),
        )
    requireSuccess(playUrl)
    val dash = playUrl.optJSONObject("data")?.optJSONObject("dash")
        ?: throw PlaybackProviderException("Bilibili did not provide DASH streams")
    val video = selectAvcVideo(dash.optJSONArray("video"))
        ?: throw PlaybackProviderException("No compatible AVC DASH video stream is available")
    val audio = selectAudio(dash.optJSONArray("audio"))
        ?: throw PlaybackProviderException("No DASH audio stream is available")
    val factory =
        DefaultHttpDataSource.Factory().setDefaultRequestProperties(
            mapOf(
                "Origin" to "https://www.bilibili.com",
                "Referer" to "https://www.bilibili.com/video/$videoId",
                "User-Agent" to USER_AGENT,
            )
        )
    return MergingMediaSource(
        ProgressiveMediaSource.Factory(factory).createMediaSource(dashItem(video, MimeTypes.VIDEO_MP4)),
        ProgressiveMediaSource.Factory(factory).createMediaSource(dashItem(audio, MimeTypes.AUDIO_MP4)),
    )
  }

  private fun dashItem(stream: JSONObject, mimeType: String): MediaItem {
    val url = stream.optString("base_url").ifBlank { stream.optString("baseUrl") }
    if (url.isBlank()) throw PlaybackProviderException("Bilibili returned a DASH stream without a URL")
    return MediaItem.Builder().setUri(Uri.parse(url)).setMimeType(mimeType).build()
  }

  private fun selectAvcVideo(streams: JSONArray?): JSONObject? = streams?.let { list ->
    (0 until list.length()).mapNotNull(list::optJSONObject).firstOrNull {
      it.optString("codecs").contains("avc", ignoreCase = true)
    }
  }

  private fun selectAudio(streams: JSONArray?): JSONObject? = streams?.let { list ->
    (0 until list.length()).mapNotNull(list::optJSONObject).firstOrNull {
      it.optString("codecs").contains("mp4a", ignoreCase = true)
    }
  }

  private fun getJson(url: String, headers: Map<String, String> = emptyMap()): JSONObject {
    val connection = URL(url).openConnection() as HttpURLConnection
    try {
      connection.connectTimeout = NETWORK_TIMEOUT_MS
      connection.readTimeout = NETWORK_TIMEOUT_MS
      connection.requestMethod = "GET"
      connection.setRequestProperty("User-Agent", USER_AGENT)
      headers.forEach(connection::setRequestProperty)
      val responseCode = connection.responseCode
      if (responseCode !in 200..299) {
        val responseBody = connection.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
        throw PlaybackProviderException("Bilibili request failed with HTTP $responseCode: ${responseBody.take(160)}")
      }
      return connection.inputStream.bufferedReader().use { JSONObject(it.readText()) }
    } catch (error: Exception) {
      if (error is PlaybackProviderException) throw error
      throw PlaybackProviderException("Unable to contact Bilibili", error)
    } finally {
      connection.disconnect()
    }
  }

  private fun requireSuccess(response: JSONObject) {
    if (response.optInt("code") != 0) throw PlaybackProviderException(response.optString("message", "Bilibili request failed"))
  }

  private fun encode(value: String): String = URLEncoder.encode(value, Charsets.UTF_8.name())

}

object BilibiliWbi {
  private val permutation = intArrayOf(46, 47, 18, 2, 53, 8, 23, 32, 15, 50, 10, 31, 58, 3, 45, 35, 27, 43, 5, 49, 33, 9, 42, 19, 29, 28, 14, 39, 12, 38, 41, 13, 37, 48, 7, 16, 24, 55, 40, 61, 26, 17, 0, 1, 60, 51, 30, 4, 22, 25, 54, 21, 56, 59, 6, 63, 57, 62, 11, 36, 20, 34, 44, 52)
  private val disallowed = Regex("[!'()*]")

  fun mixinKey(imageUrl: String, subUrl: String): String? {
    val image = imageUrl.substringAfterLast('/').substringBefore('.').takeIf { it.isNotBlank() } ?: return null
    val sub = subUrl.substringAfterLast('/').substringBefore('.').takeIf { it.isNotBlank() } ?: return null
    val source = image + sub
    if (source.length <= permutation.max()) return null
    return permutation.joinToString("") { source[it].toString() }.take(32)
  }

  fun sign(parameters: Map<String, String>, mixinKey: String, timestamp: Long): String {
    val query = (parameters + ("wts" to timestamp.toString())).toSortedMap().entries.joinToString("&") {
      "${encode(it.key)}=${encode(disallowed.replace(it.value, ""))}"
    }
    return "$query&w_rid=${md5(query + mixinKey)}"
  }

  // WBI signs RFC 3986 query encoding. Java form encoding writes spaces as '+',
  // which the signed search endpoint does not treat as a keyword separator.
  private fun encode(value: String): String = URLEncoder.encode(value, Charsets.UTF_8.name()).replace("+", "%20")
  private fun md5(value: String): String = MessageDigest.getInstance("MD5").digest(value.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
}

internal fun normalizeSearchQuery(query: String): String = query.trim().replace(Regex("\\s+"), " ")
