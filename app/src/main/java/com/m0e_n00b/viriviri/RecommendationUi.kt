package com.m0e_n00b.viriviri

import android.graphics.Matrix
import android.view.TextureView
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.Button
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.media3.common.Player

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
fun RecommendationContent(state: ViriViriUiState, appState: ViriViriAppState, showPlayer: Boolean) {
  when (state.destination) {
    ViriViriDestination.RECOMMENDATIONS -> RecommendationList(state, appState)
    ViriViriDestination.VIEWER -> Viewer(state, appState, showPlayer)
  }
}

@Composable
fun RecommendationPanel(appState: ViriViriAppState = ViriViriApplication.appState) {
  val state by appState.state.collectAsState()
  Box(modifier = Modifier.fillMaxSize().background(Color(0xFF102025))) {
    RecommendationContent(state, appState, showPlayer = false)
  }
}

@Composable
private fun RecommendationList(state: ViriViriUiState, appState: ViriViriAppState) {
  val savedScrollPosition =
      if (state.isShowingSearchResults) state.searchScrollPosition else state.recommendationScrollPosition
  val listState =
      rememberLazyListState(
          initialFirstVisibleItemIndex = savedScrollPosition.firstVisibleItemIndex,
          initialFirstVisibleItemScrollOffset = savedScrollPosition.firstVisibleItemScrollOffset,
      )
  Column(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
      Text(if (state.isShowingSearchResults) "Search results" else "Bilibili recommendations", color = Color.White)
      Button(onClick = if (state.isShowingSearchResults) appState::returnToRecommendationsFeed else appState::refreshRecommendations) {
        Text(if (state.isShowingSearchResults) "Recommendations" else "Refresh")
      }
    }
    OutlinedTextField(
        value = state.searchQuery,
        onValueChange = appState::updateSearchQuery,
        modifier = Modifier.fillMaxWidth(),
        label = { Text("Search Bilibili") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { appState.submitSearch(state.searchQuery) }),
    )
    when {
      state.isLoading -> Text(if (state.isShowingSearchResults) "Searching Bilibili..." else "Loading recommendations...", color = Color.White)
      state.error != null -> Text(state.error, color = Color(0xFFFFB4AB))
      state.recommendations.isEmpty() -> Text(if (state.isShowingSearchResults) "No matching videos found." else "No recommendations are available.", color = Color.White)
      else -> LazyColumn(state = listState, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(state.recommendations, key = { it.videoId }) { recommendation ->
          Column(
              modifier =
                  Modifier.fillMaxWidth()
                      .clickable {
                        appState.selectRecommendation(
                            recommendation,
                            ListScrollPosition(listState.firstVisibleItemIndex, listState.firstVisibleItemScrollOffset),
                        )
                      }
                      .padding(8.dp)
          ) {
            Text(recommendation.title, color = Color.White)
            Text(recommendation.authorName, color = Color.LightGray)
          }
        }
      }
    }
  }
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
