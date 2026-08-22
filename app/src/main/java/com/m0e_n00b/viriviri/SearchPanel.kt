package com.m0e_n00b.viriviri

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.m0e_n00b.spatialworkbench.compose.InputConsoleStyle
import com.m0e_n00b.spatialworkbench.compose.WorkbenchPanelStyle
import com.m0e_n00b.spatialworkbench.compose.WorkbenchSection

internal data class SearchPanelActions(
    val onQueryChanged: (String) -> Unit,
    val onInputAction: (SearchInputAction) -> Unit,
    val onClear: () -> Unit,
    val onSubmit: () -> Unit,
)

@Composable
internal fun SearchPanel(
    session: SearchInputSession,
    method: SearchInputMethod,
    actions: SearchPanelActions,
    style: WorkbenchPanelStyle,
    inputStyle: InputConsoleStyle,
    visible: Boolean,
    modifier: Modifier = Modifier,
) {
  if (!visible) return
  // UX: Search is a standardized center-content module and never owns Spatial or playback state.
  WorkbenchSection(style = style, modifier = modifier) {
    SearchInputPanel(
        session = session,
        method = method,
        onSystemTextChanged = actions.onQueryChanged,
        onInputAction = actions.onInputAction,
        onClear = actions.onClear,
        onSearch = actions.onSubmit,
        style = inputStyle,
    )
  }
}
