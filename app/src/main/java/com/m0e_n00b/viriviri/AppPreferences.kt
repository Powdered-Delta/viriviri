package com.m0e_n00b.viriviri

import android.content.Context
import android.content.SharedPreferences
import java.nio.charset.StandardCharsets
import java.util.Base64

/** Non-sensitive application preferences. Authentication credentials belong in a separate secure store. */
interface AppPreferences {
  fun loadSearchHistory(): List<String>

  fun saveSearchHistory(history: List<String>)

  fun loadPlaybackStageScale(): Float

  fun savePlaybackStageScale(scale: Float)
}

internal class SharedPreferencesAppPreferences(context: Context) : AppPreferences {
  private val preferences: SharedPreferences =
      context.applicationContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

  override fun loadSearchHistory(): List<String> =
      AppPreferenceCodec.decodeHistory(preferences.getString(KEY_SEARCH_HISTORY, null))

  override fun saveSearchHistory(history: List<String>) {
    preferences.edit().putString(KEY_SEARCH_HISTORY, AppPreferenceCodec.encodeHistory(history)).apply()
  }

  override fun loadPlaybackStageScale(): Float =
      AppPreferenceCodec.decodeStageScale(preferences.getString(KEY_PLAYBACK_STAGE_SCALE, null))

  override fun savePlaybackStageScale(scale: Float) {
    preferences.edit().putString(KEY_PLAYBACK_STAGE_SCALE, PlaybackCanvasSize.clampStageScale(scale).toString()).apply()
  }

  private companion object {
    const val PREFERENCES_NAME = "viriviri_app_preferences"
    const val KEY_SEARCH_HISTORY = "search_history"
    const val KEY_PLAYBACK_STAGE_SCALE = "playback_stage_scale"
  }
}

internal object AppPreferenceCodec {
  private val encoder = Base64.getUrlEncoder().withoutPadding()
  private val decoder = Base64.getUrlDecoder()

  fun encodeHistory(history: List<String>): String =
      history
          .asSequence()
          .map(String::trim)
          .filter(String::isNotBlank)
          .distinct()
          .joinToString(",") { entry ->
            encoder.encodeToString(entry.toByteArray(StandardCharsets.UTF_8))
          }

  fun decodeHistory(encodedHistory: String?): List<String> =
      encodedHistory
          ?.takeIf(String::isNotBlank)
          ?.split(',')
          ?.mapNotNull { entry ->
            runCatching { String(decoder.decode(entry), StandardCharsets.UTF_8).trim() }.getOrNull()
          }
          ?.filter(String::isNotBlank)
          ?.distinct()
          .orEmpty()

  fun decodeStageScale(encodedScale: String?): Float =
      encodedScale?.toFloatOrNull()?.let(PlaybackCanvasSize::clampStageScale) ?: PlaybackCanvasSize.STANDARD.scale
}
