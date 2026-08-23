package com.m0e_n00b.viriviri

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Button
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.Switch
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.meta.spatial.toolkit.SpatialActivityManager

// UX: Workbench roots are opaque; consistent transparency is applied by SpatialPanelVisibilityController.
private val navigationSurface = Color(0xFF152028)
private val navigationContent = Color(0xFFF1F4F7)

@Composable
fun GlobalNavigation(isMrModeDefault: Boolean) {
  val (isMrMode, setMrMode) = remember { mutableStateOf(isMrModeDefault) }
  val centerMode by CenterContentSession.mode.collectAsState()
  val appState by ViriViriApplication.appState.state.collectAsState()

  Column(
      modifier =
          Modifier.fillMaxSize()
              .clip(RoundedCornerShape(8.dp))
              .background(navigationSurface)
              .padding(horizontal = 8.dp, vertical = 3.dp),
      verticalArrangement = Arrangement.spacedBy(2.dp),
  ) {
    // UX: this is the Demo's GlobalNavigation role, not a route-specific button strip.
    Row(
        modifier = Modifier.fillMaxWidth().weight(1f),
        verticalAlignment = Alignment.CenterVertically,
    ) {
      NavigationTextButton(
          label = stringResource(R.string.nav_brand),
          onClick = {
            SpatialActivityManager.executeOnVrActivity<SpatialVideoSampleActivity> { it.openHomeCanvas() }
          },
      )
      Spacer(Modifier.weight(1f))
      IconButton(onClick = {
        SpatialActivityManager.executeOnVrActivity<SpatialVideoSampleActivity> { it.openSearchCanvas() }
      }) {
        Icon(Icons.Default.Search, contentDescription = stringResource(R.string.nav_search), tint = navigationContent)
      }
      IconButton(onClick = {}, enabled = false) {
        Icon(Icons.Default.AccountCircle, contentDescription = stringResource(R.string.nav_account) + "暂不可用", tint = navigationContent.copy(alpha = 0.42f))
      }
      IconButton(onClick = {}, enabled = false) {
        Icon(Icons.Default.Settings, contentDescription = stringResource(R.string.nav_settings) + "暂不可用", tint = navigationContent.copy(alpha = 0.42f))
      }
      Switch(checked = isMrMode, onCheckedChange = { enabled ->
        setMrMode(enabled)
        SpatialActivityManager.executeOnVrActivity<SpatialVideoSampleActivity> { it.setMrMode(enabled) }
      })
    }

    // UX: exactly one content-navigation state is visible: playback list, search context, or explicit Back.
    ContentNavigationSlot(
        mode = centerMode,
        query = appState.searchInput.committedText,
        onOpenVideoList = {
          SpatialActivityManager.executeOnVrActivity<SpatialVideoSampleActivity> { it.openBrowseCanvas() }
        },
        onOpenSearch = {
          SpatialActivityManager.executeOnVrActivity<SpatialVideoSampleActivity> { it.openSearchCanvas() }
        },
        onBack = {
          SpatialActivityManager.executeOnVrActivity<SpatialVideoSampleActivity> { it.closeCenterContentFromNavigation() }
        },
        modifier = Modifier.fillMaxWidth().weight(1f),
    )
  }
}

@Composable
private fun ContentNavigationSlot(
    mode: CenterContentMode,
    query: String,
    onOpenVideoList: () -> Unit,
    onOpenSearch: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
  Row(
      modifier = modifier,
      horizontalArrangement = Arrangement.Center,
      verticalAlignment = Alignment.CenterVertically,
  ) {
    when (mode) {
      CenterContentMode.PLAYBACK -> NavigationTextButton("视频列表", onOpenVideoList)
      CenterContentMode.VIDEO_LIST -> {
        IconButton(onClick = onBack) {
          Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.nav_back), tint = navigationContent)
        }
        NavigationTextButton(stringResource(R.string.nav_video_list), onOpenVideoList, selected = true)
      }
      CenterContentMode.SEARCH -> {
        IconButton(onClick = onOpenSearch) {
          Icon(Icons.Default.Search, contentDescription = stringResource(R.string.nav_search), tint = navigationContent)
        }
        Text(
            text = query.ifBlank { stringResource(R.string.nav_search) },
            color = navigationContent,
            fontSize = 11.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.width(180.dp).padding(horizontal = 6.dp),
        )
        IconButton(onClick = onBack) {
          Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.nav_back), tint = navigationContent)
        }
      }
    }
  }
}

@Composable
private fun NavigationTextButton(label: String, onClick: () -> Unit, selected: Boolean = false) {
  Button(
      onClick = onClick,
      colors =
          ButtonDefaults.buttonColors(
              backgroundColor = if (selected) navigationContent.copy(alpha = 0.22f) else Color.Transparent,
              contentColor = navigationContent,
          ),
      contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 0.dp),
  ) {
    Text(label, fontSize = 11.sp)
  }
}

class MRPanel : ComponentActivity() {
  override fun onCreate(savedInstanceBundle: Bundle?) {
    super.onCreate(savedInstanceBundle)
    setContent { GlobalNavigation(intent.getStringExtra("isMrMode").toBoolean()) }
  }
}
