package com.m0e_n00b.spatialworkbench.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.m0e_n00b.spatialworkbench.core.CinemaColorRole
import com.m0e_n00b.spatialworkbench.core.CinemaPalette
import com.m0e_n00b.spatialworkbench.core.ContentAccess
import com.m0e_n00b.spatialworkbench.core.badgeText

data class ContentAccessBadgeStyle(
    val label: String,
    val background: Color,
    val content: Color,
)

fun contentAccessBadgeStyle(
    access: ContentAccess,
    palette: CinemaPalette,
): ContentAccessBadgeStyle? {
  val label = access.badgeText() ?: return null
  return ContentAccessBadgeStyle(
      label = label,
      background = palette.composeColor(CinemaColorRole.CHARGING_BADGE),
      content = palette.composeColor(CinemaColorRole.CHARGING_BADGE_LABEL),
  )
}

/** Compact, non-interactive marker intended to be overlaid on a media thumbnail. */
@Composable
fun ContentAccessBadge(
    access: ContentAccess,
    palette: CinemaPalette,
    modifier: Modifier = Modifier,
) {
  val style = contentAccessBadgeStyle(access, palette) ?: return
  Text(
      text = style.label,
      color = style.content,
      modifier = modifier.background(style.background).padding(horizontal = 6.dp, vertical = 2.dp),
  )
}
