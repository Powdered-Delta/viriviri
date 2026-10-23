package com.m0e_n00b.viriviri

import androidx.compose.runtime.*
import com.m0e_n00b.spatialworkbench.compose.*
import com.m0e_n00b.spatialworkbench.core.InputAction

/** Existing console appearance; actions always address the controller's current editor. */
@Composable
internal fun AppInputConsole(
    controller: AppInputController,
    style: InputConsoleStyle = DefaultInputConsoleStyle,
    transparentRoot: Boolean = false,
) {
  val state by controller.state.collectAsState()
  val layout = InputKeyboardLayout.forSession(state.session)
  val keys = (layout.numberRows.flatten() + layout.mainRows.flatten() + layout.actionKeys).associateBy { it.id }
  fun item(key: InputKey): SearchKeyItem = SearchKeyItem(
      key.id, key.label, key.hint, key.widthWeight,
      enabled = key.id != "voice" && !(state.direct && key.id == "language") &&
          (state.kind != InputFieldKind.NUMBER || key.id.startsWith("digit:") || key.id in listOf("backspace", "enter", "hide")),
  )
  CinemaInputConsole(
      query = "",
      composition = state.session.display,
      candidates = state.session.candidates.map { SearchCandidateItem(it.id, it.text) },
      numberRows = layout.numberRows.map { row -> row.map(::item) },
      keyboardRows = layout.mainRows.map { row -> row.map(::item) },
      actionKeys = layout.actionKeys.map(::item),
      candidateExpanded = state.expanded,
      onQueryChanged = {},
      onSelectCandidate = { controller.dispatch(InputAction.SelectCandidate(it.id), state.epoch) },
      onToggleCandidates = controller::toggleCandidates,
      onKeyPress = { key ->
        if (key.id == "hide") controller.hide()
        else keys[key.id]?.let { controller.dispatch(it.action, state.epoch) }
      },
      actions = CinemaInputConsoleActions(
          onBackspace = { controller.dispatch(InputAction.Backspace) },
          onClear = { controller.dispatch(InputAction.Clear) },
          onSearch = { controller.dispatch(InputAction.Submit) },
          onVoice = {},
          onSystemIme = controller::requestSystem,
          onDismiss = controller::hide,
      ),
      showQueryField = false,
      transparentRoot = transparentRoot,
      style = style,
      inputUnavailable = state.unavailable,
      onRetryInput = controller::retry,
  )
}
