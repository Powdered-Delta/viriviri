package com.m0e_n00b.viriviri

import android.content.Context
import android.view.Surface
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.MediaSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
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
    val searchInput: SearchInputSession = DefaultSearchInputMethods.registry.initialSession(),
    val isShowingSearchResults: Boolean = false,
    val recommendationScrollPosition: ListScrollPosition = ListScrollPosition(),
    val searchScrollPosition: ListScrollPosition = ListScrollPosition(),
)

data class ListScrollPosition(val firstVisibleItemIndex: Int = 0, val firstVisibleItemScrollOffset: Int = 0)

internal class SearchRequestTracker {
  private var latestRequestId = 0L

  fun beginRequest(): Long = ++latestRequestId

  fun isCurrent(requestId: Long): Boolean = requestId == latestRequestId
}

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

class ViriViriAppState(
    context: Context,
    private val provider: BilibiliPlaybackProvider = BilibiliPlaybackProvider(),
    internal val inputMethods: SearchInputMethodRegistry = DefaultSearchInputMethods.registry,
) {
  val playerSession = PlayerSession(context)
  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
  private val mutableState = MutableStateFlow(
      ViriViriUiState(isLoading = true, searchInput = inputMethods.initialSession())
  )
  private var playbackRequestId = 0L
  private var searchJob: Job? = null
  private val searchRequestTracker = SearchRequestTracker()
  val state: StateFlow<ViriViriUiState> = mutableState.asStateFlow()

  init { refreshRecommendations() }

  fun refreshRecommendations() {
    searchJob?.cancel()
    val requestId = searchRequestTracker.beginRequest()
    scope.launch {
      mutableState.value = mutableState.value.copy(isLoading = true, error = null, isShowingSearchResults = false)
      runCatching { withContext(Dispatchers.IO) { provider.loadRecommendations() } }
          .onSuccess { recommendations ->
            if (searchRequestTracker.isCurrent(requestId)) {
              mutableState.value = mutableState.value.copy(recommendations = recommendations, isLoading = false)
            }
          }
          .onFailure { error ->
            if (searchRequestTracker.isCurrent(requestId)) {
              mutableState.value = mutableState.value.copy(
                  isLoading = false,
                  error = error.message ?: "Unable to load recommendations",
              )
            }
          }
    }
  }

  fun updateSearchQuery(query: String) {
    val current = mutableState.value
    mutableState.value =
        current.copy(searchInput = inputMethods.replaceCommittedText(current.searchInput, query))
  }

  fun applySearchInputAction(action: SearchInputAction) {
    val current = mutableState.value
    mutableState.value = current.copy(searchInput = inputMethods.reduce(current.searchInput, action))
  }

  fun clearSearchInput() {
    val current = mutableState.value
    mutableState.value = current.copy(searchInput = inputMethods.initialSession())
  }

  fun submitSearch() = submitSearch(mutableState.value.searchInput.committedText)

  private fun submitSearch(query: String) {
    val normalizedQuery = normalizeSearchQuery(query)
    searchJob?.cancel()
    val requestId = searchRequestTracker.beginRequest()
    if (normalizedQuery.isBlank()) {
      mutableState.value = mutableState.value.copy(
          searchInput = inputMethods.initialSession(),
          isShowingSearchResults = false,
          isLoading = false,
          error = null,
      )
      return
    }
    searchJob = scope.launch {
      mutableState.value = mutableState.value.copy(
          isLoading = true,
          error = null,
          searchInput =
              inputMethods.replaceCommittedText(mutableState.value.searchInput, normalizedQuery),
          isShowingSearchResults = true,
          searchScrollPosition = ListScrollPosition(),
      )
      runCatching { withContext(Dispatchers.IO) { provider.searchVideos(normalizedQuery) } }
          .onSuccess { recommendations ->
            if (searchRequestTracker.isCurrent(requestId)) {
              mutableState.value = mutableState.value.copy(recommendations = recommendations, isLoading = false)
            }
          }
          .onFailure { error ->
            if (searchRequestTracker.isCurrent(requestId)) {
              mutableState.value = mutableState.value.copy(
                  isLoading = false,
                  error = error.message ?: "Unable to search Bilibili",
              )
            }
          }
    }
  }

  fun returnToRecommendationsFeed() {
    mutableState.value =
        mutableState.value.copy(searchInput = inputMethods.initialSession(), isShowingSearchResults = false)
    refreshRecommendations()
  }

  fun selectRecommendation(recommendation: Recommendation, scrollPosition: ListScrollPosition) {
    val current = mutableState.value
    mutableState.value =
        if (current.isShowingSearchResults) {
          current.copy(
              selected = recommendation,
              destination = ViriViriDestination.VIEWER,
              error = null,
              searchScrollPosition = scrollPosition,
          )
        } else {
          current.copy(
              selected = recommendation,
              destination = ViriViriDestination.VIEWER,
              error = null,
              recommendationScrollPosition = scrollPosition,
          )
        }
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
      selectRecommendation(
          current.recommendations[(index + direction + current.recommendations.size) % current.recommendations.size],
          if (current.isShowingSearchResults) current.searchScrollPosition else current.recommendationScrollPosition,
      )
    }
  }
}
