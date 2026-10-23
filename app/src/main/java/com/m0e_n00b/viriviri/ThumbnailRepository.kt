package com.m0e_n00b.viriviri

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.LinkedHashMap

// UX: keep a larger sampled thumbnail working set so virtual-list scrollback does not blank older cards.
internal const val THUMBNAIL_CACHE_SIZE = 80
internal const val MAX_THUMBNAIL_WIDTH = 480
internal const val MAX_THUMBNAIL_HEIGHT = 270

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
  // Running requests must survive completed-bitmap LRU eviction.
  private val inFlight = mutableSetOf<String>()

  @Synchronized
  fun state(url: String): ThumbnailState? =
      if (url in inFlight) ThumbnailState.Loading else cache[url]

  @Synchronized
  fun markLoading(url: String, retryFailed: Boolean = false): Boolean {
    if (url in inFlight) return false
    val existing = cache[url]
    if (existing != null && !(retryFailed && existing == ThumbnailState.Failed)) return false
    cache.remove(url)
    inFlight.add(url)
    return true
  }

  @Synchronized
  fun store(url: String, bitmap: Bitmap?) {
    inFlight.remove(url)
    cache[url] = bitmap?.let(ThumbnailState::Ready) ?: ThumbnailState.Failed
  }

  fun download(url: String): Bitmap? = try { downloader(url) } catch (_: Exception) { null }

  private companion object {
    private const val CONNECT_TIMEOUT_MS = 8_000
    private const val READ_TIMEOUT_MS = 12_000
    private const val MAX_DOWNLOAD_BYTES = 8 * 1024 * 1024

    fun downloadThumbnail(url: String): Bitmap? {
      val connection = openThumbnailConnection(url)
      return try {
        if (connection.responseCode !in 200..299) return null
        val bytes = connection.inputStream.use { input ->
          val output = ByteArrayOutputStream()
          val buffer = ByteArray(8192)
          while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            if (output.size() + count > MAX_DOWNLOAD_BYTES) return null
            output.write(buffer, 0, count)
          }
          output.toByteArray()
        }
        // Inspect and decode the same response; avoid downloading every image twice.
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        val options = BitmapFactory.Options().apply {
          inSampleSize = thumbnailDecodeSampleSize(bounds.outWidth, bounds.outHeight)
          inPreferredConfig = Bitmap.Config.RGB_565
        }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
      } catch (_: Exception) {
        null
      } finally {
        connection.disconnect()
      }
    }

    private fun openThumbnailConnection(url: String): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
          connectTimeout = CONNECT_TIMEOUT_MS
          readTimeout = READ_TIMEOUT_MS
          instanceFollowRedirects = true
          setRequestProperty("User-Agent", "ViriViri/0.1")
          setRequestProperty("Referer", "https://www.bilibili.com/")
        }
  }
}

internal fun thumbnailDecodeSampleSize(sourceWidth: Int, sourceHeight: Int): Int {
  if (sourceWidth <= 0 || sourceHeight <= 0) return 1
  var sample = 1
  while (sourceWidth / sample > MAX_THUMBNAIL_WIDTH || sourceHeight / sample > MAX_THUMBNAIL_HEIGHT) {
    sample *= 2
  }
  return sample
}
