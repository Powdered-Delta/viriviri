package com.m0e_n00b.viriviri

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.m0e_n00b.spatialworkbench.compose.SearchActions
import com.m0e_n00b.spatialworkbench.compose.SearchCandidateItem
import com.m0e_n00b.spatialworkbench.compose.SearchCandidateModeItem
import com.m0e_n00b.spatialworkbench.compose.SearchCandidateModeSwitcher
import com.m0e_n00b.spatialworkbench.compose.SearchCandidateStrip
import com.m0e_n00b.spatialworkbench.compose.SearchInputMethodBoard
import com.m0e_n00b.spatialworkbench.compose.SearchKeyItem
import com.m0e_n00b.spatialworkbench.compose.SearchQueryField
import com.m0e_n00b.spatialworkbench.compose.SpatialPanelShell
import com.m0e_n00b.spatialworkbench.compose.SpatialPanelShellStyle

@Composable
internal fun SearchInputPanel(
    session: SearchInputSession,
    method: SearchInputMethod,
    onSystemTextChanged: (String) -> Unit,
    onInputAction: (SearchInputAction) -> Unit,
    onClear: () -> Unit,
    onSearch: () -> Unit,
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

  SpatialPanelShell(
      style = SpatialPanelShellStyle(contentPadding = 0.dp, sectionSpacing = 6.dp),
      header = {
        SearchQueryField(
            value = session.committedText,
            onValueChange = onSystemTextChanged,
            modifier = Modifier.fillMaxWidth(),
        )
      },
      mainArea = {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
          androidx.compose.material.Text(
              text =
                  if (session.composition.isBlank()) {
                    " "
                  } else {
                    "${method.displayName}：${session.composition}"
                  },
              modifier = Modifier.height(20.dp),
              color = Color(0xFFCFD8DC),
          )
          SearchCandidateModeSwitcher(
              modes =
                  listOf(
                      SearchCandidateModeItem(SearchCandidateMode.PHRASE.name, "词组"),
                      SearchCandidateModeItem(SearchCandidateMode.SINGLE_CHARACTER.name, "单字"),
                  ),
              selectedId = session.candidateMode.name,
              onSelect = { mode ->
                onInputAction(
                    SearchInputAction.SetCandidateMode(
                        SearchCandidateMode.valueOf(mode.id),
                    )
                )
              },
          )
          SearchCandidateStrip(
              candidates =
                  session.candidates.map { candidate ->
                    SearchCandidateItem(candidate.value, candidate.label)
                  },
              onSelect = { candidate ->
                onInputAction(SearchInputAction.SelectCandidate(candidate.id))
              },
          )
          SearchInputMethodBoard(
              rows = keyboardRows,
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
          )
        }
      },
      footer = {
        SearchActions(
            onBackspace = { onInputAction(SearchInputAction.Backspace) },
            onClear = onClear,
            onSearch = onSearch,
            modifier = Modifier.fillMaxWidth(),
        )
      },
  )
}
