package com.m0e_n00b.viriviri

import com.m0e_n00b.spatialworkbench.core.PlaybackCanvas

data class ImmersiveBrowseSession(
    val baselineVideoId: String? = null,
    val isActive: Boolean = false,
)

data class ImmersiveBrowseSessionTransition(
    val session: ImmersiveBrowseSession,
    val returnToPlayback: Boolean = false,
)

internal object ImmersiveBrowseSessionReducer {
  fun open(selectedVideoId: String?): ImmersiveBrowseSession =
      ImmersiveBrowseSession(baselineVideoId = selectedVideoId, isActive = true)

  fun cancel(session: ImmersiveBrowseSession): ImmersiveBrowseSessionTransition =
      if (session.isActive) {
        ImmersiveBrowseSessionTransition(session = ImmersiveBrowseSession(), returnToPlayback = true)
      } else {
        ImmersiveBrowseSessionTransition(session = session)
      }

  // UX: returns to playback only when the user actually selects a video while
  // browsing. Opening Search/Browse over an already-playing video keeps the
  // destination at VIEWER but is not a selection, so it must not close the
  // workspace. A genuine selection is a destination transition into VIEWER
  // (previousDestination != VIEWER).
  fun onAppState(
      session: ImmersiveBrowseSession,
      canvas: PlaybackCanvas,
      destination: ViriViriDestination,
      previousDestination: ViriViriDestination,
  ): ImmersiveBrowseSessionTransition =
      if (
          session.isActive &&
              canvas == PlaybackCanvas.BROWSE &&
              previousDestination != ViriViriDestination.VIEWER &&
              destination == ViriViriDestination.VIEWER
      ) {
        ImmersiveBrowseSessionTransition(session = ImmersiveBrowseSession(), returnToPlayback = true)
      } else {
        ImmersiveBrowseSessionTransition(session = session)
      }
}
