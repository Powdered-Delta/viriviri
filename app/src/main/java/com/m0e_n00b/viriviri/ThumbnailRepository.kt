package com.m0e_n00b.viriviri

import android.graphics.Bitmap
import android.graphics.BitmapFactory
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
      val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
      if (!readThumbnailBounds(url, bounds)) return null
      val options =
          BitmapFactory.Options().apply {
            inSampleSize = thumbnailDecodeSampleSize(bounds.outWidth, bounds.outHeight)
            // UX: cover art has no alpha requirement; RGB_565 halves retained bitmap memory.
            inPreferredConfig = Bitmap.Config.RGB_565
          }
      return decodeThumbnailBitmap(url, options)
    }

    private fun readThumbnailBounds(url: String, options: BitmapFactory.Options): Boolean {
      val connection = openThumbnailConnection(url)
      return try {
        if (connection.responseCode !in 200..299) return false
        connection.inputStream.use { input ->
          BitmapFactory.decodeStream(input, null, options)
        }
        options.outWidth > 0 && options.outHeight > 0
      } catch (_: Exception) {
        false
      } finally {
        connection.disconnect()
      }
    }

    private fun decodeThumbnailBitmap(url: String, options: BitmapFactory.Options): Bitmap? {
      val connection = openThumbnailConnection(url)
      return try {
        if (connection.responseCode !in 200..299) return null
        connection.inputStream.use { input -> BitmapFactory.decodeStream(input, null, options) }
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
