package com.m0e_n00b.viriviri

import android.view.TextureView
import androidx.compose.foundation.clickable
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
import androidx.compose.material.Button
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.unit.dp

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
  RecommendationContent(state, appState, showPlayer = false)
}

@Composable
private fun RecommendationList(state: ViriViriUiState, appState: ViriViriAppState) {
  Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
      Text("Bilibili recommendations", color = Color.White)
      Button(onClick = appState::refreshRecommendations) { Text("Refresh") }
    }
    when {
      state.isLoading -> Text("Loading recommendations...", color = Color.White)
      state.error != null -> Text(state.error, color = Color(0xFFFFB4AB))
      state.recommendations.isEmpty() -> Text("No recommendations are available.", color = Color.White)
      else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(state.recommendations, key = { it.videoId }) { recommendation ->
          Column(
              modifier = Modifier.fillMaxWidth().clickable { appState.selectRecommendation(recommendation) }.padding(8.dp)
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
  AndroidView(
      factory = {
        TextureView(context).apply {
          var renderSurface: android.view.Surface? = null
          surfaceTextureListener = object : TextureView.SurfaceTextureListener {
            override fun onSurfaceTextureAvailable(texture: android.graphics.SurfaceTexture, width: Int, height: Int) {
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
      modifier = Modifier.fillMaxWidth().height(260.dp),
  )
}
