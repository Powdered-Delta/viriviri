package com.m0e_n00b.viriviri

import android.graphics.Matrix
import android.view.TextureView
import androidx.compose.foundation.Image
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.material.Button
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Refresh
import androidx.media3.common.Player
import com.m0e_n00b.spatialworkbench.compose.ContentAccessBadge
import com.m0e_n00b.spatialworkbench.compose.InputConsoleStyle
import com.m0e_n00b.spatialworkbench.compose.MediaThumbnailFrame
import com.m0e_n00b.spatialworkbench.compose.TransientMessageHost
import com.m0e_n00b.spatialworkbench.compose.WorkbenchPanelStyle
import com.m0e_n00b.spatialworkbench.compose.composeColor
import com.m0e_n00b.spatialworkbench.core.CinemaColorRole
import com.m0e_n00b.spatialworkbench.core.CinemaPalette
import com.m0e_n00b.spatialworkbench.core.ContentAccess
import com.m0e_n00b.spatialworkbench.core.TransientMessage
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

internal data class TextureViewScale(val x: Float, val y: Float)

internal fun calculateTextureViewScale(
    containerWidth: Int,
    containerHeight: Int,
    videoWidth: Int,
    videoHeight: Int,
    pixelWidthHeightRatio: Float = 1f,
): TextureViewScale {
  if (
      containerWidth <= 0 ||
          containerHeight <= 0 ||
          videoWidth <= 0 ||
          videoHeight <= 0 ||
          !pixelWidthHeightRatio.isFinite() ||
          pixelWidthHeightRatio <= 0f
  ) {
    return TextureViewScale(0f, 0f)
  }
  val containerAspectRatio = containerWidth.toFloat() / containerHeight
  val videoAspectRatio = videoWidth.toFloat() * pixelWidthHeightRatio / videoHeight
  return if (videoAspectRatio > containerAspectRatio) {
    TextureViewScale(1f, containerAspectRatio / videoAspectRatio)
  } else {
    TextureViewScale(videoAspectRatio / containerAspectRatio, 1f)
  }
}

@Composable
fun RecommendationContent(
    state: ViriViriUiState,
    appState: ViriViriAppState,
    showPlayer: Boolean,
    onReturnToPlayback: (() -> Unit)? = null,
    palette: CinemaPalette = CinemaPalette.DARK,
    showSearchConsoleByDefault: Boolean = !showPlayer,
    showViewerContent: Boolean = true,
    onVideoSelected: (() -> Unit)? = null,
    onDismissWorkbench: (() -> Unit)? = null,
) {
  Box(modifier = Modifier.fillMaxSize()) {
    when (state.destination) {
      ViriViriDestination.RECOMMENDATIONS ->
          CenterContentWorkspace(
              state = state,
              appState = appState,
              palette = palette,
              showSearchConsoleByDefault = showSearchConsoleByDefault,
              onReturnToPlayback = onReturnToPlayback,
              onVideoSelected = onVideoSelected,
              onDismissWorkbench = onDismissWorkbench,
          )
      ViriViriDestination.VIEWER -> {
        if (showViewerContent) {
          Viewer(state, appState, showPlayer)
        } else {
          CenterContentWorkspace(
              state = state,
              appState = appState,
              palette = palette,
              showSearchConsoleByDefault = showSearchConsoleByDefault,
              onReturnToPlayback = onReturnToPlayback,
              onVideoSelected = onVideoSelected,
              onDismissWorkbench = onDismissWorkbench,
          )
        }
      }
    }
    TransientMessageHost(
        state = state.transientMessages,
        palette = palette,
        onEvent = appState::dispatchTransientMessage,
        onAction = { _: TransientMessage, _: String -> },
        modifier = Modifier.align(Alignment.BottomCenter),
    )
  }
}

@Composable
fun RecommendationPanel(
    appState: ViriViriAppState = ViriViriApplication.appState,
    onReturnToPlayback: (() -> Unit)? = null,
    palette: CinemaPalette = CinemaPalette.DARK,
    searchOpenByDefault: Boolean = false,
    showViewerContent: Boolean = true,
    onVideoSelected: (() -> Unit)? = null,
    onDismissWorkbench: (() -> Unit)? = null,
) {
  val state by appState.state.collectAsState()
  // UX: Browse and Detail share one semantic palette while occupying the same angled panel.
  Box(modifier = Modifier.fillMaxSize().background(palette.composeColor(CinemaColorRole.BACKGROUND))) {
    RecommendationContent(
        state = state,
        appState = appState,
        showPlayer = false,
        onReturnToPlayback = onReturnToPlayback,
        palette = palette,
        showSearchConsoleByDefault = searchOpenByDefault,
        showViewerContent = showViewerContent,
        onVideoSelected = onVideoSelected,
        onDismissWorkbench = onDismissWorkbench,
    )
  }
}

@Composable
private fun CenterContentWorkspace(
    state: ViriViriUiState,
    appState: ViriViriAppState,
    palette: CinemaPalette,
    showSearchConsoleByDefault: Boolean,
    onReturnToPlayback: (() -> Unit)?,
    onVideoSelected: (() -> Unit)?,
    onDismissWorkbench: (() -> Unit)?,
) {
  val savedScrollPosition =
      if (state.isShowingSearchResults) state.searchScrollPosition else state.recommendationScrollPosition
  val listState =
      rememberLazyListState(
          initialFirstVisibleItemIndex = savedScrollPosition.firstVisibleItemIndex,
          initialFirstVisibleItemScrollOffset = savedScrollPosition.firstVisibleItemScrollOffset,
      )
  val gridState =
      rememberLazyGridState(
          initialFirstVisibleItemIndex = savedScrollPosition.firstVisibleItemIndex,
          initialFirstVisibleItemScrollOffset = savedScrollPosition.firstVisibleItemScrollOffset,
      )
  val thumbnailStates by appState.thumbnailStates.collectAsState()
  val searchConsoleVisible = showSearchConsoleByDefault || state.isShowingSearchResults
  var isGridView by rememberSaveable { mutableStateOf(true) }
  var filterState by remember { mutableStateOf(VideoListFilterState()) }
  var moreFiltersExpanded by rememberSaveable { mutableStateOf(false) }
  val coroutineScope = rememberCoroutineScope()
  fun applyFilter(next: VideoListFilterState) {
    filterState = next
    if (state.isShowingSearchResults) appState.submitSearch(next.toBilibiliSearchOptions())
  }

  val panelStyle = remember(palette) { WorkbenchPanelStyle.fromPalette(palette) }
  val inputStyle = remember(palette) { InputConsoleStyle.fromPalette(palette) }
  LaunchedEffect(listState, gridState, isGridView, state.recommendations.size, state.canLoadMore, state.isLoadingNextPage) {
    snapshotFlow {
          val lastVisible =
              if (isGridView) {
                gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
              } else {
                listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
              }
          lastVisible >= state.recommendations.lastIndex - PAGINATION_PREFETCH_DISTANCE
        }
        .distinctUntilChanged()
        .collect { nearEnd ->
          if (nearEnd) appState.loadNextPage()
        }
  }
  // UX: card/input actions consume their own click; only unclaimed center-panel background clicks dismiss Workbench.
  Column(
      modifier =
          Modifier.fillMaxSize()
              .background(palette.composeColor(CinemaColorRole.BACKGROUND))
              .clickable(enabled = onDismissWorkbench != null) { onDismissWorkbench?.invoke() }
              .padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    VideoListFilterBar(
        state = filterState,
        isGridView = isGridView,
        moreExpanded = moreFiltersExpanded,
        style = panelStyle,
        onSortChanged = { applyFilter(filterState.copy(sort = it)) },
        onDateChanged = { applyFilter(filterState.copy(date = it)) },
        onDurationChanged = { applyFilter(filterState.copy(duration = it)) },
        onToggleMore = { moreFiltersExpanded = !moreFiltersExpanded },
        remoteFilteringAvailable = state.isShowingSearchResults,
        onToggleLayout = { isGridView = !isGridView },
        isAtTop = if (isGridView) gridState.firstVisibleItemIndex == 0 else listState.firstVisibleItemIndex == 0,
        onTopOrRefresh = {
          if (if (isGridView) gridState.firstVisibleItemIndex == 0 else listState.firstVisibleItemIndex == 0) {
            if (state.isShowingSearchResults) appState.submitSearch(filterState.toBilibiliSearchOptions())
            else appState.refreshRecommendations()
          } else if (isGridView) {
            coroutineScope.launch { gridState.animateScrollToItem(0) }
          } else {
            coroutineScope.launch { listState.animateScrollToItem(0) }
          }
        },
        modifier = Modifier.fillMaxWidth(),
    )
    SearchPanel(
        session = state.searchInput,
        method = appState.inputMethods.methodFor(state.searchInput),
        actions =
            SearchPanelActions(
                onQueryChanged = appState::updateSearchQuery,
                onInputAction = appState::applySearchInputAction,
                onClear = appState::clearSearchInput,
                onSubmit = appState::submitSearch,
            ),
        style = panelStyle,
        inputStyle = inputStyle,
        visible = searchConsoleVisible,
        modifier = Modifier.fillMaxWidth(),
    )
    val selectRecommendation: (Recommendation) -> Unit = { recommendation ->
      val position =
          if (isGridView) {
            ListScrollPosition(gridState.firstVisibleItemIndex, gridState.firstVisibleItemScrollOffset)
          } else {
            ListScrollPosition(listState.firstVisibleItemIndex, listState.firstVisibleItemScrollOffset)
          }
      appState.selectRecommendation(recommendation, position)
      // UX: selection immediately closes center content so Viewer fallback content can never flash over MediaStage.
      onVideoSelected?.invoke()
    }
    VideoListPanel(
        state = state,
        thumbnailStates = thumbnailStates,
        isGridView = isGridView,
        listState = listState,
        gridState = gridState,
        style = panelStyle,
        palette = palette,
        onSelect = selectRecommendation,
        onToggleLayout = { isGridView = !isGridView },
        modifier = Modifier.weight(1f),
    )
  }
}

@Composable
internal fun VideoListPanel(
    state: ViriViriUiState,
    thumbnailStates: Map<String, ThumbnailState>,
    isGridView: Boolean,
    listState: LazyListState,
    gridState: LazyGridState,
    style: WorkbenchPanelStyle,
    palette: CinemaPalette,
    onSelect: (Recommendation) -> Unit,
    onToggleLayout: () -> Unit,
    modifier: Modifier = Modifier,
) {
  // UX: VideoList is a standardized center-content module and never owns player or Spatial state.
  when {
    state.isLoading ->
        Text(
            if (state.isShowingSearchResults) "Searching Bilibili..." else "Loading recommendations...",
            color = style.text,
            modifier = modifier,
        )
    state.recommendations.isEmpty() && state.error != null ->
        Text(state.error, color = palette.composeColor(CinemaColorRole.DANGER), modifier = modifier)
    state.recommendations.isEmpty() ->
        Text(
            if (state.isShowingSearchResults) "No matching videos found." else "No recommendations are available.",
            color = style.text,
            modifier = modifier,
        )
    isGridView ->
        LazyVerticalGrid(
            // UX: the center content plane uses the approved three-column video grid.
            columns = GridCells.Fixed(CENTER_VIDEO_GRID_COLUMNS),
            state = gridState,
            modifier = modifier,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          items(state.recommendations, key = { it.videoId }) { recommendation ->
            RecommendationCard(
                recommendation = recommendation,
                thumbnailState = thumbnailStates[normalizedThumbnailUrl(recommendation.coverUrl)],
                palette = palette,
                onClick = { onSelect(recommendation) },
            )
          }
          item(key = "pagination-status", span = { GridItemSpan(maxLineSpan) }) {
            PaginationStatus(state, palette)
          }
        }
    else ->
        LazyColumn(
            state = listState,
            modifier = modifier,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          items(state.recommendations, key = { it.videoId }) { recommendation ->
            RecommendationRow(
                recommendation = recommendation,
                thumbnailState = thumbnailStates[normalizedThumbnailUrl(recommendation.coverUrl)],
                palette = palette,
                onClick = { onSelect(recommendation) },
            )
          }
          item(key = "pagination-status") { PaginationStatus(state, palette) }
        }
  }
}

@Composable
private fun PaginationStatus(state: ViriViriUiState, palette: CinemaPalette) {
  when {
    state.isLoadingNextPage ->
        Text(
            "Loading more...",
            color = palette.composeColor(CinemaColorRole.SECONDARY_TEXT),
            modifier = Modifier.padding(8.dp),
        )
    state.error != null ->
        Text(
            state.error,
            color = palette.composeColor(CinemaColorRole.DANGER),
            modifier = Modifier.padding(8.dp),
        )
    !state.canLoadMore ->
        Text(
            "No more videos",
            color = palette.composeColor(CinemaColorRole.SECONDARY_TEXT),
            modifier = Modifier.padding(8.dp),
        )
  }
}

@Composable
private fun RecommendationCard(
    recommendation: Recommendation,
    thumbnailState: ThumbnailState?,
    palette: CinemaPalette,
    onClick: () -> Unit,
) {
  Column(
      modifier =
          Modifier.fillMaxWidth()
              .background(palette.composeColor(CinemaColorRole.SURFACE))
              .clickable(onClick = onClick)
              .padding(8.dp),
      verticalArrangement = Arrangement.spacedBy(5.dp),
  ) {
    Box(
        modifier =
            Modifier.fillMaxWidth()
                .aspectRatio(16f / 9f)
                .background(palette.composeColor(CinemaColorRole.SECONDARY_BUTTON)),
        contentAlignment = Alignment.Center,
    ) {
      RecommendationThumbnailContent(thumbnailState, palette)
      // UX: access marker stays top-left while duration remains legible at the thumbnail's bottom-right.
      ContentAccessBadge(
          access = recommendation.access,
          palette = palette,
          modifier = Modifier.align(Alignment.TopStart),
      )
      recommendation.durationSeconds?.let { durationSeconds ->
        Text(
            text = formatTransportTimecode(durationSeconds * 1_000L),
            color = palette.composeColor(CinemaColorRole.NORMAL_TEXT),
            fontSize = 10.sp,
            modifier =
                Modifier.align(Alignment.BottomEnd)
                    .background(palette.composeColor(CinemaColorRole.BACKGROUND).copy(alpha = 0.88f))
                    .padding(horizontal = 4.dp, vertical = 2.dp),
        )
      }
    }
    Text(
        text = recommendation.title,
        color = palette.composeColor(CinemaColorRole.NORMAL_TEXT),
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        fontWeight = FontWeight.Medium,
    )
    Text(
        text = recommendation.authorName,
        color = palette.composeColor(CinemaColorRole.SECONDARY_TEXT),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
      Text(
          formatMetric(recommendation.viewCount, "播放"),
          color = palette.composeColor(CinemaColorRole.SECONDARY_TEXT),
      )
      recommendation.likeCount?.let { likeCount ->
        Icon(
            imageVector = Icons.Default.Favorite,
            contentDescription = "Likes",
            tint = palette.composeColor(CinemaColorRole.SECONDARY_TEXT),
            modifier = Modifier.size(14.dp),
        )
        Text(
            formatMetric(likeCount, "点赞"),
            color = palette.composeColor(CinemaColorRole.SECONDARY_TEXT),
        )
      }
    }
  }
}

private fun formatMetric(value: Long?, label: String): String =
    when {
      value == null -> "$label --"
      value >= 100_000_000L -> "$label %.1f亿".format(value / 100_000_000f)
      value >= 10_000L -> "$label %.1f万".format(value / 10_000f)
      else -> "$label $value"
    }

@Composable
private fun RecommendationThumbnailContent(state: ThumbnailState?, palette: CinemaPalette) {
  when (state) {
    is ThumbnailState.Ready -> Image(
        bitmap = state.bitmap.asImageBitmap(),
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = Modifier.fillMaxSize(),
    )
    ThumbnailState.Loading ->
        Text("Loading", color = palette.composeColor(CinemaColorRole.SECONDARY_TEXT))
    ThumbnailState.Failed, null ->
        Text("No image", color = palette.composeColor(CinemaColorRole.SECONDARY_TEXT))
  }
}

@Composable
private fun RecommendationRow(
    recommendation: Recommendation,
    thumbnailState: ThumbnailState?,
    palette: CinemaPalette,
    onClick: () -> Unit,
) {
  Row(
      modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(8.dp),
      verticalAlignment = Alignment.CenterVertically,
  ) {
    Thumbnail(
        state = thumbnailState,
        palette = palette,
        access = recommendation.access,
    )
    Spacer(Modifier.width(10.dp))
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
      Text(recommendation.title, color = palette.composeColor(CinemaColorRole.NORMAL_TEXT))
      Text(recommendation.authorName, color = palette.composeColor(CinemaColorRole.SECONDARY_TEXT))
      recommendation.durationSeconds?.let {
        Text(
            formatTransportTimecode(it * 1_000L),
            color = palette.composeColor(CinemaColorRole.SECONDARY_TEXT),
        )
      }
    }
  }
}

@Composable
private fun Thumbnail(
    state: ThumbnailState?,
    palette: CinemaPalette,
    access: ContentAccess = ContentAccess.STANDARD,
) {
  MediaThumbnailFrame(
      content = { RecommendationThumbnailContent(state, palette) },
      overlay = {
        ContentAccessBadge(
            access = access,
            palette = palette,
            modifier = Modifier.align(Alignment.BottomEnd),
        )
      },
  )
}

@Composable
private fun Viewer(state: ViriViriUiState, appState: ViriViriAppState, showPlayer: Boolean) {
  Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
    Button(onClick = appState::returnToRecommendations) { Text("Back to recommendations") }
    Text(state.selected?.title ?: "Selected video", color = Color.White)
    state.error?.let { Text(it, color = Color(0xFFFFB4AB)) }
    if (showPlayer) PlayerOutput(appState.playerSession)
  }
}

@Composable
private fun PlayerOutput(session: PlayerSession) {
  val context = LocalContext.current
  val textureView = remember { mutableStateOf<AspectRatioTextureView?>(null) }
  DisposableEffect(session) {
    val listener =
        object : Player.Listener {
          override fun onVideoSizeChanged(videoSize: androidx.media3.common.VideoSize) {
            textureView.value?.setVideoSize(
                videoSize.width,
                videoSize.height,
                videoSize.pixelWidthHeightRatio,
            )
          }
        }
    session.player.addListener(listener)
    onDispose { session.player.removeListener(listener) }
  }
  Box(modifier = Modifier.fillMaxWidth().height(260.dp).background(Color.Black)) {
    AndroidView(
        factory = {
          AspectRatioTextureView(context).apply {
            val output = this
            textureView.value = this
            setVideoSize(
                session.player.videoSize.width,
                session.player.videoSize.height,
                session.player.videoSize.pixelWidthHeightRatio,
            )
            var renderSurface: android.view.Surface? = null
            surfaceTextureListener = object : TextureView.SurfaceTextureListener {
              override fun onSurfaceTextureAvailable(texture: android.graphics.SurfaceTexture, width: Int, height: Int) {
                output.refreshVideoOutput()
                renderSurface = android.view.Surface(texture).also(session::attach2dSurface)
              }
              override fun onSurfaceTextureSizeChanged(texture: android.graphics.SurfaceTexture, width: Int, height: Int) = Unit
              override fun onSurfaceTextureDestroyed(texture: android.graphics.SurfaceTexture): Boolean {
                renderSurface?.let { surface ->
                  session.detachSurface(surface)
                  surface.release()
                }
                renderSurface = null
                return true
              }
              override fun onSurfaceTextureUpdated(texture: android.graphics.SurfaceTexture) = Unit
            }
          }
        },
        modifier = Modifier.fillMaxSize(),
    )
  }
}

private const val PAGINATION_PREFETCH_DISTANCE = 4
internal const val CENTER_VIDEO_GRID_COLUMNS = 3

private class AspectRatioTextureView(context: android.content.Context) : TextureView(context) {
  private var videoWidth = 0
  private var videoHeight = 0
  private var pixelWidthHeightRatio = 1f

  fun setVideoSize(width: Int, height: Int, pixelWidthHeightRatio: Float) {
    videoWidth = width
    videoHeight = height
    this.pixelWidthHeightRatio = pixelWidthHeightRatio
    updateTransform()
  }

  fun refreshVideoOutput() = updateTransform()

  override fun onSizeChanged(width: Int, height: Int, oldWidth: Int, oldHeight: Int) {
    super.onSizeChanged(width, height, oldWidth, oldHeight)
    updateTransform()
  }

  private fun updateTransform() {
    if (videoWidth > 0 && videoHeight > 0) surfaceTexture?.setDefaultBufferSize(videoWidth, videoHeight)
    val scale =
        calculateTextureViewScale(width, height, videoWidth, videoHeight, pixelWidthHeightRatio)
    setTransform(Matrix().apply { setScale(scale.x, scale.y, width / 2f, height / 2f) })
  }
}
