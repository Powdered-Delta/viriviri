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
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import com.m0e_n00b.spatialworkbench.core.DanmakuEvent
import com.m0e_n00b.spatialworkbench.core.TransientMessage
import com.m0e_n00b.spatialworkbench.core.TransientMessageEvent
import com.m0e_n00b.spatialworkbench.core.TransientMessageReducer
import com.m0e_n00b.spatialworkbench.core.TransientMessageSeverity
import com.m0e_n00b.spatialworkbench.core.TransientMessageState

private val DEFAULT_SEARCH_SUGGESTIONS =
    listOf("动画", "番剧", "音乐", "游戏", "科技", "美食", "影视", "知识")
private const val MAX_SEARCH_HISTORY = 12

/** Authored default world Y (metres) of the immersive video stage, used when the user never adjusted it. */
const val STAGE_DEFAULT_WORLD_Y: Float = 2.14f

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

enum class SearchWorkspaceRoute {
  WORKBENCH_EMPTY,
  RECOMMENDATIONS,
  SEARCH_EMPTY,
  SEARCH_RESULTS,
}

enum class SearchTextInputTarget {
  INTERNAL,
  SYSTEM,
}

enum class WorkbenchReturnTarget {
  VIDEO_LIST,
  SEARCH_RESULTS,
}

internal fun playbackReturnRoute(target: WorkbenchReturnTarget): SearchWorkspaceRoute =
    when (target) {
      WorkbenchReturnTarget.VIDEO_LIST -> SearchWorkspaceRoute.RECOMMENDATIONS
      WorkbenchReturnTarget.SEARCH_RESULTS -> SearchWorkspaceRoute.SEARCH_RESULTS
    }

data class SearchWorkspaceState(
    val route: SearchWorkspaceRoute = SearchWorkspaceRoute.RECOMMENDATIONS,
    val workbenchReturnTarget: WorkbenchReturnTarget = WorkbenchReturnTarget.VIDEO_LIST,
    val input: SearchInputSession = DefaultSearchInputMethods.registry.initialSession(),
    val textInputTarget: SearchTextInputTarget = SearchTextInputTarget.INTERNAL,
    val history: List<String> = emptyList(),
    val suggestedQueries: List<String> = DEFAULT_SEARCH_SUGGESTIONS,
    val isHistoryExpanded: Boolean = false,
    val isKeyboardVisible: Boolean = false,
    val isKeyboardDismissed: Boolean = false,
    val isCandidatesExpanded: Boolean = false,
    val scrollPosition: ListScrollPosition = ListScrollPosition(),
    val options: BilibiliSearchOptions = BilibiliSearchOptions(),
)

data class ViriViriUiState(
    val recommendations: List<Recommendation> = emptyList(),
    val selected: Recommendation? = null,
    val destination: ViriViriDestination = ViriViriDestination.RECOMMENDATIONS,
    val playbackQuality: PlaybackQuality = PlaybackQuality.AUTO,
    val playbackDisplayRatio: PlaybackDisplayRatio = PlaybackDisplayRatio.AUTO,
    val playbackCanvasSize: PlaybackCanvasSize = PlaybackCanvasSize.STANDARD,
    val playbackStageScale: Float = PlaybackCanvasSize.STANDARD.scale,
    /**
     * Curvature of the WHOLE MediaStage — video, danmaku, backdrop, and every layer added later.
     *
     * Deliberately a single value. Per-layer curvature was implemented, wired to a debug layer
     * selector, and refuted on device: a backdrop on a different radius cannot dim a stage it does
     * not follow, and a danmaku plane on a different radius slices through the picture. It also made
     * every layer a separate owner of the same gesture, so one thumbstick sweep moved the layers
     * independently. The layers are separated by their AUTHORED local Z, not by their radius.
     */
    val playbackCurvature: PlaybackStageCurvature = PlaybackStageCurvature.Flat,
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
    val searchWorkspace: SearchWorkspaceState = SearchWorkspaceState(),
    val recommendationScrollPosition: ListScrollPosition = ListScrollPosition(),
)

data class ListScrollPosition(val firstVisibleItemIndex: Int = 0, val firstVisibleItemScrollOffset: Int = 0)

internal val ViriViriUiState.searchInput: SearchInputSession
  get() = searchWorkspace.input

internal val ViriViriUiState.isShowingSearchResults: Boolean
  get() = searchWorkspace.route == SearchWorkspaceRoute.SEARCH_RESULTS

internal val ViriViriUiState.isSearchEmpty: Boolean
  get() = searchWorkspace.route == SearchWorkspaceRoute.SEARCH_EMPTY

internal val ViriViriUiState.isSearchKeyboardVisible: Boolean
  get() = searchWorkspace.isKeyboardVisible

internal val ViriViriUiState.isSearchKeyboardDismissed: Boolean
  get() = searchWorkspace.isKeyboardDismissed

internal val ViriViriUiState.isSearchCandidatesExpanded: Boolean
  get() = searchWorkspace.isCandidatesExpanded

internal val ViriViriUiState.searchScrollPosition: ListScrollPosition
  get() = searchWorkspace.scrollPosition

internal val ViriViriUiState.searchOptions: BilibiliSearchOptions
  get() = searchWorkspace.options

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
    private val appPreferences: AppPreferences = SharedPreferencesAppPreferences(context),
) {
  val playerSession = PlayerSession(context)
  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
  private val mutableState = MutableStateFlow(
      ViriViriUiState(
          isLoading = true,
          playbackCanvasSize = PlaybackCanvasSize.STANDARD,
          playbackStageScale = appPreferences.loadPlaybackStageScale(),
          playbackCurvature = appPreferences.loadPlaybackCurvature(),
          searchWorkspace =
              SearchWorkspaceState(
                  input = inputMethods.initialSession(),
                  history = appPreferences.loadSearchHistory(),
              ),
      )
  )
  private val mutableImmersiveBrowseCommands = MutableSharedFlow<ImmersiveBrowseCommand>(extraBufferCapacity = 1)
  private var playbackRequestId = 0L
  private var transientMessageId = 0L
  private var playbackResolutionJob: Job? = null
  private var danmakuLoadJob: Job? = null
  @Volatile private var danmakuStream: DanmakuStreamSource? = null

  /**
   * Shared streaming danmaku source for the current video. One instance is shared across every
   * danmaku canvas (single network/cache/measurement); per-canvas lane state lives in each
   * [DanmakuCanvasRuntime]. Null while no video is loaded.
   */
  internal fun danmakuStreamSource(videoId: String): DanmakuStreamSource {
    danmakuStream?.let { return it }
    return synchronized(this) {
      danmakuStream ?: run {
        val contentId = mutableState.value.selected?.takeIf { it.videoId == videoId }?.contentId
        // cid resolution and segment fetches run lazily on the source's background thread
        // (they both do network I/O); nothing here touches the network on the calling thread.
        DanmakuStreamSource(
            resolveContentId = { runCatching { provider.danmakuContentId(videoId, contentId) }.getOrElse { -1L } },
            fetchSegment = { cid, index ->
              if (cid > 0) provider.loadDanmakuSegment(cid, index) else emptyList()
            },
        )
          .also { danmakuStream = it }
      }
    }
  }

  /** Current shared stream without creating one (for debug/telemetry). */
  internal fun danmakuStreamSourceOrNull(@Suppress("UNUSED_PARAMETER") videoId: String): DanmakuStreamSource? =
      synchronized(this) { danmakuStream }

  private fun resetDanmakuStream() {
    synchronized(this) { danmakuStream = null }
  }
  private var danmakuRequestId = 0L
  private var listJob: Job? = null
  private var nextPageJob: Job? = null
  private val searchRequestTracker = SearchRequestTracker()
  private val thumbnails = ThumbnailRepository()
  private val thumbnailDownloads = Semaphore(4)
  private val mutableThumbnailStates = MutableStateFlow<Map<String, ThumbnailState>>(emptyMap())
  private val mutableVideoContext = MutableStateFlow(VideoContextState())
  internal val videoContext: StateFlow<VideoContextState> = mutableVideoContext.asStateFlow()
  private val contextRequests = SearchRequestTracker()
  private var contextDetailsJob: Job? = null
  private var contextRelatedJob: Job? = null
  val state: StateFlow<ViriViriUiState> = mutableState.asStateFlow()
  internal val thumbnailStates: StateFlow<Map<String, ThumbnailState>> = mutableThumbnailStates.asStateFlow()
  internal val immersiveBrowseCommands: SharedFlow<ImmersiveBrowseCommand> =
      mutableImmersiveBrowseCommands.asSharedFlow()

  init {
    refreshRecommendations()
    refreshHotSearch()
  }

  private var hotSearchJob: Job? = null

  fun refreshHotSearch() {
    hotSearchJob?.cancel()
    hotSearchJob =
        scope.launch {
          val keywords =
              runCatching { withContext(Dispatchers.IO) { provider.hotSearchKeywords() } }
                  .getOrDefault(emptyList())
          if (keywords.isNotEmpty()) {
            val current = mutableState.value
            mutableState.value =
                current.copy(
                    searchWorkspace = current.searchWorkspace.copy(suggestedQueries = keywords)
                )
          }
        }
  }

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
                  searchWorkspace =
                      mutableState.value.searchWorkspace.copy(
                          route = SearchWorkspaceRoute.RECOMMENDATIONS,
                          isKeyboardDismissed = false,
                          isCandidatesExpanded = false,
                      ),
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

  fun openRecommendationList() {
    val current = mutableState.value
    mutableState.value =
        current.copy(
            searchWorkspace =
                current.searchWorkspace.copy(
                    route = SearchWorkspaceRoute.RECOMMENDATIONS,
                    textInputTarget = SearchTextInputTarget.INTERNAL,
                    isKeyboardVisible = false,
                    isKeyboardDismissed = true,
                    isCandidatesExpanded = false,
                )
        )
  }

  fun openPlaybackReturnRoute() {
    val current = mutableState.value
    mutableState.value =
        current.copy(
            searchWorkspace =
                current.searchWorkspace.copy(
                    route = playbackReturnRoute(current.searchWorkspace.workbenchReturnTarget),
                    isKeyboardVisible = false,
                    isKeyboardDismissed = true,
                    isCandidatesExpanded = false,
                )
        )
  }

  fun openWorkbenchEmpty() {
    val current = mutableState.value
    mutableState.value =
        current.copy(
            searchWorkspace =
                current.searchWorkspace.copy(
                    route = SearchWorkspaceRoute.WORKBENCH_EMPTY,
                    textInputTarget = SearchTextInputTarget.INTERNAL,
                    isKeyboardVisible = false,
                    isKeyboardDismissed = true,
                    isCandidatesExpanded = false,
                )
        )
  }

  fun openSearchWorkspace() {
    val current = mutableState.value
    mutableState.value =
        current.copy(
            searchWorkspace =
                current.searchWorkspace.copy(
                    route = SearchWorkspaceRoute.SEARCH_EMPTY,
                    textInputTarget = SearchTextInputTarget.INTERNAL,
                    isKeyboardVisible = false,
                    isKeyboardDismissed = true,
                    isCandidatesExpanded = false,
                )
        )
  }

  fun returnToSearchEmpty() {
    val current = mutableState.value
    if (current.searchWorkspace.route != SearchWorkspaceRoute.SEARCH_RESULTS) return
    mutableState.value =
        current.copy(
            searchWorkspace =
                current.searchWorkspace.copy(
                    route = SearchWorkspaceRoute.SEARCH_EMPTY,
                    textInputTarget = SearchTextInputTarget.INTERNAL,
                    isKeyboardVisible = false,
                    isKeyboardDismissed = true,
                    isCandidatesExpanded = false,
                )
        )
  }

  fun closeSearchWorkspace() {
    val current = mutableState.value
    mutableState.value =
        current.copy(
            searchWorkspace =
                current.searchWorkspace.copy(
                    route = SearchWorkspaceRoute.RECOMMENDATIONS,
                    textInputTarget = SearchTextInputTarget.INTERNAL,
                    isKeyboardVisible = false,
                    isKeyboardDismissed = true,
                    isCandidatesExpanded = false,
                )
        )
  }

  fun submitSearchQuery(query: String) {
    val normalized = normalizeSearchQuery(query)
    if (normalized.isNotBlank()) submitSearch(normalized, mutableState.value.searchOptions)
  }

  fun selectSearchHistory(query: String) {
    submitSearchQuery(query)
  }

  fun removeSearchHistory(query: String) {
    val current = mutableState.value
    val history = current.searchWorkspace.history - query
    appPreferences.saveSearchHistory(history)
    mutableState.value =
        current.copy(searchWorkspace = current.searchWorkspace.copy(history = history))
  }

  fun toggleSearchHistoryExpanded() {
    val current = mutableState.value
    mutableState.value =
        current.copy(
            searchWorkspace =
                current.searchWorkspace.copy(isHistoryExpanded = !current.searchWorkspace.isHistoryExpanded)
        )
  }

  fun refreshSearchSuggestions() {
    // UX: the discovery section shows live Bilibili hot searches; refresh refetches them.
    refreshHotSearch()
  }

  fun selectSearchSuggestion(query: String) {
    // UX: tapping a hot-search keyword jumps straight to its results, like search history.
    submitSearchQuery(query)
  }

  fun updateSearchQuery(query: String) {
    val current = mutableState.value
    mutableState.value =
        current.copy(
            searchWorkspace =
                current.searchWorkspace.copy(
                    input = inputMethods.replaceCommittedText(current.searchInput, query),
                    isKeyboardDismissed = false,
                    isCandidatesExpanded = false,
                )
        )
  }

  fun applySearchInputAction(action: SearchInputAction) {
    val current = mutableState.value
    if (action == SearchInputAction.CommitComposition && current.searchInput.composition.isBlank()) {
      submitSearch()
      return
    }
    mutableState.value =
        current.copy(
            searchWorkspace =
                current.searchWorkspace.copy(
                    input = inputMethods.reduce(current.searchInput, action),
                    isCandidatesExpanded = false,
                )
        )
  }

  fun requestInternalSearchInput() {
    val current = mutableState.value
    mutableState.value =
        current.copy(
            searchWorkspace =
                current.searchWorkspace.copy(
                    textInputTarget = SearchTextInputTarget.INTERNAL,
                    isKeyboardVisible = true,
                    isKeyboardDismissed = false,
                    isCandidatesExpanded = false,
                )
        )
  }

  fun requestSystemSearchInput() {
    val current = mutableState.value
    mutableState.value =
        current.copy(
            searchWorkspace =
                current.searchWorkspace.copy(
                    textInputTarget = SearchTextInputTarget.SYSTEM,
                    isKeyboardVisible = false,
                    isKeyboardDismissed = true,
                    isCandidatesExpanded = false,
                )
        )
  }

  fun setSearchKeyboardVisible(visible: Boolean) {
    val current = mutableState.value
    mutableState.value =
        current.copy(
            searchWorkspace =
                current.searchWorkspace.copy(
                    textInputTarget = if (visible) SearchTextInputTarget.INTERNAL else current.searchWorkspace.textInputTarget,
                    isKeyboardVisible = visible,
                    isKeyboardDismissed = !visible,
                    isCandidatesExpanded = if (visible) current.isSearchCandidatesExpanded else false,
                )
        )
  }

  fun toggleSearchCandidates() {
    val current = mutableState.value
    if (current.searchInput.candidates.isEmpty()) return
    mutableState.value =
        current.copy(
            searchWorkspace =
                current.searchWorkspace.copy(isCandidatesExpanded = !current.isSearchCandidatesExpanded)
        )
  }

  fun clearSearchInput() {
    val current = mutableState.value
    mutableState.value =
        current.copy(
            searchWorkspace =
                current.searchWorkspace.copy(
                    input = inputMethods.initialSession(),
                    isCandidatesExpanded = false,
                    isKeyboardDismissed = false,
                )
        )
  }

  fun submitSearch() = submitSearch(mutableState.value.searchInput.committedText, mutableState.value.searchOptions)

  fun submitSearch(options: BilibiliSearchOptions) =
      submitSearch(mutableState.value.searchInput.committedText, options)

  fun returnToSearchEmptyFromResults() {
    returnToSearchEmpty()
  }

  private fun submitSearch(query: String, options: BilibiliSearchOptions) {
    val normalizedQuery = normalizeSearchQuery(query)
    listJob?.cancel()
    nextPageJob?.cancel()
    val requestId = searchRequestTracker.beginRequest()
    val updatedHistory =
        (listOf(normalizedQuery) + mutableState.value.searchWorkspace.history)
            .distinct()
            .take(MAX_SEARCH_HISTORY)
    appPreferences.saveSearchHistory(updatedHistory)
    if (normalizedQuery.isBlank()) {
      mutableState.value =
          mutableState.value.copy(
              searchWorkspace =
                  mutableState.value.searchWorkspace.copy(
                      input = inputMethods.initialSession(),
                      route = SearchWorkspaceRoute.SEARCH_EMPTY,
                      isKeyboardVisible = false,
                      isKeyboardDismissed = false,
                      isCandidatesExpanded = false,
                  ),
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
                  searchWorkspace =
                      mutableState.value.searchWorkspace.copy(
                          input = inputMethods.replaceCommittedText(mutableState.value.searchInput, normalizedQuery),
                          route = SearchWorkspaceRoute.SEARCH_RESULTS,
                          isKeyboardVisible = false,
                          isKeyboardDismissed = true,
                          history = updatedHistory,
                          isCandidatesExpanded = false,
                          scrollPosition = ListScrollPosition(),
                          options = options,
                      ),
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
    recommendations.mapNotNull { normalizedThumbnailUrl(it.coverUrl) }.distinct().forEach {
      requestThumbnail(it)
    }
  }

  /** Called when a lazy row enters composition, including after publication/cache eviction. */
  internal fun requestThumbnail(coverUrl: String?, retryFailed: Boolean = false) {
    val url = normalizedThumbnailUrl(coverUrl) ?: return
    if (!thumbnails.markLoading(url, retryFailed)) {
      // A repository hit may have been evicted from the separately bounded UI snapshot.
      publishThumbnailState(url)
      return
    }
    publishThumbnailState(url, ThumbnailState.Loading)
    scope.launch {
      val bitmap = thumbnailDownloads.withPermit {
        withContext(Dispatchers.IO) { thumbnails.download(url) }
      }
      thumbnails.store(url, bitmap)
      publishThumbnailState(url)
    }
  }

  private fun publishThumbnailState(url: String, state: ThumbnailState? = null) {
    val next = ((mutableThumbnailStates.value - url) +
        (url to (state ?: thumbnails.state(url) ?: ThumbnailState.Failed)))
    mutableThumbnailStates.value =
        if (next.size <= THUMBNAIL_CACHE_SIZE) {
          next
        } else {
          next.entries.toList().takeLast(THUMBNAIL_CACHE_SIZE).associate { it.toPair() }
        }
  }

  fun returnToRecommendationsFeed() {
    mutableState.value =
        mutableState.value.copy(
            searchWorkspace =
                mutableState.value.searchWorkspace.copy(
                    input = inputMethods.initialSession(),
                    route = SearchWorkspaceRoute.RECOMMENDATIONS,
                    isKeyboardVisible = false,
                    isKeyboardDismissed = false,
                    isCandidatesExpanded = false,
                )
        )
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
              searchWorkspace =
                  current.searchWorkspace.copy(
                      scrollPosition = scrollPosition,
                      workbenchReturnTarget = WorkbenchReturnTarget.SEARCH_RESULTS,
                  ),
          )
        } else {
          current.copy(
              selected = recommendation,
              destination = ViriViriDestination.VIEWER,
              isResolvingPlayback = true,
              error = null,
              recommendationScrollPosition = scrollPosition,
              searchWorkspace =
                  current.searchWorkspace.copy(
                      workbenchReturnTarget = WorkbenchReturnTarget.VIDEO_LIST,
                  ),
          )
        }
    startPlaybackResolution(recommendation)
    loadVideoContext(recommendation.videoId)
  }

  /** Context selection preserves the center browse return target and scroll position. */
  internal fun selectContextVideo(video: Recommendation) {
    val current = mutableState.value
    if (current.selected?.videoId == video.videoId &&
        (video.contentId == null || current.selected.contentId == video.contentId)) return
    mutableState.value = current.copy(selected = video, destination = ViriViriDestination.VIEWER)
    startPlaybackResolution(video)
    loadVideoContext(video.videoId)
  }

  internal fun selectContextPart(contentId: Long) {
    val current = mutableState.value
    val selected = current.selected ?: return
    val context = mutableVideoContext.value.takeIf { it.videoId == selected.videoId } ?: return
    val part = context.details.parts.firstOrNull { it.contentId == contentId } ?: return
    if (selected.matchesPart(part)) return
    val next = selected.withPart(part)
    mutableState.value = current.copy(selected = next)
    startPlaybackResolution(next)
  }

  internal fun retryVideoContext() {
    mutableState.value.selected?.videoId?.let { loadVideoContext(it, force = true) }
  }

  private fun loadVideoContext(videoId: String, force: Boolean = false) {
    if (!force && mutableVideoContext.value.videoId == videoId) return
    contextDetailsJob?.cancel()
    contextRelatedJob?.cancel()
    val requestId = contextRequests.beginRequest()
    val previous = mutableVideoContext.value
    mutableVideoContext.value =
        (previous.takeIf { it.videoId == videoId } ?: VideoContextState(videoId)).copy(
            loadingDetails = true, loadingRelated = true, detailsError = null, relatedError = null)
    fun isCurrent() = contextRequests.isCurrent(requestId) && mutableState.value.selected?.videoId == videoId
    contextDetailsJob = scope.launch {
      try {
        val details = withTimeout(PLAYBACK_RESOLUTION_TIMEOUT_MS) {
          withContext(Dispatchers.IO) { provider.loadVideoContext(videoId) }
        }
        if (isCurrent()) {
          mutableVideoContext.value = mutableVideoContext.value.copy(details = details, loadingDetails = false)
        }
      } catch (_: CancellationException) {
        if (isCurrent()) mutableVideoContext.value = mutableVideoContext.value.copy(
            loadingDetails = false, detailsError = "选集加载超时，请重试")
      } catch (error: Exception) {
        if (isCurrent()) mutableVideoContext.value = mutableVideoContext.value.copy(
            loadingDetails = false, detailsError = error.message ?: "选集加载失败")
      }
    }
    contextRelatedJob = scope.launch {
      try {
        val related = withTimeout(PLAYBACK_RESOLUTION_TIMEOUT_MS) {
          withContext(Dispatchers.IO) { provider.loadRelatedVideos(videoId) }
        }
        if (isCurrent()) {
          mutableVideoContext.value = mutableVideoContext.value.copy(related = related, loadingRelated = false)
        }
      } catch (_: CancellationException) {
        if (isCurrent()) mutableVideoContext.value = mutableVideoContext.value.copy(
            loadingRelated = false, relatedError = "相关推荐加载超时，请重试")
      } catch (error: Exception) {
        if (isCurrent()) mutableVideoContext.value = mutableVideoContext.value.copy(
            loadingRelated = false, relatedError = error.message ?: "相关推荐加载失败")
      }
    }
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
    if (current.playbackCanvasSize == canvasSize && current.playbackStageScale == canvasSize.scale) return
    appPreferences.savePlaybackStageScale(canvasSize.scale)
    mutableState.value =
        current.copy(
            playbackCanvasSize = canvasSize,
            playbackStageScale = canvasSize.scale,
        )
  }

  fun setPlaybackStageScale(stageScale: Float) {
    val current = mutableState.value
    val nextScale = PlaybackCanvasSize.clampStageScale(stageScale)
    if (nextScale == current.playbackStageScale) return
    appPreferences.savePlaybackStageScale(nextScale)
    mutableState.value = current.copy(playbackStageScale = nextScale)
  }

  fun adjustPlaybackStageScale(delta: Float) {
    setPlaybackStageScale(mutableState.value.playbackStageScale + delta)
  }

  /**
   * Persists and publishes the stage curvature — ONE value shared by every layer.
   *
   * Video, danmaku and backdrop are kept in lockstep. They have to be: the backdrop exists to dim the
   * MediaStage, so a backdrop on a different radius cannot dim it, and a danmaku plane on a different
   * radius slices through it. A layer added later (subtitles, for instance) inherits the same value by
   * simply reading [ViriViriUiState.playbackCurvature] — there is nothing per-layer to opt into.
   */
  fun setPlaybackCurvature(curvature: PlaybackStageCurvature) {
    val current = mutableState.value
    if (current.playbackCurvature == curvature) return
    appPreferences.savePlaybackCurvature(curvature)
    mutableState.value = current.copy(playbackCurvature = curvature)
  }

  /**
   * Thumbstick curvature step for the whole stage. [curveDelta] follows
   * [PlaybackStageCurvature.adjustedBy]: positive bends tighter, negative flattens back towards
   * [PlaybackStageCurvature.Flat].
   */
  fun adjustPlaybackCurvature(curveDelta: Float) {
    setPlaybackCurvature(mutableState.value.playbackCurvature.adjustedBy(curveDelta))
  }

  /**
   * Loads the user-adjusted immersive stage world Y. Returns the authored default height
   * when the user never grabbed the stage. Only the height survives restarts; horizontal
   * position and yaw are reset to their authored defaults every launch.
   */
  fun loadWorkbenchStageYOrDefault(defaultY: Float = STAGE_DEFAULT_WORLD_Y): Float =
      appPreferences.loadWorkbenchStageY() ?: defaultY

  /** Persists the user-adjusted immersive stage world Y after a grab finishes. */
  fun persistWorkbenchStageY(y: Float) {
    appPreferences.saveWorkbenchStageY(y)
  }

  /**
   * Clears the persisted stage Y so the next launch uses the authored default again.
   * Returns the default height so callers can also snap the stage back immediately.
   */
  fun resetWorkbenchStageY(): Float {
    appPreferences.clearWorkbenchStageY()
    return STAGE_DEFAULT_WORLD_Y
  }

  private fun startPlaybackResolution(
      recommendation: Recommendation,
      quality: PlaybackQuality = mutableState.value.playbackQuality,
      startPositionMs: Long = 0L,
      playWhenReady: Boolean = true,
  ) {
    playbackResolutionJob?.cancel()
    danmakuLoadJob?.cancel()
    resetDanmakuStream()
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
                    provider.createMediaSource(recommendation.videoId, quality, recommendation.contentId)
                  }
                }
            if (requestId == playbackRequestId) {
              playerSession.setMediaSource(source, startPositionMs, playWhenReady)
              // Danmaku now stream lazily via the shared DanmakuStreamSource (6-min protobuf
              // segments + per-canvas active set); the full XML preload below is retained only as
              // a disabled legacy path reference.
              mutableState.value =
                  mutableState.value.copy(isResolvingPlayback = false, isLoadingDanmaku = false)
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
