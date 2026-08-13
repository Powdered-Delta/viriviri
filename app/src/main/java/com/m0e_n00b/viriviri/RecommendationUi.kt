package com.m0e_n00b.viriviri

import android.graphics.Matrix
import android.view.TextureView
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.material.Button
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import com.m0e_n00b.spatialworkbench.compose.ContentAccessBadge
import com.m0e_n00b.spatialworkbench.compose.MediaThumbnailFrame
import com.m0e_n00b.spatialworkbench.core.CinemaPalette
import com.m0e_n00b.spatialworkbench.core.ContentAccess
import kotlinx.coroutines.flow.distinctUntilChanged

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
    palette: CinemaPalette = CinemaPalette.DARK,
) {
  when (state.destination) {
    ViriViriDestination.RECOMMENDATIONS -> RecommendationList(state, appState, palette)
    ViriViriDestination.VIEWER -> Viewer(state, appState, showPlayer)
  }
}

@Composable
fun RecommendationPanel(
    appState: ViriViriAppState = ViriViriApplication.appState,
    palette: CinemaPalette = CinemaPalette.DARK,
) {
  val state by appState.state.collectAsState()
  Box(modifier = Modifier.fillMaxSize().background(Color(0xFF102025))) {
    RecommendationContent(state, appState, showPlayer = false, palette = palette)
  }
}

@Composable
private fun RecommendationList(
    state: ViriViriUiState,
    appState: ViriViriAppState,
    palette: CinemaPalette,
) {
  val savedScrollPosition =
      if (state.isShowingSearchResults) state.searchScrollPosition else state.recommendationScrollPosition
  val listState =
      rememberLazyListState(
          initialFirstVisibleItemIndex = savedScrollPosition.firstVisibleItemIndex,
          initialFirstVisibleItemScrollOffset = savedScrollPosition.firstVisibleItemScrollOffset,
      )
  LaunchedEffect(listState, state.recommendations.size, state.canLoadMore, state.isLoadingNextPage) {
    snapshotFlow {
          val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
          lastVisible >= state.recommendations.lastIndex - PAGINATION_PREFETCH_DISTANCE
        }
        .distinctUntilChanged()
        .collect { nearEnd ->
          if (nearEnd) appState.loadNextPage()
        }
  }
  val thumbnailStates by appState.thumbnailStates.collectAsState()
  Column(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
      Text(if (state.isShowingSearchResults) "Search results" else "Bilibili recommendations", color = Color.White)
      Button(onClick = if (state.isShowingSearchResults) appState::returnToRecommendationsFeed else appState::refreshRecommendations) {
        Text(if (state.isShowingSearchResults) "Recommendations" else "Refresh")
      }
    }
    SearchInputPanel(
        session = state.searchInput,
        method = appState.inputMethods.methodFor(state.searchInput),
        onSystemTextChanged = appState::updateSearchQuery,
        onInputAction = appState::applySearchInputAction,
        onClear = appState::clearSearchInput,
        onSearch = appState::submitSearch,
    )
    when {
      state.isLoading -> Text(if (state.isShowingSearchResults) "Searching Bilibili..." else "Loading recommendations...", color = Color.White)
      state.recommendations.isEmpty() && state.error != null -> Text(state.error, color = Color(0xFFFFB4AB))
      state.recommendations.isEmpty() -> Text(if (state.isShowingSearchResults) "No matching videos found." else "No recommendations are available.", color = Color.White)
      else -> LazyColumn(state = listState, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(state.recommendations, key = { it.videoId }) { recommendation ->
          RecommendationRow(
              recommendation = recommendation,
              thumbnailState = thumbnailStates[normalizedThumbnailUrl(recommendation.coverUrl)],
              palette = palette,
              onClick = {
                appState.selectRecommendation(
                    recommendation,
                    ListScrollPosition(listState.firstVisibleItemIndex, listState.firstVisibleItemScrollOffset),
                )
              },
          )
        }
        item(key = "pagination-status") {
          when {
            state.isLoadingNextPage -> Text("Loading more...", color = Color.LightGray, modifier = Modifier.padding(8.dp))
            state.error != null -> Text(state.error, color = Color(0xFFFFB4AB), modifier = Modifier.padding(8.dp))
            !state.canLoadMore -> Text("No more videos", color = Color.LightGray, modifier = Modifier.padding(8.dp))
          }
        }
      }
    }
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
      Text(recommendation.title, color = Color.White)
      Text(recommendation.authorName, color = Color.LightGray)
      recommendation.durationSeconds?.let { Text(formatTransportTimecode(it * 1_000L), color = Color.LightGray) }
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
      content = {
        when (state) {
          is ThumbnailState.Ready -> Image(
              bitmap = state.bitmap.asImageBitmap(),
              contentDescription = null,
              contentScale = ContentScale.Crop,
              modifier = Modifier.fillMaxSize(),
          )
          ThumbnailState.Loading -> Text("Loading", color = Color.LightGray)
          ThumbnailState.Failed -> Text("No image", color = Color.LightGray)
          null -> Text("No image", color = Color.LightGray)
        }
      },
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
