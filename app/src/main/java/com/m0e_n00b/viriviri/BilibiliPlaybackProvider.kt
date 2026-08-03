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

data class Recommendation(
    val videoId: String,
    val title: String,
    val authorName: String,
    val coverUrl: String?,
    val durationSeconds: Int?,
    val viewCount: Long?,
    val displayLabel: String?,
    val videoUrl: String,
)

class PlaybackProviderException(message: String, cause: Throwable? = null) : Exception(message, cause)

class BilibiliPlaybackProvider(
    private val apiBaseUrl: String = "https://api.bilibili.com",
    private val clockSeconds: () -> Long = { System.currentTimeMillis() / 1000L },
) {
  companion object {
    internal const val RECOMMENDATION_ENDPOINT_PATH = "/x/web-interface/wbi/index/top/feed/rcmd"
    private const val NETWORK_TIMEOUT_MS = 15_000
    private const val USER_AGENT =
        "Mozilla/5.0 (Android 14; Quest 2) AppleWebKit/537.36 Chrome/124.0 Mobile Safari/537.36"

    internal fun recommendationEndpointPath(): String = RECOMMENDATION_ENDPOINT_PATH
  }

  fun loadRecommendations(): List<Recommendation> {
    val response =
        getJson(
            "$apiBaseUrl$RECOMMENDATION_ENDPOINT_PATH" +
                "?version=1&feed_version=V8&homepage_ver=1&ps=20&fresh_idx=0&brush=0&fresh_type=4"
        )
    requireSuccess(response)
    val items = response.optJSONObject("data")?.optJSONArray("item") ?: JSONArray()
    return buildList {
      for (index in 0 until items.length()) {
        val item = items.optJSONObject(index) ?: continue
        val bvid = item.optString("bvid")
        if (bvid.isBlank()) continue
        add(
            Recommendation(
                videoId = bvid,
                title = item.optString("title", "Untitled video"),
                authorName = item.optJSONObject("owner")?.optString("name", "Unknown author") ?: "Unknown author",
                coverUrl = item.optString("pic").takeIf { it.isNotBlank() },
                durationSeconds = item.optInt("duration").takeIf { it > 0 },
                viewCount = item.optJSONObject("stat")?.optLong("view")?.takeIf { it >= 0 },
                displayLabel =
                    item.optJSONObject("rcmd_reason")?.optString("content")?.takeIf { it.isNotBlank() },
                videoUrl = "https://www.bilibili.com/video/$bvid",
            )
        )
      }
    }.ifEmpty { throw PlaybackProviderException("Bilibili returned no recommendations") }
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
    val playUrl = getJson("$apiBaseUrl/x/player/wbi/playurl?$query")
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

  private fun getJson(url: String): JSONObject {
    val connection = URL(url).openConnection() as HttpURLConnection
    try {
      connection.connectTimeout = NETWORK_TIMEOUT_MS
      connection.readTimeout = NETWORK_TIMEOUT_MS
      connection.requestMethod = "GET"
      connection.setRequestProperty("User-Agent", USER_AGENT)
      return connection.inputStream.bufferedReader().use { JSONObject(it.readText()) }
    } catch (error: Exception) {
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

  private fun encode(value: String): String = URLEncoder.encode(value, Charsets.UTF_8.name())
  private fun md5(value: String): String = MessageDigest.getInstance("MD5").digest(value.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
}
