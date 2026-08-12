package com.m0e_n00b.spatialworkbench.compose

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Divider
import androidx.compose.material.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Layout-only container shared by spatial panels and their future theme renderers. */
data class SpatialPanelShellStyle(
    val background: Color = Color.Transparent,
    val contentPadding: Dp = 16.dp,
    val sectionSpacing: Dp = 8.dp,
    val showDividers: Boolean = false,
    val fillAvailableHeight: Boolean = false,
)

@Composable
fun SpatialPanelShell(
    modifier: Modifier = Modifier,
    style: SpatialPanelShellStyle = SpatialPanelShellStyle(),
    header: @Composable ColumnScope.() -> Unit = {},
    toolbar: @Composable ColumnScope.() -> Unit = {},
    mainArea: @Composable () -> Unit,
    footer: @Composable ColumnScope.() -> Unit = {},
    overlay: @Composable () -> Unit = {},
) {
  Surface(
      modifier = modifier,
      color = style.background,
  ) {
    Box(
        modifier = if (style.fillAvailableHeight) Modifier.fillMaxSize() else Modifier.fillMaxWidth()
    ) {
      Column(
          modifier =
              (if (style.fillAvailableHeight) Modifier.fillMaxSize() else Modifier.fillMaxWidth())
                  .padding(style.contentPadding),
          verticalArrangement = Arrangement.spacedBy(style.sectionSpacing),
      ) {
        header()
        if (style.showDividers) Divider()
        toolbar()
        if (style.showDividers) Divider()
        Box(
            modifier =
                if (style.fillAvailableHeight) {
                  Modifier.fillMaxWidth().weight(1f)
                } else {
                  Modifier.fillMaxWidth()
                }
        ) { mainArea() }
        if (style.showDividers) Divider()
        footer()
      }
      overlay()
    }
  }
}
