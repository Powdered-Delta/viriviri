package com.m0e_n00b.viriviri

import com.m0e_n00b.spatialworkbench.core.PanelSlot

/** Adapts legacy canvas slots to the single product-level Workbench state. */
internal class ImmersiveWorkbenchHost(
    private val applyModules: (Set<WorkbenchModule>) -> Unit,
    private val trace: (String) -> Unit = {},
) {
  var state: ImmersiveWorkbenchState = ImmersiveWorkbenchState()
    private set
  private var appliedModules: Set<WorkbenchModule>? = null

  fun dispatch(event: WorkbenchEvent) {
    val previousState = state
    state = ImmersiveWorkbenchReducer.reduce(previousState, event)
    val modules = ImmersiveWorkbenchReducer.modules(state)
    if (modules == appliedModules) {
      trace("dispatch event=$event state=$state modules=$modules unchanged")
      return
    }
    appliedModules = modules
    trace("dispatch event=$event previous=$previousState state=$state modules=$modules")
    applyModules(modules)
  }

  fun applyCanvasSlots(visibleSlots: Set<PanelSlot>) {
    trace("applyCanvasSlots slots=$visibleSlots stateBefore=$state")
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
