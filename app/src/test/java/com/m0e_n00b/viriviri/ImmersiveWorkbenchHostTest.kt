package com.m0e_n00b.viriviri

import com.m0e_n00b.spatialworkbench.core.PanelSlot
import org.junit.Assert.assertEquals
import org.junit.Test

class ImmersiveWorkbenchHostTest {
  @Test
  fun legacyCanvasSlotsMapToOneWorkbenchContentModule() {
    val applied = mutableListOf<Set<WorkbenchModule>>()
    val host = ImmersiveWorkbenchHost(applied::add)

    host.applyCanvasSlots(setOf(PanelSlot.MEDIA_STAGE, PanelSlot.BROWSE))

    assertEquals(WorkbenchContent.BROWSE, host.state.content)
    assertEquals(
        setOf(
            WorkbenchModule.NAVIGATION,
            WorkbenchModule.TRANSPORT,
            WorkbenchModule.DETAIL_RAIL,
            WorkbenchModule.CENTER_CONTENT,
            WorkbenchModule.VIDEO_CONTEXT,
        ),
        applied.single(),
    )
  }

  @Test
  fun unchangedModuleSetsAreNotAppliedRepeatedly() {
    val applied = mutableListOf<Set<WorkbenchModule>>()
    val host = ImmersiveWorkbenchHost(applied::add)

    host.applyCanvasSlots(setOf(PanelSlot.TRANSPORT))
    host.applyCanvasSlots(setOf(PanelSlot.TRANSPORT))

    assertEquals(1, applied.size)
  }

  @Test
  fun quietCanvasDismissesTheWholeWorkbench() {
    val applied = mutableListOf<Set<WorkbenchModule>>()
    val host = ImmersiveWorkbenchHost(applied::add)
    host.applyCanvasSlots(setOf(PanelSlot.TRANSPORT))
    host.applyCanvasSlots(setOf(PanelSlot.MEDIA_STAGE))

    assertEquals(ImmersiveWorkbenchState(), host.state)
    assertEquals(emptySet<WorkbenchModule>(), applied.last())
  }
}
