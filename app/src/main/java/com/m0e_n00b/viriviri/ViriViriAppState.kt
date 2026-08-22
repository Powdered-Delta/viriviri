package com.m0e_n00b.viriviri

import android.content.Context
import android.util.Log
import android.view.Surface
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.MediaSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import com.m0e_n00b.spatialworkbench.core.DanmakuEvent
import com.m0e_n00b.spatialworkbench.core.TransientMessage
import com.m0e_n00b.spatialworkbench.core.TransientMessageEvent
import com.m0e_n00b.spatialworkbench.core.TransientMessageReducer
import com.m0e_n00b.spatialworkbench.core.TransientMessageSeverity
import com.m0e_n00b.spatialworkbench.core.TransientMessageState

enum class ViriViriDestination { RECOMMENDATIONS, VIEWER }

internal fun enqueueErrorMessage(
    state: TransientMessageState,
    id: Long,
    text: String,
): TransientMessageState =
    TransientMessageReducer.reduce(
        state,
        TransientMessageEvent.Enqueue(
            TransientMessage(
                id = "error-$id",
                text = text,
                severity = TransientMessageSeverity.ERROR,
            )
        ),
    )

data class ViriViriUiState(
    val recommendations: List<Recommendation> = emptyList(),
    val selected: Recommendation? = null,
    val destination: ViriViriDestination = ViriViriDestination.RECOMMENDATIONS,
    val playbackQuality: PlaybackQuality = PlaybackQuality.AUTO,
    val playbackDisplayRatio: PlaybackDisplayRatio = PlaybackDisplayRatio.AUTO,
    val playbackCanvasSize: PlaybackCanvasSize = PlaybackCanvasSize.STANDARD,
    val danmakuEvents: List<DanmakuEvent> = emptyList(),
    val danmakuLaneAssignments: Map<String, DanmakuLaneAssignment> = emptyMap(),
    val danmakuRenderMetrics: Map<String, DanmakuRenderMetrics> = emptyMap(),
    val isLoadingDanmaku: Boolean = false,
    val isLoading: Boolean = false,
    val isLoadingNextPage: Boolean = false,
    val canLoadMore: Boolean = true,
    val nextPage: Int = 1,
    val isResolvingPlayback: Boolean = false,
    val error: String? = null,
    val transientMessages: TransientMessageState = TransientMessageState(),
    val searchInput: SearchInputSession = DefaultSearchInputMethods.registry.initialSession(),
    val isShowingSearchResults: Boolean = false,
    val recommendationScrollPosition: ListScrollPosition = ListScrollPosition(),
    val searchScrollPosition: ListScrollPosition = ListScrollPosition(),
    val searchOptions: BilibiliSearchOptions = BilibiliSearchOptions(),
)

data class ListScrollPosition(val firstVisibleItemIndex: Int = 0, val firstVisibleItemScrollOffset: Int = 0)

internal enum class ImmersiveBrowseCommand {
  RETURN_TO_PLAYBACK,
}

internal class SearchRequestTracker {
  private var latestRequestId = 0L

  fun beginRequest(): Long = ++latestRequestId

  fun isCurrent(requestId: Long): Boolean = requestId == latestRequestId
}

class PlayerSession(context: Context) {
  val player = ExoPlayer.Builder(context).build()
  private var surface: Surface? = null

  init {
    player.setVideoScalingMode(IMMERSIVE_VIDEO_SCALING_MODE)
  }

  fun setMediaSource(
      source: MediaSource,
      startPositionMs: Long = 0L,
      playWhenReady: Boolean = true,
  ) {
    player.setMediaSource(source, startPositionMs.coerceAtLeast(0L))
    player.prepare()
    player.playWhenReady = playWhenReady
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

  /** Rebinds the SDK-owned output after an explicit 2D-to-immersive route handoff. */
  fun reattachImmersiveSurface(newSurface: Surface) = attachSurface(newSurface, force = true)

  fun attach2dSurface(newSurface: Surface) = attachSurface(newSurface)

  fun detachSurface(oldSurface: Surface) {
    if (surface === oldSurface) {
      player.clearVideoSurface(oldSurface)
      surface = null
    }
  }

  private fun attachSurface(newSurface: Surface, force: Boolean = false) {
    if (!newSurface.isValid || (!force && surface === newSurface)) return
    if (surface !== newSurface) surface?.let(player::clearVideoSurface)
    surface = newSurface
    player.setVideoSurface(newSurface)
  }
}

class ViriViriAppState(
    context: Context,
    private val provider: BilibiliPlaybackProvider = BilibiliPlaybackProvider(),
    internal val inputMethods: SearchInputMethodRegistry = DefaultSearchInputMethods.registry,
    internal val danmakuMergeConfig: DanmakuMergeConfig = DanmakuMergeConfig(),
) {
  val playerSession = PlayerSession(context)
  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
  private val mutableState = MutableStateFlow(
      ViriViriUiState(isLoading = true, searchInput = inputMethods.initialSession())
  )
  private val mutableImmersiveBrowseCommands = MutableSharedFlow<ImmersiveBrowseCommand>(extraBufferCapacity = 1)
  private var playbackRequestId = 0L
  private var transientMessageId = 0L
  private var playbackResolutionJob: Job? = null
  private var danmakuLoadJob: Job? = null
  private var danmakuRequestId = 0L
  private var listJob: Job? = null
  private var nextPageJob: Job? = null
  private val searchRequestTracker = SearchRequestTracker()
  private val thumbnails = ThumbnailRepository()
  private val mutableThumbnailStates = MutableStateFlow<Map<String, ThumbnailState>>(emptyMap())
  val state: StateFlow<ViriViriUiState> = mutableState.asStateFlow()
  internal val thumbnailStates: StateFlow<Map<String, ThumbnailState>> = mutableThumbnailStates.asStateFlow()
  internal val immersiveBrowseCommands: SharedFlow<ImmersiveBrowseCommand> =
      mutableImmersiveBrowseCommands.asSharedFlow()

  init { refreshRecommendations() }

  fun dispatchTransientMessage(event: TransientMessageEvent) {
    mutableState.value =
        mutableState.value.copy(
            transientMessages = TransientMessageReducer.reduce(mutableState.value.transientMessages, event)
        )
  }

  fun refreshRecommendations() {
    listJob?.cancel()
    nextPageJob?.cancel()
    val requestId = searchRequestTracker.beginRequest()
    listJob =
        scope.launch {
          mutableState.value =
              mutableState.value.copy(
                  recommendations = emptyList(),
                  isLoading = true,
                  isLoadingNextPage = false,
                  canLoadMore = true,
                  nextPage = 1,
                  error = null,
                  isShowingSearchResults = false,
                  recommendationScrollPosition = ListScrollPosition(),
              )
          runCatching { withContext(Dispatchers.IO) { provider.loadRecommendations() } }
              .onSuccess { recommendations ->
                if (searchRequestTracker.isCurrent(requestId)) {
                  val page = mergeRecommendationPage(emptyList(), recommendations)
                  mutableState.value =
                      mutableState.value.copy(
                          recommendations = page.recommendations,
                          isLoading = false,
                          canLoadMore = page.canLoadMore,
                          nextPage = 2,
                      )
                  requestThumbnails(page.recommendations)
                }
              }
              .onFailure { error ->
                if (searchRequestTracker.isCurrent(requestId)) {
                  mutableState.value =
                      mutableState.value.copy(
                          isLoading = false,
                          canLoadMore = false,
                          error = error.message ?: "Unable to load recommendations",
                          transientMessages = enqueueError(error.message ?: "Unable to load recommendations"),
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

  fun submitSearch() = submitSearch(mutableState.value.searchInput.committedText, mutableState.value.searchOptions)

  fun submitSearch(options: BilibiliSearchOptions) =
      submitSearch(mutableState.value.searchInput.committedText, options)

  private fun submitSearch(query: String, options: BilibiliSearchOptions) {
    val normalizedQuery = normalizeSearchQuery(query)
    listJob?.cancel()
    nextPageJob?.cancel()
    val requestId = searchRequestTracker.beginRequest()
    if (normalizedQuery.isBlank()) {
      mutableState.value =
          mutableState.value.copy(
              searchInput = inputMethods.initialSession(),
              isShowingSearchResults = false,
              isLoading = false,
              isLoadingNextPage = false,
              error = null,
          )
      return
    }
    listJob =
        scope.launch {
          mutableState.value =
              mutableState.value.copy(
                  recommendations = emptyList(),
                  isLoading = true,
                  isLoadingNextPage = false,
                  canLoadMore = true,
                  nextPage = 1,
                  error = null,
                  searchInput = inputMethods.replaceCommittedText(mutableState.value.searchInput, normalizedQuery),
                  isShowingSearchResults = true,
                  searchScrollPosition = ListScrollPosition(),
                  searchOptions = options,
              )
          runCatching { withContext(Dispatchers.IO) { provider.searchVideos(normalizedQuery, options = options) } }
              .onSuccess { recommendations ->
                if (searchRequestTracker.isCurrent(requestId)) {
                  val page = mergeRecommendationPage(emptyList(), recommendations)
                  mutableState.value =
                      mutableState.value.copy(
                          recommendations = page.recommendations,
                          isLoading = false,
                          canLoadMore = page.canLoadMore,
                          nextPage = 2,
                      )
                  requestThumbnails(page.recommendations)
                }
              }
              .onFailure { error ->
                if (searchRequestTracker.isCurrent(requestId)) {
                  mutableState.value =
                      mutableState.value.copy(
                          isLoading = false,
                          canLoadMore = false,
                          error = error.message ?: "Unable to search Bilibili",
                          transientMessages = enqueueError(error.message ?: "Unable to search Bilibili"),
                      )
                }
              }
        }
  }

  fun loadNextPage() {
    val current = mutableState.value
    if (current.isLoading || current.isLoadingNextPage || !current.canLoadMore) return
    val requestId = searchRequestTracker.beginRequest()
    val pageToLoad = current.nextPage
    val searchQuery = current.searchInput.committedText.takeIf { current.isShowingSearchResults }
    mutableState.value = current.copy(isLoadingNextPage = true, error = null)
    nextPageJob =
        scope.launch {
          val result =
              runCatching {
                withContext(Dispatchers.IO) {
                  if (searchQuery == null) {
                    provider.loadRecommendations(
                        freshIndex = current.recommendations.size,
                    )
                  } else {
                    provider.searchVideos(searchQuery, page = pageToLoad, options = current.searchOptions)
                  }
                }
              }
          if (!searchRequestTracker.isCurrent(requestId)) return@launch
          result
              .onSuccess { recommendations ->
                val latest = mutableState.value
                val page = mergeRecommendationPage(latest.recommendations, recommendations)
                mutableState.value =
                    latest.copy(
                        recommendations = page.recommendations,
                        isLoadingNextPage = false,
                        canLoadMore = page.canLoadMore,
                        nextPage = pageToLoad + 1,
                    )
                requestThumbnails(
                    page.recommendations.filter { recommendation ->
                      latest.recommendations.none { it.videoId == recommendation.videoId }
                    }
                )
              }
              .onFailure { error ->
                mutableState.value =
                    mutableState.value.copy(
                        isLoadingNextPage = false,
                        error = error.message ?: "Unable to load more videos",
                        transientMessages = enqueueError(error.message ?: "Unable to load more videos"),
                    )
              }
        }
  }

  private fun requestThumbnails(recommendations: List<Recommendation>) {
    recommendations.mapNotNull { normalizedThumbnailUrl(it.coverUrl) }.distinct().forEach { url ->
      if (!thumbnails.markLoading(url)) return@forEach
      publishThumbnailState(url, ThumbnailState.Loading)
      scope.launch {
        val bitmap = withContext(Dispatchers.IO) { thumbnails.download(url) }
        thumbnails.store(url, bitmap)
        if (mutableState.value.recommendations.any { normalizedThumbnailUrl(it.coverUrl) == url }) {
          publishThumbnailState(url)
        }
      }
    }
  }

  private fun publishThumbnailState(url: String, state: ThumbnailState? = null) {
    val next = (mutableThumbnailStates.value + (url to (state ?: thumbnails.state(url) ?: ThumbnailState.Failed)))
    mutableThumbnailStates.value =
        if (next.size <= THUMBNAIL_CACHE_SIZE) {
          next
        } else {
          next.entries.toList().takeLast(THUMBNAIL_CACHE_SIZE).associate { it.toPair() }
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
              isResolvingPlayback = true,
              error = null,
              searchScrollPosition = scrollPosition,
          )
        } else {
          current.copy(
              selected = recommendation,
              destination = ViriViriDestination.VIEWER,
              isResolvingPlayback = true,
              error = null,
              recommendationScrollPosition = scrollPosition,
          )
        }
    startPlaybackResolution(recommendation)
  }

  fun retrySelectedVideo() {
    val current = mutableState.value
    val selected = current.selected ?: return
    if (!canRetryImmersiveMedia(current.destination, selected, current.error, current.isResolvingPlayback)) return
    startPlaybackResolution(selected)
  }

  fun selectPlaybackQuality(quality: PlaybackQuality) {
    val current = mutableState.value
    if (current.playbackQuality == quality) return
    mutableState.value = current.copy(playbackQuality = quality)
    current.selected?.let { selected ->
      startPlaybackResolution(
          recommendation = selected,
          quality = quality,
          startPositionMs = playerSession.player.currentPosition.coerceAtLeast(0L),
          playWhenReady = playerSession.player.playWhenReady,
      )
    }
  }

  fun selectPlaybackDisplayRatio(displayRatio: PlaybackDisplayRatio) {
    val current = mutableState.value
    if (current.playbackDisplayRatio == displayRatio) return
    mutableState.value = current.copy(playbackDisplayRatio = displayRatio)
  }

  fun selectPlaybackCanvasSize(canvasSize: PlaybackCanvasSize) {
    val current = mutableState.value
    if (current.playbackCanvasSize == canvasSize) return
    mutableState.value = current.copy(playbackCanvasSize = canvasSize)
  }

  private fun startPlaybackResolution(
      recommendation: Recommendation,
      quality: PlaybackQuality = mutableState.value.playbackQuality,
      startPositionMs: Long = 0L,
      playWhenReady: Boolean = true,
  ) {
    playbackResolutionJob?.cancel()
    danmakuLoadJob?.cancel()
    val danmakuRequestId = ++danmakuRequestId
    mutableState.value =
        mutableState.value.copy(
            isResolvingPlayback = true,
            isLoadingDanmaku = true,
            danmakuEvents = emptyList(),
            danmakuLaneAssignments = emptyMap(),
            danmakuRenderMetrics = emptyMap(),
            error = null,
        )
    val requestId = ++playbackRequestId
    playbackResolutionJob =
        scope.launch {
          try {
            val source =
                withTimeout(PLAYBACK_RESOLUTION_TIMEOUT_MS) {
                  withContext(Dispatchers.IO) {
                    provider.createMediaSource(recommendation.videoId, quality)
                  }
                }
            if (requestId == playbackRequestId) {
              playerSession.setMediaSource(source, startPositionMs, playWhenReady)
              mutableState.value = mutableState.value.copy(isResolvingPlayback = false)
              loadDanmaku(recommendation, danmakuRequestId)
            }
          } catch (error: kotlinx.coroutines.TimeoutCancellationException) {
            if (requestId == playbackRequestId) {
              val message = playbackResolutionError(error)
              mutableState.value =
                  mutableState.value.copy(
                      isResolvingPlayback = false,
                      error = message,
                      transientMessages = enqueueError(message),
                  )
            }
          } catch (_: CancellationException) {
            // A newer selection or retry owns the current loading/error state.
          } catch (error: Throwable) {
            if (requestId == playbackRequestId) {
              val message = playbackResolutionError(error)
              mutableState.value =
                  mutableState.value.copy(
                      isResolvingPlayback = false,
                      error = message,
                      transientMessages = enqueueError(message),
                  )
            }
          }
        }
  }

  private fun loadDanmaku(recommendation: Recommendation, requestId: Long) {
    danmakuLoadJob =
        scope.launch {
          val prepared =
              runCatching {
                withContext(Dispatchers.IO) { prepareDanmaku(provider.loadDanmaku(recommendation.videoId), danmakuMergeConfig) }
              }
          if (requestId != danmakuRequestId) return@launch
          prepared.onSuccess {
                Log.i("ViriViriDanmaku", "loaded ${it.events.size} events for ${recommendation.videoId}")
              }
              .onFailure { Log.w("ViriViriDanmaku", "unable to load ${recommendation.videoId}", it) }
          mutableState.value =
              mutableState.value.copy(
                  danmakuEvents = prepared.getOrNull()?.events.orEmpty(),
                  danmakuLaneAssignments = prepared.getOrNull()?.laneAssignments.orEmpty(),
                  danmakuRenderMetrics = prepared.getOrNull()?.renderMetrics.orEmpty(),
                  isLoadingDanmaku = false,
              )
        }
  }

  private fun enqueueError(text: String): TransientMessageState =
      enqueueErrorMessage(mutableState.value.transientMessages, ++transientMessageId, text)

  fun returnToRecommendations() {
    mutableState.value = mutableState.value.copy(destination = ViriViriDestination.RECOMMENDATIONS, error = null)
  }

  fun requestImmersiveBrowseReturn() {
    mutableImmersiveBrowseCommands.tryEmit(ImmersiveBrowseCommand.RETURN_TO_PLAYBACK)
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
