package com.viriviri.ui.browse

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.viriviri.core.model.SampleVideoCatalog
import com.viriviri.core.model.VideoId
import com.viriviri.core.model.VideoSummary

@Composable
fun BrowseScreen(
    modifier: Modifier = Modifier,
    videos: List<VideoSummary> = remember { SampleVideoCatalog.videos },
    onSelectVideo: (VideoId) -> Unit = {},
    onEnterImmersive: () -> Unit = {},
) {
    MaterialTheme {
        Surface(modifier = modifier.fillMaxSize()) {
            BrowseContent(
                videos = videos,
                onSelectVideo = onSelectVideo,
                onEnterImmersive = onEnterImmersive,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BrowseContent(
    videos: List<VideoSummary>,
    onSelectVideo: (VideoId) -> Unit,
    onEnterImmersive: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(title = { Text("viriviri") })
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                text = { Text("Enter immersive") },
                onClick = onEnterImmersive,
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(videos, key = { it.id.value }) { video ->
                VideoRow(
                    video = video,
                    onClick = { onSelectVideo(video.id) },
                )
            }
        }
    }
}

@Composable
private fun VideoRow(
    video: VideoSummary,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = video.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(text = video.ownerName, style = MaterialTheme.typography.bodyMedium)
                Text(text = video.durationSeconds.durationLabel(), style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

private fun Int?.durationLabel(): String = this?.let { seconds ->
    "%d:%02d".format(seconds / 60, seconds % 60)
} ?: "--:--"

@Preview(showBackground = true, widthDp = 720, heightDp = 540)
@Composable
private fun BrowseScreenPreview() {
    BrowseScreen()
}
