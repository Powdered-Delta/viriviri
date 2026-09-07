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

  /** Persisted user-adjusted world Y (metres) of the immersive video stage. Null = never adjusted. */
  fun loadWorkbenchStageY(): Float?

  fun saveWorkbenchStageY(y: Float)

  /** Clears the persisted stage Y so the next launch falls back to the authored default. */
  fun clearWorkbenchStageY()
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

  override fun loadWorkbenchStageY(): Float? =
      AppPreferenceCodec.decodeStageY(preferences.getString(KEY_WORKBENCH_STAGE_Y, null))

  override fun saveWorkbenchStageY(y: Float) {
    preferences
        .edit()
        .putString(KEY_WORKBENCH_STAGE_Y, AppPreferenceCodec.clampStageY(y).toString())
        .apply()
  }

  override fun clearWorkbenchStageY() {
    preferences.edit().remove(KEY_WORKBENCH_STAGE_Y).apply()
  }

  private companion object {
    const val PREFERENCES_NAME = "viriviri_app_preferences"
    const val KEY_SEARCH_HISTORY = "search_history"
    const val KEY_PLAYBACK_STAGE_SCALE = "playback_stage_scale"
    const val KEY_WORKBENCH_STAGE_Y = "workbench_stage_y"
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

  /**
   * Decodes a persisted stage world-Y (metres). Null/malformed returns null (caller falls back
   * to the authored default). Values outside the comfortable clamp are clamped on read too, so a
   * stale/bad preference can never sink the stage below the floor.
   */
  fun decodeStageY(encodedY: String?): Float? =
      encodedY?.toFloatOrNull()?.let(::clampStageY)

  /** Stage world-Y clamp: floor-level is unsafe for seated/standing comfort; 3m keeps it reachable. */
  fun clampStageY(y: Float): Float =
      if (y.isFinite()) y.coerceIn(MIN_STAGE_Y_METERS, MAX_STAGE_Y_METERS) else STAGE_DEFAULT_WORLD_Y

  private const val MIN_STAGE_Y_METERS = 0.6f
  private const val MAX_STAGE_Y_METERS = 3.0f
}
