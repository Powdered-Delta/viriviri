package com.m0e_n00b.spatialworkbench.compose

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.m0e_n00b.spatialworkbench.core.CinemaColorRole
import com.m0e_n00b.spatialworkbench.core.CinemaPalette
import com.m0e_n00b.spatialworkbench.core.TransientMessage
import com.m0e_n00b.spatialworkbench.core.TransientMessageEvent
import com.m0e_n00b.spatialworkbench.core.TransientMessageSeverity
import com.m0e_n00b.spatialworkbench.core.TransientMessageState
import kotlinx.coroutines.delay

data class TransientMessageStyle(
    val background: Color,
    val content: Color,
    val action: Color,
)

fun transientMessageStyle(
    severity: TransientMessageSeverity,
    palette: CinemaPalette,
): TransientMessageStyle =
    when (severity) {
      TransientMessageSeverity.INFO ->
          TransientMessageStyle(
              background = palette.composeColor(CinemaColorRole.SURFACE),
              content = palette.composeColor(CinemaColorRole.NORMAL_TEXT),
              action = palette.composeColor(CinemaColorRole.HIGHLIGHT_TEXT),
          )
      TransientMessageSeverity.SUCCESS ->
          TransientMessageStyle(
              background = palette.composeColor(CinemaColorRole.PRIMARY_BUTTON),
              content = palette.composeColor(CinemaColorRole.PRIMARY_BUTTON_LABEL),
              action = palette.composeColor(CinemaColorRole.PRIMARY_BUTTON_LABEL),
          )
      TransientMessageSeverity.WARNING ->
          TransientMessageStyle(
              background = palette.composeColor(CinemaColorRole.HIGHLIGHT_TEXT),
              content = palette.composeColor(CinemaColorRole.BACKGROUND),
              action = palette.composeColor(CinemaColorRole.BACKGROUND),
          )
      TransientMessageSeverity.ERROR ->
          TransientMessageStyle(
              background = palette.composeColor(CinemaColorRole.DANGER),
              content = palette.composeColor(CinemaColorRole.PRIMARY_BUTTON_LABEL),
              action = palette.composeColor(CinemaColorRole.PRIMARY_BUTTON_LABEL),
          )
    }

/** Overlay-hosted, token-driven transient feedback with host-owned event dispatch. */
@Composable
fun TransientMessageHost(
    state: TransientMessageState,
    palette: CinemaPalette,
    onEvent: (TransientMessageEvent) -> Unit,
    onAction: (TransientMessage, String) -> Unit,
    modifier: Modifier = Modifier,
) {
  val message = state.current ?: return
  val style = transientMessageStyle(message.severity, palette)
  LaunchedEffect(message.id, message.durationMs) {
    delay(message.durationMs)
    onEvent(TransientMessageEvent.Advance)
  }
  Surface(
      modifier = modifier.fillMaxWidth(),
      color = style.background,
  ) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
      Text(message.text, color = style.content, modifier = Modifier.weight(1f))
      message.action?.let { action ->
        TextButton(
            onClick = {
              onAction(message, action.id)
              onEvent(TransientMessageEvent.ActionTriggered(message.id))
            },
        ) {
          Text(action.label, color = style.action)
        }
      }
      IconButton(onClick = { onEvent(TransientMessageEvent.Dismiss(message.id)) }) {
        Icon(Icons.Default.Close, contentDescription = "Dismiss", tint = style.content)
      }
    }
  }
}
