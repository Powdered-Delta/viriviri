package com.m0e_n00b.viriviri

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.m0e_n00b.spatialworkbench.compose.CinemaInputConsole
import com.m0e_n00b.spatialworkbench.compose.CinemaInputConsoleActions
import com.m0e_n00b.spatialworkbench.compose.DefaultInputConsoleStyle
import com.m0e_n00b.spatialworkbench.compose.InputConsoleStyle
import com.m0e_n00b.spatialworkbench.compose.SearchCandidateItem
import com.m0e_n00b.spatialworkbench.compose.SearchCandidateModeItem
import com.m0e_n00b.spatialworkbench.compose.SearchKeyItem

@Composable
internal fun SearchInputPanel(
    session: SearchInputSession,
    method: SearchInputMethod,
    onSystemTextChanged: (String) -> Unit,
    onInputAction: (SearchInputAction) -> Unit,
    onClear: () -> Unit,
    onSearch: () -> Unit,
    style: InputConsoleStyle = DefaultInputConsoleStyle,
) {
  val keyboardRows =
      method.keyboard(session).mapIndexed { rowIndex, row ->
        row.mapIndexed { keyIndex, key ->
          SearchKeyItem(
              id = "$rowIndex-$keyIndex",
              label = key.label,
              hint = key.hint,
          )
        }
      }
  val keyActions =
      method.keyboard(session).flatMapIndexed { rowIndex, row ->
        row.mapIndexed { keyIndex, key -> "$rowIndex-$keyIndex" to key.action }
      }.toMap()

  CinemaInputConsole(
      query = session.committedText,
      composition = session.composition,
      candidateModes =
          listOf(
              SearchCandidateModeItem(SearchCandidateMode.PHRASE.name, "词组"),
              SearchCandidateModeItem(SearchCandidateMode.SINGLE_CHARACTER.name, "单字"),
          ),
      selectedCandidateModeId = session.candidateMode.name,
      candidates =
          session.candidates.map { candidate ->
            SearchCandidateItem(candidate.value, candidate.label)
          },
      keyboardRows = keyboardRows,
      candidateExpanded = false,
      onQueryChanged = onSystemTextChanged,
      onSelectCandidateMode = { mode ->
        onInputAction(
            SearchInputAction.SetCandidateMode(
                SearchCandidateMode.valueOf(mode.id),
            )
        )
      },
      onSelectCandidate = { candidate ->
        onInputAction(SearchInputAction.SelectCandidate(candidate.id))
      },
      onToggleCandidates = {},
      onKeyPress = { key ->
        val action = keyActions.getValue(key.id)
        onInputAction(
            if (action is SearchInputAction.PressKey) {
              action.copy(eventTimeMs = System.currentTimeMillis())
            } else {
              action
            }
        )
      },
      actions =
          CinemaInputConsoleActions(
              onBackspace = { onInputAction(SearchInputAction.Backspace) },
              onClear = onClear,
              onSearch = onSearch,
          ),
      style = style,
  )
}
