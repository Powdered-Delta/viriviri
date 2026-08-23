package com.m0e_n00b.viriviri

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.m0e_n00b.spatialworkbench.compose.CinemaInputConsole
import com.m0e_n00b.spatialworkbench.compose.CinemaInputConsoleActions
import com.m0e_n00b.spatialworkbench.compose.DefaultInputConsoleStyle
import com.m0e_n00b.spatialworkbench.compose.InputConsoleStyle
import com.m0e_n00b.spatialworkbench.compose.SearchCandidateItem
import com.m0e_n00b.spatialworkbench.compose.SearchKeyItem

@Composable
internal fun SearchInputPanel(
    session: SearchInputSession,
    method: SearchInputMethod,
    candidateExpanded: Boolean,
    onSystemTextChanged: (String) -> Unit,
    onInputAction: (SearchInputAction) -> Unit,
    onToggleCandidates: () -> Unit,
    onClear: () -> Unit,
    onSearch: () -> Unit,
    onVoice: () -> Unit = {},
    onSystemIme: () -> Unit = {},
    onDismiss: () -> Unit = {},
    style: InputConsoleStyle = DefaultInputConsoleStyle,
) {
  val layout = method.keyboardLayout(session)
  val allKeys = layout.numberRows.flatten() + layout.mainRows.flatten()
  val keyActions = allKeys.associateBy(SearchInputKey::id).mapValues { it.value.action }
  val candidateValues =
      session.candidates.mapIndexed { index, candidate ->
        "candidate-$index" to candidate.value
      }.toMap()

  fun dispatchKey(key: SearchKeyItem) {
    when (key.id) {
      "backspace" -> onInputAction(SearchInputAction.Backspace)
      "enter" -> onInputAction(SearchInputAction.CommitComposition)
      "voice" -> onVoice()
      "hide" -> onDismiss()
      else -> {
        val action = keyActions[key.id] ?: return
        onInputAction(
            if (action is SearchInputAction.PressKey) {
              action.copy(eventTimeMs = System.currentTimeMillis())
            } else {
              action
            }
        )
      }
    }
  }

  CinemaInputConsole(
      query = session.committedText,
      composition = session.composition,
      candidates =
          session.candidates.mapIndexed { index, candidate ->
            SearchCandidateItem("candidate-$index", candidate.label)
          },
      numberRows =
          layout.numberRows.map { row ->
            row.map { key -> SearchKeyItem(key.id, key.label, key.hint) }
          },
      keyboardRows =
          layout.mainRows.map { row ->
            row.map { key -> SearchKeyItem(key.id, key.label, key.hint) }
          },
      actionKeys = layout.actionKeys.map { key -> SearchKeyItem(key.id, key.label, key.hint) },
      candidateExpanded = candidateExpanded,
      onQueryChanged = onSystemTextChanged,
      onSelectCandidate = { candidate ->
        candidateValues[candidate.id]?.let { onInputAction(SearchInputAction.SelectCandidate(it)) }
      },
      onToggleCandidates = onToggleCandidates,
      onKeyPress = ::dispatchKey,
      actions =
          CinemaInputConsoleActions(
              onBackspace = { onInputAction(SearchInputAction.Backspace) },
              onClear = onClear,
              onSearch = onSearch,
              onVoice = onVoice,
              onSystemIme = onSystemIme,
              onDismiss = onDismiss,
          ),
      showQueryField = false,
      style = style,
  )
}
