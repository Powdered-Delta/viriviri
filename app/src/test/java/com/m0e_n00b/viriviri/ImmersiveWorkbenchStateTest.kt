package com.m0e_n00b.viriviri

import org.junit.Assert.assertEquals
import org.junit.Test

class ImmersiveWorkbenchStateTest {
  @Test
  fun configOverlaysTheCurrentWorkbenchContent() {
    val browse = ImmersiveWorkbenchReducer.reduce(ImmersiveWorkbenchState(), WorkbenchEvent.OpenBrowse)
    val configured = ImmersiveWorkbenchReducer.reduce(browse, WorkbenchEvent.OpenPlaybackConfig)

    assertEquals(WorkbenchContent.BROWSE, configured.content)
    assertEquals(
        setOf(
            WorkbenchModule.NAVIGATION,
            WorkbenchModule.TRANSPORT,
            WorkbenchModule.CONTENT_LIST,
            WorkbenchModule.PLAYBACK_CONFIG,
        ),
        ImmersiveWorkbenchReducer.modules(configured),
    )
  }

  @Test
  fun dismissClearsTheEntireWorkbenchWithoutChangingPlayerState() {
    val visible =
        ImmersiveWorkbenchReducer.reduce(
            ImmersiveWorkbenchState(content = WorkbenchContent.VIDEO_CONTEXT, visible = true),
            WorkbenchEvent.Dismiss,
        )

    assertEquals(ImmersiveWorkbenchState(), visible)
    assertEquals(emptySet<WorkbenchModule>(), ImmersiveWorkbenchReducer.modules(visible))
  }
}
