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
import com.viriviri.core.state.SurfaceHandoffMetrics

@Composable
fun ImmersiveScreen(
    modifier: Modifier = Modifier,
    featuredVideo: VideoSummary = SampleVideoCatalog.videos.first(),
    metrics: SurfaceHandoffMetrics = SurfaceHandoffMetrics(),
    videoTarget: @Composable (Modifier) -> Unit = {},
    transitionMask: @Composable (Modifier) -> Unit = {},
    onEnterPanel: () -> Unit = {},
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
                    metrics = metrics,
                    onEnterPanel = onEnterPanel,
                    modifier = Modifier.weight(0.36f),
                )
                Spacer(modifier = Modifier.width(32.dp))
                Box(
                    modifier = Modifier
                        .weight(0.64f)
                        .fillMaxWidth()
                        .height(420.dp)
                        .background(Color.Black),
                ) {
                    videoTarget(Modifier.fillMaxSize())
                    transitionMask(Modifier.fillMaxSize())
                }
            }
        }
    }
}

@Composable
private fun ImmersiveControls(
    video: VideoSummary,
    metrics: SurfaceHandoffMetrics,
    onEnterPanel: () -> Unit,
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
            text = "Shared process player: prepare ${metrics.prepareCalls}, decoder ${metrics.videoDecoderInitializations}, handoffs ${metrics.surfaceHandoffs}",
            color = Color.White.copy(alpha = 0.72f),
            style = MaterialTheme.typography.bodyLarge,
        )
        Text(
            text = "Policy: ${metrics.activePolicy.label}\n" +
                "Position: ${metrics.playbackPositionMs / 1000}s\n" +
                "Route: ${metrics.sourceTarget?.label ?: "--"} -> ${metrics.destinationTarget?.label ?: "--"}\n" +
                "Surface ready: ${metrics.destinationSurfaceReady}, first frame: ${metrics.destinationFirstFrameReady}\n" +
                "Attach: ${metrics.destinationSurfaceAttachedAfterMs.msLabel()}, first frame: ${metrics.destinationFirstFrameAfterMs.msLabel()}\n" +
                "Handoff: ${metrics.lastHandoffDurationMs.msLabel()}, unseen playback: ${metrics.playingWithoutVisibleDestinationMs.msLabel()}\n" +
                "Source: ${metrics.sourceFinishDisposition.label}, timed out: ${metrics.transitionTimedOut}",
            color = Color.White.copy(alpha = 0.72f),
            style = MaterialTheme.typography.bodyMedium,
        )
        Button(onClick = onEnterPanel) {
            Text("Enter 2D Panel")
        }
    }
}

private fun Long?.msLabel(): String = this?.let { "${it}ms" } ?: "--"

@Preview(widthDp = 1200, heightDp = 700)
@Composable
private fun ImmersiveScreenPreview() {
    ImmersiveScreen()
}
