package com.m0e_n00b.viriviri

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.Button
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.ViewModule
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.m0e_n00b.spatialworkbench.compose.WorkbenchPanelStyle

private data class VideoListFilterOption<T>(val value: T, val label: String)

@androidx.compose.runtime.Composable
internal fun VideoListFilterBar(
    state: VideoListFilterState,
    isGridView: Boolean,
    moreExpanded: Boolean,
    style: WorkbenchPanelStyle,
    onSortChanged: (VideoListSort) -> Unit,
    onDateChanged: (VideoListDateFilter) -> Unit,
    onDurationChanged: (VideoListDurationFilter) -> Unit,
    onToggleMore: () -> Unit,
    onToggleLayout: () -> Unit,
    isAtTop: Boolean,
    onTopOrRefresh: () -> Unit,
    modifier: Modifier = Modifier,
) {
  val sorts = listOf(
      VideoListFilterOption(VideoListSort.COMPREHENSIVE, stringResource(R.string.list_sort_comprehensive)),
      VideoListFilterOption(VideoListSort.LATEST, stringResource(R.string.list_sort_latest)),
      VideoListFilterOption(VideoListSort.DANMAKU, stringResource(R.string.list_sort_danmaku)),
      VideoListFilterOption(VideoListSort.FAVORITES, stringResource(R.string.list_sort_favorites)),
  )
  Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
      sorts.forEach { option ->
        FilterButton(
            label = option.label,
            selected = state.sort == option.value,
            enabled = true,
            style = style,
            onClick = { onSortChanged(option.value) },
        )
      }
      FilterButton(
          label = stringResource(R.string.list_filter_more),
          selected = state.date != VideoListDateFilter.ANY || state.duration != VideoListDurationFilter.ANY,
          enabled = true,
          style = style,
          onClick = onToggleMore,
      )
      IconButton(onClick = onTopOrRefresh) {
        Icon(
            imageVector = if (isAtTop) Icons.Default.Refresh else Icons.Default.ArrowUpward,
            contentDescription = if (isAtTop) stringResource(R.string.list_refresh) else stringResource(R.string.list_to_top),
            tint = style.text,
        )
      }
      IconButton(onClick = onToggleLayout) {
        Icon(
            imageVector = if (isGridView) Icons.AutoMirrored.Filled.ViewList else Icons.Default.ViewModule,
            contentDescription = stringResource(R.string.list_toggle_layout),
            tint = style.text,
        )
      }
    }
    if (moreExpanded || state.date != VideoListDateFilter.ANY || state.duration != VideoListDurationFilter.ANY) {
      Row(
          modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
          horizontalArrangement = Arrangement.spacedBy(4.dp),
      ) {
        listOf(
            VideoListFilterOption(VideoListDateFilter.ANY, stringResource(R.string.list_date_any)),
            VideoListFilterOption(VideoListDateFilter.TODAY, stringResource(R.string.list_date_today)),
            VideoListFilterOption(VideoListDateFilter.THIS_WEEK, stringResource(R.string.list_date_week)),
            VideoListFilterOption(VideoListDateFilter.THIS_MONTH, stringResource(R.string.list_date_month)),
        ).forEach { option ->
          FilterButton(option.label, state.date == option.value, true, style) { onDateChanged(option.value) }
        }
        listOf(
            VideoListFilterOption(VideoListDurationFilter.ANY, stringResource(R.string.list_duration_any)),
            VideoListFilterOption(VideoListDurationFilter.SHORT, stringResource(R.string.list_duration_short)),
            VideoListFilterOption(VideoListDurationFilter.MEDIUM, stringResource(R.string.list_duration_medium)),
            VideoListFilterOption(VideoListDurationFilter.LONG, stringResource(R.string.list_duration_long)),
        ).forEach { option ->
          FilterButton(option.label, state.duration == option.value, true, style) { onDurationChanged(option.value) }
        }
      }
    }
  }
}

@androidx.compose.runtime.Composable
private fun FilterButton(
    label: String,
    selected: Boolean,
    enabled: Boolean,
    style: WorkbenchPanelStyle,
    onClick: () -> Unit,
) {
  Button(
      onClick = onClick,
      enabled = enabled,
      contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 0.dp),
      colors = ButtonDefaults.buttonColors(
          backgroundColor = if (selected) style.accent else Color.Transparent,
          contentColor = if (selected) style.accentContent else style.text,
          disabledContentColor = style.secondaryText.copy(alpha = 0.45f),
          disabledBackgroundColor = Color.Transparent,
      ),
  ) { Text(label, fontSize = 11.sp) }
}
