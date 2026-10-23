package com.m0e_n00b.viriviri

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.m0e_n00b.spatialworkbench.compose.DefaultWorkbenchPanelStyle
import com.m0e_n00b.spatialworkbench.compose.WorkbenchPanelShell
import com.m0e_n00b.spatialworkbench.compose.WorkbenchSecondaryText

/** W3 contextTabs + playlistList: presentation only; AppState owns selection and requests. */
@Composable
internal fun ImmersiveRightPanel(appState: ViriViriAppState) {
  val state by appState.state.collectAsState()
  val context by appState.videoContext.collectAsState()
  val thumbnails by appState.thumbnailStates.collectAsState()
  val selected = state.selected
  var tab by rememberSaveable { mutableStateOf(0) }
  val collectionScroll = rememberSaveable(selected?.videoId, saver = LazyListState.Saver) { LazyListState() }
  val relatedScroll = rememberSaveable(selected?.videoId, saver = LazyListState.Saver) { LazyListState() }
  val style = DefaultWorkbenchPanelStyle
  WorkbenchPanelShell(style = style) {
    TabRow(selectedTabIndex = tab, backgroundColor = style.background, contentColor = style.accent) {
      listOf("合集", "相关视频").forEachIndexed { index, label ->
        Tab(selected = tab == index, onClick = { tab = index }, modifier = Modifier.height(40.dp)) {
          Text(label, color = if (tab == index) style.accent else style.secondaryText, fontSize = 14.sp)
        }
      }
    }
    if (selected == null) {
      Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        WorkbenchSecondaryText("选择视频后查看分P、合集和相关推荐")
      }
    } else {
      // Separate both lazy identity and scroll state: tab changes must not reuse item anchors.
      key(tab) {
        LazyColumn(state = if (tab == 0) collectionScroll else relatedScroll,
            modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(8.dp)) {
          if (state.isResolvingPlayback || state.error != null) {
            item("playback-status") {
              ContextStatus(
                  if (state.isResolvingPlayback) "正在加载视频…" else state.error.orEmpty(),
                  retry = if (state.isResolvingPlayback) null else appState::retrySelectedVideo)
            }
          }
          if (context.videoId != selected.videoId) {
            item { ContextStatus("正在加载…") }
          } else if (tab == 0) {
            if (context.loadingDetails) item("loading") { ContextStatus("正在加载选集…") }
            context.detailsError?.let { error ->
              item("error") { ContextStatus(error, appState::retryVideoContext) }
            }
            if (context.details.parts.size > 1) {
              item("parts-title") { ContextSectionTitle("分P · ${context.details.parts.size}") }
              items(context.details.parts, key = { "part:${it.contentId}" }) { part ->
                RequestContextThumbnail(appState, selected.coverUrl, thumbnails)
                ContextVideoRow(
                    title = "P${part.number}  ${part.title}", duration = part.durationSeconds,
                    thumbnail = thumbnails[normalizedThumbnailUrl(selected.coverUrl)],
                    selected = selected.matchesPart(part),
                    onRetryThumbnail = { appState.requestThumbnail(selected.coverUrl, retryFailed = true) },
                    onClick = { appState.selectContextPart(part.contentId) })
              }
            }
            context.details.collectionTitle?.let { title ->
              item("collection-title") {
                ContextSectionTitle("$title · ${context.details.sections.sumOf { it.videos.size }}集")
              }
            }
            context.details.sections.forEachIndexed { index, section ->
              if (context.details.sections.size > 1 && section.title.isNotBlank()) {
                item("section:$index") { ContextSectionTitle("${section.title} · ${section.videos.size}集") }
              }
              items(section.videos, key = { "collection:$index:${it.videoId}" }) { video ->
                RequestContextThumbnail(appState, video.coverUrl, thumbnails)
                ContextRecommendationRow(video, thumbnails[normalizedThumbnailUrl(video.coverUrl)],
                    selected = selected.videoId == video.videoId,
                    onRetryThumbnail = { appState.requestThumbnail(video.coverUrl, retryFailed = true) }) {
                  appState.selectContextVideo(video)
                }
              }
            }
            if (!context.loadingDetails && context.detailsError == null &&
                context.details.parts.size <= 1 && context.details.sections.isEmpty()) {
              item("empty") { ContextStatus("当前视频为单P，暂无合集") }
            }
          } else {
            if (context.loadingRelated) item("loading") { ContextStatus("正在加载相关推荐…") }
            context.relatedError?.let { error ->
              item("error") { ContextStatus(error, appState::retryVideoContext) }
            }
            items(context.related, key = { it.videoId }) { video ->
              RequestContextThumbnail(appState, video.coverUrl, thumbnails)
              ContextRecommendationRow(video, thumbnails[normalizedThumbnailUrl(video.coverUrl)],
                  onRetryThumbnail = { appState.requestThumbnail(video.coverUrl, retryFailed = true) }) {
                appState.selectContextVideo(video)
              }
            }
            if (!context.loadingRelated && context.relatedError == null && context.related.isEmpty()) {
              item("empty") { ContextStatus("暂无相关推荐") }
            }
          }
        }
      }
    }
  }
}

/** Re-entering a lazy row also repairs a UI snapshot/cache miss. No work per recomposition. */
@Composable
private fun RequestContextThumbnail(
    appState: ViriViriAppState,
    coverUrl: String?,
    thumbnails: Map<String, ThumbnailState>,
) {
  val url = normalizedThumbnailUrl(coverUrl)
  val missing = thumbnails[url] == null
  LaunchedEffect(url, missing) { if (missing) appState.requestThumbnail(url) }
}

/** Both related and collection results expose enough context to choose a video. */
@Composable
private fun ContextRecommendationRow(
    video: Recommendation,
    thumbnail: ThumbnailState?,
    selected: Boolean = false,
    onRetryThumbnail: () -> Unit,
    onClick: () -> Unit,
) {
  val style = DefaultWorkbenchPanelStyle
  Row(
      modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp)
          .clip(RoundedCornerShape(8.dp)).background(if (selected) style.surfaceStrong else style.surface)
          .clickable(onClick = onClick).padding(8.dp).heightIn(min = 88.dp),
      horizontalArrangement = Arrangement.spacedBy(10.dp),
      verticalAlignment = Alignment.CenterVertically,
  ) {
    Box(Modifier.width(112.dp).aspectRatio(16f / 9f)
        .clip(RoundedCornerShape(4.dp)).background(style.border),
        contentAlignment = Alignment.Center) {
      ContextThumbnail(thumbnail, normalizedThumbnailUrl(video.coverUrl) != null, onRetryThumbnail)
      video.durationSeconds?.takeIf { it > 0 }?.let { duration ->
        Text(formatTransportTimecode(duration * 1000L), color = style.text, fontSize = 10.sp,
            modifier = Modifier.align(Alignment.BottomEnd).padding(3.dp)
                .background(style.background.copy(alpha = .9f), RoundedCornerShape(3.dp))
                .padding(horizontal = 4.dp, vertical = 1.dp))
      }
    }
    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
      Text(video.title, color = if (selected) style.accent else style.text, fontSize = 14.sp, lineHeight = 19.sp,
          fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis)
      Text("${if (selected) "正在播放 · " else ""}UP · ${video.authorName.takeUnless { it.isBlank() || it == "Unknown author" } ?: "未知UP主"}",
          color = style.secondaryText, fontSize = 12.sp, maxLines = 1,
          overflow = TextOverflow.Ellipsis)
      Text("${formatMetric(video.viewCount, "播放")} · ${formatMetric(video.danmakuCount, "弹幕")}",
          color = style.secondaryText, fontSize = 11.sp, maxLines = 1,
          overflow = TextOverflow.Ellipsis)
    }
  }
}

@Composable
private fun ContextThumbnail(state: ThumbnailState?, hasCover: Boolean, onRetry: () -> Unit) {
  val style = DefaultWorkbenchPanelStyle
  when (state) {
    is ThumbnailState.Ready -> Image(state.bitmap.asImageBitmap(), null,
        contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
    ThumbnailState.Failed -> Box(Modifier.fillMaxSize().clickable(onClickLabel = "重试封面", onClick = onRetry),
        contentAlignment = Alignment.Center) {
      Text("封面失败\n点击重试", color = style.secondaryText, fontSize = 10.sp)
    }
    else -> Text(if (hasCover) "加载中…" else "暂无封面",
        color = style.secondaryText, fontSize = 10.sp)
  }
}

@Composable
private fun ContextSectionTitle(title: String) {
  Text(title, color = DefaultWorkbenchPanelStyle.secondaryText, fontSize = 12.sp,
      modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), maxLines = 2,
      overflow = TextOverflow.Ellipsis)
}

@Composable
private fun ContextStatus(text: String, retry: (() -> Unit)? = null) {
  Column(Modifier.fillMaxWidth().padding(8.dp)) {
    WorkbenchSecondaryText(text)
    retry?.let { TextButton(onClick = it) { Text("重试") } }
  }
}

@Composable
private fun ContextVideoRow(
    title: String,
    duration: Int?,
    thumbnail: ThumbnailState?,
    selected: Boolean,
    onRetryThumbnail: () -> Unit,
    onClick: () -> Unit,
) {
  val style = DefaultWorkbenchPanelStyle
  Row(modifier = Modifier.fillMaxWidth().height(56.dp)
      .background(if (selected) style.surfaceStrong else style.surface)
      .clickable(onClick = onClick).padding(horizontal = 4.dp),
      verticalAlignment = Alignment.CenterVertically) {
    Box(Modifier.size(48.dp).background(style.border), contentAlignment = Alignment.Center) {
      ContextThumbnail(thumbnail, thumbnail != null, onRetryThumbnail)
    }
    Text(title, color = if (selected) style.accent else style.text, fontSize = 13.sp,
        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
        modifier = Modifier.weight(1f).padding(horizontal = 8.dp), maxLines = 1,
        overflow = TextOverflow.Ellipsis)
    Text(duration?.let { formatTransportTimecode(it * 1000L) } ?: "--:--",
        color = style.secondaryText, fontSize = 10.sp)
  }
  Spacer(Modifier.height(2.dp))
}
