package com.m0e_n00b.viriviri

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.filled.Comment
import androidx.compose.material.icons.filled.MonetizationOn
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.ThumbDown
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.m0e_n00b.spatialworkbench.compose.DefaultWorkbenchPanelStyle
import com.m0e_n00b.spatialworkbench.compose.WorkbenchActionItem
import com.m0e_n00b.spatialworkbench.compose.WorkbenchActionStrip
import com.m0e_n00b.spatialworkbench.compose.WorkbenchCreatorRow
import com.m0e_n00b.spatialworkbench.compose.WorkbenchFooterAction
import com.m0e_n00b.spatialworkbench.compose.WorkbenchFullHeightCollapse
import com.m0e_n00b.spatialworkbench.compose.WorkbenchPanelShell
import com.m0e_n00b.spatialworkbench.compose.WorkbenchPanelStyle
import com.m0e_n00b.spatialworkbench.compose.WorkbenchSecondaryText
import com.m0e_n00b.spatialworkbench.compose.WorkbenchSection
import com.m0e_n00b.spatialworkbench.compose.WorkbenchTitle
import com.m0e_n00b.spatialworkbench.core.CinemaPalette

@Composable
internal fun ImmersiveLeftPanel(
    appState: ViriViriAppState = ViriViriApplication.appState,
    palette: CinemaPalette = CinemaPalette.DARK,
) {
  val state by appState.state.collectAsState()
  // UX: the original angled left panel is always Detail; Search and VideoList belong to CenterContentPanel.
  ImmersiveVideoDetailPanel(selected = state.selected, palette = palette)
}

@Composable
private fun ImmersiveVideoDetailPanel(
    selected: Recommendation?,
    palette: CinemaPalette,
) {
  val style = WorkbenchPanelStyle.fromPalette(palette)
  var commentsOpen by rememberSaveable { mutableStateOf(false) }

  Box(modifier = Modifier.fillMaxSize()) {
    // UX: the left panel is split into a scrollable body and one fixed comment footer; it has no header band.
    WorkbenchPanelShell(style = style) {
      Column(
          modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
      ) {
        WorkbenchSection(style = style) {
          WorkbenchTitle(selected?.title ?: "未选择视频", style = style)
          WorkbenchSecondaryText(videoMetrics(selected), style = style)
          WorkbenchActionStrip(
              actions =
                  listOf(
                      WorkbenchActionItem(
                          label = "点赞暂不可用",
                          value = compactCount(selected?.likeCount),
                          icon = Icons.Default.ThumbUp,
                          enabled = false,
                      ),
                      WorkbenchActionItem(
                          label = "投币暂不可用",
                          value = "--",
                          icon = Icons.Default.MonetizationOn,
                          enabled = false,
                      ),
                      WorkbenchActionItem(
                          label = "收藏暂不可用",
                          value = "--",
                          icon = Icons.Default.Star,
                          enabled = false,
                      ),
                  ),
              style = style,
          )
        }
        WorkbenchCreatorRow(
            name = selected?.authorName ?: "未知作者",
            detail = "作者投稿入口暂不可用",
            style = style,
            enabled = false,
        )
        WorkbenchSection(style = style) {
          WorkbenchSecondaryText("暂无视频简介", style = style)
        }
      }
      WorkbenchFooterAction(
          label = "评论",
          icon = Icons.Default.Comment,
          style = style,
          onClick = { commentsOpen = true },
      )
    }

    if (commentsOpen) {
      CommentsUnavailableCollapse(style = style, onCollapse = { commentsOpen = false })
    }
  }
}

@Composable
private fun CommentsUnavailableCollapse(
    style: WorkbenchPanelStyle = DefaultWorkbenchPanelStyle,
    onCollapse: () -> Unit,
) {
  // UX: comments replace the complete left panel with an opaque surface; unavailable writes never report success.
  WorkbenchFullHeightCollapse(title = "评论", style = style, onCollapse = onCollapse) {
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
      Text("评论服务尚未接入", color = style.text, fontWeight = FontWeight.Bold)
      WorkbenchSecondaryText(
          text = "回复、点赞和点踩需要登录与已验证的服务接口。",
          style = style,
          modifier = Modifier.padding(top = 8.dp),
      )
      // UX: disabled controls preserve the approved left-reply/right-reaction geometry without faking writes.
      Row(
          modifier = Modifier.fillMaxWidth().padding(top = 18.dp),
          verticalAlignment = Alignment.CenterVertically,
      ) {
        IconButton(onClick = {}, enabled = false) {
          Icon(Icons.AutoMirrored.Filled.Reply, contentDescription = "回复暂不可用")
        }
        Spacer(Modifier.weight(1f))
        IconButton(onClick = {}, enabled = false) {
          Icon(Icons.Default.ThumbDown, contentDescription = "点踩暂不可用")
        }
        IconButton(onClick = {}, enabled = false) {
          Icon(Icons.Default.ThumbUp, contentDescription = "点赞暂不可用")
        }
      }
    }
  }
}

private fun videoMetrics(selected: Recommendation?): String {
  if (selected == null) return "暂无播放数据"
  return listOfNotNull(
          selected.viewCount?.let { "播放 ${compactCount(it)}" },
          selected.durationSeconds?.let { formatTransportTimecode(it * 1_000L) },
      )
      .ifEmpty { listOf("暂无播放数据") }
      .joinToString(" · ")
}

private fun compactCount(value: Long?): String =
    when {
      value == null -> "--"
      value >= 100_000_000L -> "%.1f亿".format(value / 100_000_000f)
      value >= 10_000L -> "%.1f万".format(value / 10_000f)
      else -> value.toString()
    }
