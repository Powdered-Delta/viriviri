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

  fun onAppState(
      session: ImmersiveBrowseSession,
      canvas: PlaybackCanvas,
      destination: ViriViriDestination,
  ): ImmersiveBrowseSessionTransition =
      if (session.isActive && canvas == PlaybackCanvas.BROWSE && destination == ViriViriDestination.VIEWER) {
        ImmersiveBrowseSessionTransition(session = ImmersiveBrowseSession(), returnToPlayback = true)
      } else {
        ImmersiveBrowseSessionTransition(session = session)
      }
}
