package com.m0e_n00b.viriviri

import androidx.compose.runtime.Composable
import com.m0e_n00b.spatialworkbench.core.CinemaPalette

/**
 * Hosts the center route from SearchWorkspaceState; it does not own a second route bridge.
 *
 * Dismissal ownership: outside the Workbench, `WorkbenchOuterDismiss` (see
 * `attachOuterDismissInput`) owns dismissal. Within the center panel, only the
 * `WORKBENCH_EMPTY` route is dismissible from blank space -- the empty list is effectively a
 * background surface. In a content route (list / search / detail) blank space stays inert so a
 * stray click cannot close the Workbench mid-browse.
 */
@Composable
internal fun ImmersiveCenterContentPanel(
    appState: ViriViriAppState = ViriViriApplication.appState,
    palette: CinemaPalette = CinemaPalette.DARK,
    onVideoSelected: () -> Unit = {},
    onDismissWorkbench: () -> Unit = {},
) {
  RecommendationPanel(
      appState = appState,
      palette = palette,
      showViewerContent = false,
      onVideoSelected = onVideoSelected,
      onDismissWorkbench = onDismissWorkbench,
  )
}
