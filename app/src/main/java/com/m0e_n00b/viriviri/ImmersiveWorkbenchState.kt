package com.m0e_n00b.viriviri

/** Product-level state for the one immersive operation workbench. It owns no Spatial entity or media output. */
internal data class ImmersiveWorkbenchState(
    val visible: Boolean = false,
    val content: WorkbenchContent = WorkbenchContent.NONE,
    val isPlaybackConfigVisible: Boolean = false,
)

internal enum class WorkbenchContent {
  NONE,
  BROWSE,
  VIDEO_CONTEXT,
  FOCUS,
}

internal enum class WorkbenchModule {
  NAVIGATION,
  TRANSPORT,
  CONTENT_LIST,
  VIDEO_CONTEXT,
  PLAYBACK_CONFIG,
}

internal sealed interface WorkbenchEvent {
  data object RevealTransport : WorkbenchEvent
  data object OpenBrowse : WorkbenchEvent
  data object OpenVideoContext : WorkbenchEvent
  data object OpenFocus : WorkbenchEvent
  data object OpenPlaybackConfig : WorkbenchEvent
  data object ClosePlaybackConfig : WorkbenchEvent
  data object Dismiss : WorkbenchEvent
}

internal object ImmersiveWorkbenchReducer {
  fun reduce(state: ImmersiveWorkbenchState, event: WorkbenchEvent): ImmersiveWorkbenchState =
      when (event) {
        WorkbenchEvent.RevealTransport -> state.copy(visible = true)
        WorkbenchEvent.OpenBrowse -> state.copy(visible = true, content = WorkbenchContent.BROWSE)
        WorkbenchEvent.OpenVideoContext ->
            state.copy(visible = true, content = WorkbenchContent.VIDEO_CONTEXT)
        WorkbenchEvent.OpenFocus -> state.copy(visible = true, content = WorkbenchContent.FOCUS)
        WorkbenchEvent.OpenPlaybackConfig -> state.copy(visible = true, isPlaybackConfigVisible = true)
        WorkbenchEvent.ClosePlaybackConfig -> state.copy(isPlaybackConfigVisible = false)
        WorkbenchEvent.Dismiss -> ImmersiveWorkbenchState()
      }

  fun modules(state: ImmersiveWorkbenchState): Set<WorkbenchModule> {
    if (!state.visible) return emptySet()
    return buildSet {
      add(WorkbenchModule.NAVIGATION)
      add(WorkbenchModule.TRANSPORT)
      when (state.content) {
        WorkbenchContent.BROWSE, WorkbenchContent.FOCUS -> add(WorkbenchModule.CONTENT_LIST)
        WorkbenchContent.VIDEO_CONTEXT -> add(WorkbenchModule.VIDEO_CONTEXT)
        WorkbenchContent.NONE -> Unit
      }
      if (state.isPlaybackConfigVisible) add(WorkbenchModule.PLAYBACK_CONFIG)
    }
  }
}
