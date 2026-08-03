package com.m0e_n00b.viriviri

import android.content.Context
import android.view.Surface
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.MediaSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class ViriViriDestination { RECOMMENDATIONS, VIEWER }

data class ViriViriUiState(
    val recommendations: List<Recommendation> = emptyList(),
    val selected: Recommendation? = null,
    val destination: ViriViriDestination = ViriViriDestination.RECOMMENDATIONS,
    val isLoading: Boolean = false,
    val error: String? = null,
)

class PlayerSession(context: Context) {
  val player = ExoPlayer.Builder(context).build()
  private var surface: Surface? = null

  fun setMediaSource(source: MediaSource) {
    player.setMediaSource(source)
    player.prepare()
    player.playWhenReady = true
  }

  fun setMediaItem(item: MediaItem) {
    player.setMediaItem(item)
    player.prepare()
  }

  fun beginOutputHandoff() {
    surface?.let(player::clearVideoSurface)
    surface = null
  }

  fun attachImmersiveSurface(newSurface: Surface) = attachSurface(newSurface)

  fun attach2dSurface(newSurface: Surface) = attachSurface(newSurface)

  fun detachSurface(oldSurface: Surface) {
    if (surface === oldSurface) {
      player.clearVideoSurface(oldSurface)
      surface = null
    }
  }

  private fun attachSurface(newSurface: Surface) {
    if (!newSurface.isValid || surface === newSurface) return
    surface?.let(player::clearVideoSurface)
    surface = newSurface
    player.setVideoSurface(newSurface)
  }
}

class ViriViriAppState(context: Context, private val provider: BilibiliPlaybackProvider = BilibiliPlaybackProvider()) {
  val playerSession = PlayerSession(context)
  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
  private val mutableState = MutableStateFlow(ViriViriUiState(isLoading = true))
  private var playbackRequestId = 0L
  val state: StateFlow<ViriViriUiState> = mutableState.asStateFlow()

  init { refreshRecommendations() }

  fun refreshRecommendations() {
    scope.launch {
      mutableState.value = mutableState.value.copy(isLoading = true, error = null)
      runCatching { withContext(Dispatchers.IO) { provider.loadRecommendations() } }
          .onSuccess { recommendations ->
            mutableState.value = mutableState.value.copy(recommendations = recommendations, isLoading = false)
          }
          .onFailure { error ->
            mutableState.value = mutableState.value.copy(isLoading = false, error = error.message ?: "Unable to load recommendations")
          }
    }
  }

  fun selectRecommendation(recommendation: Recommendation) {
    mutableState.value = mutableState.value.copy(selected = recommendation, destination = ViriViriDestination.VIEWER, error = null)
    val requestId = ++playbackRequestId
    scope.launch {
      runCatching { withContext(Dispatchers.IO) { provider.createMediaSource(recommendation.videoId) } }
          .onSuccess { source -> if (requestId == playbackRequestId) playerSession.setMediaSource(source) }
          .onFailure { error ->
            if (requestId == playbackRequestId) {
              mutableState.value = mutableState.value.copy(error = error.message ?: "Unable to play this video")
            }
          }
    }
  }

  fun returnToRecommendations() {
    mutableState.value = mutableState.value.copy(destination = ViriViriDestination.RECOMMENDATIONS, error = null)
  }

  fun selectAdjacentRecommendation(direction: Int) {
    val current = mutableState.value
    val index = current.recommendations.indexOfFirst { it.videoId == current.selected?.videoId }
    if (index >= 0 && current.recommendations.isNotEmpty()) {
      selectRecommendation(current.recommendations[(index + direction + current.recommendations.size) % current.recommendations.size])
    }
  }
}
