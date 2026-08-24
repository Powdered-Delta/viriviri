package com.m0e_n00b.viriviri

/** Product-level state for the one immersive operation workbench. It owns no Spatial entity or media output. */
internal data class ImmersiveWorkbenchState(
    val visible: Boolean = false,
    val presentation: WorkbenchPresentation = WorkbenchPresentation.NORMAL,
    val content: WorkbenchContent = WorkbenchContent.NONE,
    val isPlaybackConfigVisible: Boolean = false,
)

internal enum class WorkbenchPresentation {
  NORMAL,
  SHORTS,
}

internal enum class WorkbenchContent {
  NONE,
  WORKBENCH_EMPTY,
  BROWSE,
  VIDEO_CONTEXT,
  FOCUS,
}

internal enum class WorkbenchModule {
  NAVIGATION,
  TRANSPORT,
  DETAIL_RAIL,
  CENTER_CONTENT,
  VIDEO_CONTEXT,
  PLAYBACK_CONFIG,
  SHORTS_ACTIONS,
}

internal sealed interface WorkbenchEvent {
  data object RevealTransport : WorkbenchEvent
  data object OpenShortsControls : WorkbenchEvent
  data object OpenBrowse : WorkbenchEvent
  data object OpenVideoContext : WorkbenchEvent
  data object OpenFocus : WorkbenchEvent
  data object OpenPlaybackConfig : WorkbenchEvent
  data object ClosePlaybackConfig : WorkbenchEvent
  data object Dismiss : WorkbenchEvent
}

internal fun shouldShowWorkbenchModule(
    module: WorkbenchModule,
    visibleModules: Set<WorkbenchModule>,
    hasDataSource: Boolean,
): Boolean =
    module in visibleModules &&
        (hasDataSource || module !in setOf(WorkbenchModule.DETAIL_RAIL, WorkbenchModule.VIDEO_CONTEXT))

internal object ImmersiveWorkbenchReducer {
  fun reduce(state: ImmersiveWorkbenchState, event: WorkbenchEvent): ImmersiveWorkbenchState =
      when (event) {
        WorkbenchEvent.RevealTransport ->
            state.copy(
                visible = true,
                presentation = WorkbenchPresentation.NORMAL,
                content = WorkbenchContent.WORKBENCH_EMPTY,
                isPlaybackConfigVisible = false,
            )
        WorkbenchEvent.OpenShortsControls -> state.copy(visible = true, presentation = WorkbenchPresentation.SHORTS)
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
      add(
          if (state.presentation == WorkbenchPresentation.NORMAL) {
            WorkbenchModule.TRANSPORT
          } else {
            WorkbenchModule.SHORTS_ACTIONS
          }
      )
      // UX: normal controls preserve angled Detail and Context rails around the single MediaStage.
      if (state.presentation == WorkbenchPresentation.NORMAL) {
        add(WorkbenchModule.DETAIL_RAIL)
        add(WorkbenchModule.VIDEO_CONTEXT)
      }
      when (state.content) {
        WorkbenchContent.WORKBENCH_EMPTY,
        WorkbenchContent.BROWSE, WorkbenchContent.FOCUS -> add(WorkbenchModule.CENTER_CONTENT)
        WorkbenchContent.VIDEO_CONTEXT -> add(WorkbenchModule.VIDEO_CONTEXT)
        WorkbenchContent.NONE -> Unit
      }
      if (state.isPlaybackConfigVisible) add(WorkbenchModule.PLAYBACK_CONFIG)
    }
  }
}
