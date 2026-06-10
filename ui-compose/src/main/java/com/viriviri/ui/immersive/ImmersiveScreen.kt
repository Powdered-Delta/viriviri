package com.viriviri.ui.immersive

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
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.viriviri.core.model.SampleVideoCatalog
import com.viriviri.core.model.VideoSummary

@Composable
fun ImmersiveScreen(
    modifier: Modifier = Modifier,
    featuredVideo: VideoSummary = SampleVideoCatalog.videos.first(),
    onReturnToPanel: () -> Unit = {},
) {
    MaterialTheme {
        Surface(
            modifier = modifier.fillMaxSize(),
            color = Color(0xFF05070D),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ImmersiveControls(
                    video = featuredVideo,
                    onReturnToPanel = onReturnToPanel,
                    modifier = Modifier.weight(0.36f),
                )
                Spacer(modifier = Modifier.width(32.dp))
                PlaceholderCinemaSurface(modifier = Modifier.weight(0.64f))
            }
        }
    }
}

@Composable
private fun ImmersiveControls(
    video: VideoSummary,
    onReturnToPanel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = "Immersive mode",
            color = Color.White,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = video.title,
            color = Color.White.copy(alpha = 0.88f),
            style = MaterialTheme.typography.titleLarge,
        )
        Text(
            text = "This high-immersion layout is intentionally distinct from the 2D panel and is ready for a future Spatial SDK scene host.",
            color = Color.White.copy(alpha = 0.72f),
            style = MaterialTheme.typography.bodyLarge,
        )
        Button(onClick = onReturnToPanel) {
            Text("Return to 2D panel")
        }
    }
}

@Composable
private fun PlaceholderCinemaSurface(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(420.dp)
            .background(Color(0xFF121A2A)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "Spatial video surface placeholder",
            color = Color.White.copy(alpha = 0.82f),
            style = MaterialTheme.typography.titleLarge,
        )
    }
}

@Preview(widthDp = 1200, heightDp = 700)
@Composable
private fun ImmersiveScreenPreview() {
    ImmersiveScreen()
}
