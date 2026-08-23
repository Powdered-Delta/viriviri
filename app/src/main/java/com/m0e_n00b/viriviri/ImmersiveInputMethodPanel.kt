package com.m0e_n00b.viriviri

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.m0e_n00b.spatialworkbench.compose.InputConsoleStyle
import com.m0e_n00b.spatialworkbench.core.CinemaPalette

/**
 * Standalone near-field input panel. It owns no route, player, Spatial entity, or panel lifecycle;
 * [SpatialVideoSampleActivity] owns those concerns and only hosts this Compose content.
 */
@Composable
internal fun ImmersiveInputMethodPanel(
    appState: ViriViriAppState = ViriViriApplication.appState,
    palette: CinemaPalette = CinemaPalette.DARK,
) {
  val state by appState.state.collectAsState()
  val workspace = state.searchWorkspace
  if (workspace.route != SearchWorkspaceRoute.SEARCH_EMPTY || !workspace.isKeyboardVisible) return

  Box(modifier = Modifier.fillMaxSize()) {
    SearchInputPanel(
        session = workspace.input,
        method = appState.inputMethods.methodFor(workspace.input),
        candidateExpanded = workspace.isCandidatesExpanded,
        onSystemTextChanged = appState::updateSearchQuery,
        onInputAction = appState::applySearchInputAction,
        onToggleCandidates = appState::toggleSearchCandidates,
        onClear = appState::clearSearchInput,
        onSearch = appState::submitSearch,
        onVoice = {},
        onSystemIme = {},
        onDismiss = { appState.setSearchKeyboardVisible(false) },
        style = InputConsoleStyle.fromPalette(palette),
        transparentRoot = true,
    )
  }
}
