package com.m0e_n00b.viriviri

import com.m0e_n00b.spatialworkbench.core.PanelSlot

/** Adapts legacy canvas slots to the single product-level Workbench state. */
internal class ImmersiveWorkbenchHost(
    private val applyModules: (Set<WorkbenchModule>) -> Unit,
) {
  var state: ImmersiveWorkbenchState = ImmersiveWorkbenchState()
    private set

  fun dispatch(event: WorkbenchEvent) {
    state = ImmersiveWorkbenchReducer.reduce(state, event)
    applyModules(ImmersiveWorkbenchReducer.modules(state))
  }

  fun applyCanvasSlots(visibleSlots: Set<PanelSlot>) {
    dispatch(
        when {
          PanelSlot.BROWSE in visibleSlots -> WorkbenchEvent.OpenBrowse
          PanelSlot.CONTEXT in visibleSlots -> WorkbenchEvent.OpenVideoContext
          PanelSlot.TRANSPORT in visibleSlots -> WorkbenchEvent.RevealTransport
          else -> WorkbenchEvent.Dismiss
        }
    )
  }
}
