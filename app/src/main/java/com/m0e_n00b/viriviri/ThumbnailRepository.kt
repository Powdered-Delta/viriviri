package com.m0e_n00b.viriviri

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.net.HttpURLConnection
import java.net.URL
import java.util.LinkedHashMap

internal const val THUMBNAIL_CACHE_SIZE = 80

internal sealed interface ThumbnailState {
  data object Loading : ThumbnailState
  data class Ready(val bitmap: Bitmap) : ThumbnailState
  data object Failed : ThumbnailState
}

internal fun normalizedThumbnailUrl(coverUrl: String?): String? {
  val value = coverUrl?.trim().orEmpty()
  if (value.isBlank()) return null
  val normalized = if (value.startsWith("//")) "https:$value" else value
  return normalized.replaceFirst("http://", "https://").takeIf {
    it.startsWith("https://") || it.startsWith("http://")
  }
}

internal class ThumbnailRepository(
    private val maxEntries: Int = THUMBNAIL_CACHE_SIZE,
    private val downloader: (String) -> Bitmap? = ::downloadThumbnail,
) {
  private val cache =
      object : LinkedHashMap<String, ThumbnailState>(maxEntries, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, ThumbnailState>?): Boolean =
            size > maxEntries
      }

  @Synchronized
  fun state(url: String): ThumbnailState? = cache[url]

  @Synchronized
  fun markLoading(url: String): Boolean {
    if (url in cache) return false
    cache[url] = ThumbnailState.Loading
    return true
  }

  @Synchronized
  fun store(url: String, bitmap: Bitmap?) {
    cache[url] = bitmap?.let(ThumbnailState::Ready) ?: ThumbnailState.Failed
  }

  fun download(url: String): Bitmap? = downloader(url)

  private companion object {
    private const val CONNECT_TIMEOUT_MS = 8_000
    private const val READ_TIMEOUT_MS = 12_000

    fun downloadThumbnail(url: String): Bitmap? {
      val connection = URL(url).openConnection() as HttpURLConnection
      return try {
        connection.connectTimeout = CONNECT_TIMEOUT_MS
        connection.readTimeout = READ_TIMEOUT_MS
        connection.instanceFollowRedirects = true
        connection.setRequestProperty("User-Agent", "ViriViri/0.1")
        if (connection.responseCode !in 200..299) return null
        connection.inputStream.use(BitmapFactory::decodeStream)
      } catch (_: Exception) {
        null
      } finally {
        connection.disconnect()
      }
    }
  }
}
