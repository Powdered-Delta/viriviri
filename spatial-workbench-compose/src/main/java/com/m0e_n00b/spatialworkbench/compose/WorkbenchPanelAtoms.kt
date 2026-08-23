package com.m0e_n00b.spatialworkbench.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.Button
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.Divider
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.m0e_n00b.spatialworkbench.core.CinemaColorRole
import com.m0e_n00b.spatialworkbench.core.CinemaPalette

/** Semantic visual tokens shared by every atom inside one Workbench rail. */
data class WorkbenchPanelStyle(
    val background: Color,
    val surface: Color,
    val surfaceStrong: Color,
    val border: Color,
    val text: Color,
    val secondaryText: Color,
    val accent: Color,
    val accentContent: Color,
    val horizontalPadding: Dp = 16.dp,
    val sectionSpacing: Dp = 12.dp,
) {
  companion object {
    fun fromPalette(palette: CinemaPalette): WorkbenchPanelStyle =
        WorkbenchPanelStyle(
            background = palette.composeColor(CinemaColorRole.BACKGROUND),
            surface = palette.composeColor(CinemaColorRole.SURFACE),
            surfaceStrong = palette.composeColor(CinemaColorRole.SECONDARY_BUTTON),
            border = palette.composeColor(CinemaColorRole.BORDER),
            text = palette.composeColor(CinemaColorRole.NORMAL_TEXT),
            secondaryText = palette.composeColor(CinemaColorRole.SECONDARY_TEXT),
            accent = palette.composeColor(CinemaColorRole.PRIMARY_BUTTON),
            accentContent = palette.composeColor(CinemaColorRole.PRIMARY_BUTTON_LABEL),
        )
  }
}

val DefaultWorkbenchPanelStyle = WorkbenchPanelStyle.fromPalette(CinemaPalette.DARK)

@Composable
fun WorkbenchPanelShell(
    style: WorkbenchPanelStyle = DefaultWorkbenchPanelStyle,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
  Column(
      modifier = modifier.fillMaxSize().background(style.background),
      content = content,
  )
}

@Composable
fun WorkbenchSection(
    style: WorkbenchPanelStyle = DefaultWorkbenchPanelStyle,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
  Column(
      modifier = modifier.fillMaxWidth().padding(horizontal = style.horizontalPadding, vertical = 12.dp),
      verticalArrangement = Arrangement.spacedBy(style.sectionSpacing),
      content = content,
  )
  Divider(color = style.border.copy(alpha = 0.58f))
}

@Composable
fun WorkbenchTitle(
    text: String,
    style: WorkbenchPanelStyle = DefaultWorkbenchPanelStyle,
    modifier: Modifier = Modifier,
    maxLines: Int = 3,
) {
  Text(
      text = text,
      color = style.text,
      fontSize = 18.sp,
      fontWeight = FontWeight.Bold,
      maxLines = maxLines,
      overflow = TextOverflow.Ellipsis,
      modifier = modifier,
  )
}

@Composable
fun WorkbenchSecondaryText(
    text: String,
    style: WorkbenchPanelStyle = DefaultWorkbenchPanelStyle,
    modifier: Modifier = Modifier,
    maxLines: Int = 4,
) {
  Text(
      text = text,
      color = style.secondaryText,
      fontSize = 12.sp,
      maxLines = maxLines,
      overflow = TextOverflow.Ellipsis,
      modifier = modifier,
  )
}

@Composable
fun WorkbenchActionStrip(
    actions: List<WorkbenchActionItem>,
    style: WorkbenchPanelStyle = DefaultWorkbenchPanelStyle,
    modifier: Modifier = Modifier,
) {
  Row(
      modifier = modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceEvenly,
      verticalAlignment = Alignment.CenterVertically,
  ) {
    actions.forEach { action ->
      Column(horizontalAlignment = Alignment.CenterHorizontally) {
        IconButton(onClick = action.onClick, enabled = action.enabled, modifier = Modifier.size(40.dp)) {
          Icon(
              imageVector = action.icon,
              contentDescription = action.label,
              tint =
                  when {
                    !action.enabled -> style.secondaryText.copy(alpha = 0.45f)
                    action.selected -> style.accent
                    else -> style.secondaryText
                  },
          )
        }
        Text(
            text = action.value,
            color = if (action.selected) style.accent else style.secondaryText,
            fontSize = 10.sp,
            maxLines = 1,
        )
      }
    }
  }
}

data class WorkbenchActionItem(
    val label: String,
    val value: String,
    val icon: ImageVector,
    val enabled: Boolean = true,
    val selected: Boolean = false,
    val onClick: () -> Unit = {},
)

@Composable
fun WorkbenchCreatorRow(
    name: String,
    detail: String,
    style: WorkbenchPanelStyle = DefaultWorkbenchPanelStyle,
    enabled: Boolean = true,
    onClick: () -> Unit = {},
) {
  Row(
      modifier =
          Modifier.fillMaxWidth()
              .clickable(enabled = enabled, onClick = onClick)
              .padding(horizontal = style.horizontalPadding, vertical = 12.dp),
      verticalAlignment = Alignment.CenterVertically,
  ) {
    Box(
        modifier = Modifier.size(36.dp).background(style.accent),
        contentAlignment = Alignment.Center,
    ) {
      Text(name.take(1), color = style.accentContent, fontWeight = FontWeight.Bold)
    }
    Column(modifier = Modifier.padding(start = 10.dp)) {
      Text(name, color = style.text, fontWeight = FontWeight.Bold, maxLines = 1)
      Text(detail, color = style.secondaryText, fontSize = 11.sp, maxLines = 1)
    }
  }
  Divider(color = style.border.copy(alpha = 0.58f))
}

@Composable
fun WorkbenchFooterAction(
    label: String,
    icon: ImageVector,
    style: WorkbenchPanelStyle = DefaultWorkbenchPanelStyle,
    onClick: () -> Unit,
) {
  Button(
      onClick = onClick,
      modifier = Modifier.fillMaxWidth().height(54.dp),
      contentPadding = PaddingValues(horizontal = style.horizontalPadding),
      colors = ButtonDefaults.buttonColors(backgroundColor = style.surfaceStrong, contentColor = style.text),
  ) {
    Icon(icon, contentDescription = null)
    Text(label, modifier = Modifier.padding(start = 8.dp))
    Spacer(Modifier.weight(1f))
  }
}

@Composable
fun WorkbenchFullHeightCollapse(
    title: String,
    style: WorkbenchPanelStyle = DefaultWorkbenchPanelStyle,
    onCollapse: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
  Column(modifier = Modifier.fillMaxSize().background(style.background)) {
    Button(
        onClick = onCollapse,
        modifier = Modifier.fillMaxWidth().height(48.dp),
        colors = ButtonDefaults.buttonColors(backgroundColor = style.surface, contentColor = style.text),
        contentPadding = PaddingValues(horizontal = style.horizontalPadding),
    ) {
      Text(title, fontWeight = FontWeight.Bold)
      Spacer(Modifier.weight(1f))
      Icon(Icons.Default.ExpandMore, contentDescription = stringResource(R.string.common_collapse))
    }
    content()
  }
}
