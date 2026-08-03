package com.m0e_n00b.viriviri

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Button
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

class PancakeActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setTheme(R.style.PanelAppThemeTransparent)
    setContent {
      MaterialTheme {
        PancakeScreen(ViriViriApplication.appState) {
          ViriViriApplication.appState.playerSession.beginOutputHandoff()
          startActivity(
              Intent(this, SpatialVideoSampleActivity::class.java).apply {
                action = Intent.ACTION_MAIN
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
              }
          )
          finish()
        }
      }
    }
  }
}

@Composable
private fun PancakeScreen(appState: ViriViriAppState, onReturnToImmersive: () -> Unit) {
  val state by appState.state.collectAsState()
  Column(
      modifier = Modifier.fillMaxSize().background(Color(0xFF102025)).padding(20.dp),
      verticalArrangement = Arrangement.spacedBy(16.dp),
  ) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
      Text("ViriViri", style = MaterialTheme.typography.h5, color = Color.White)
      Button(onClick = onReturnToImmersive) { Text("Return to immersive") }
    }
    Box(modifier = Modifier.fillMaxSize()) { RecommendationContent(state, appState, showPlayer = true) }
  }
}
