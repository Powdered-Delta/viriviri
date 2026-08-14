package com.m0e_n00b.viriviri

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Button
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.Switch
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.meta.spatial.toolkit.SpatialActivityManager

private val navigationSurface = Color(0xD9152028)
private val navigationContent = Color(0xFFF1F4F7)

@Composable
fun GlobalNavigation(isMrModeDefault: Boolean) {
  val (isMrMode, setMrMode) = remember { mutableStateOf(isMrModeDefault) }
  val context = LocalContext.current
  Row(
      modifier =
          Modifier.fillMaxSize()
              .clip(RoundedCornerShape(8.dp))
              .background(navigationSurface)
              .padding(horizontal = 8.dp, vertical = 4.dp),
      horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
      verticalAlignment = Alignment.CenterVertically,
  ) {
    NavigationAction("Home") {
      SpatialActivityManager.executeOnVrActivity<SpatialVideoSampleActivity> { it.openBrowseCanvas() }
    }
    NavigationAction("Search") {
      SpatialActivityManager.executeOnVrActivity<SpatialVideoSampleActivity> { it.openBrowseCanvas() }
    }
    Text("Profile", color = navigationContent.copy(alpha = 0.55f), fontSize = 11.sp)
    Text("MR", color = navigationContent, fontSize = 11.sp)
    Switch(checked = isMrMode, onCheckedChange = { enabled ->
      setMrMode(enabled)
      SpatialActivityManager.executeOnVrActivity<SpatialVideoSampleActivity> { it.setMrMode(enabled) }
    })
  }
}

@Composable
private fun NavigationAction(label: String, onClick: () -> Unit) {
  Button(
      onClick = onClick,
      colors =
          ButtonDefaults.buttonColors(
              backgroundColor = Color.Transparent,
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
