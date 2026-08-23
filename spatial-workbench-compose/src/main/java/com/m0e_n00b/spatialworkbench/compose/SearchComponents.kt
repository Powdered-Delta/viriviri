package com.m0e_n00b.spatialworkbench.compose

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Button
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.material.TextFieldDefaults
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Mic
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp

val DefaultInputConsoleStyle: InputConsoleStyle =
    InputConsoleStyle.fromPalette(com.m0e_n00b.spatialworkbench.core.CinemaPalette.DARK)

data class SearchCandidateItem(val id: String, val label: String)

data class SearchCandidateModeItem(val id: String, val label: String)

data class SearchKeyItem(val id: String, val label: String, val hint: String = "")

@Composable
fun SearchQueryField(
    value: String,
    onValueChange: (String) -> Unit,
    onRequestSystemKeyboard: () -> Unit = {},
    label: String? = null,
    modifier: Modifier = Modifier,
    style: InputConsoleStyle = DefaultInputConsoleStyle,
    onRequestVoice: () -> Unit = {},
    focusRequester: FocusRequester? = null,
) {
  val resolvedFocusRequester = focusRequester ?: remember { FocusRequester() }
  val focusManager = LocalFocusManager.current
  OutlinedTextField(
      value = value,
      onValueChange = onValueChange,
      modifier = modifier.focusRequester(resolvedFocusRequester),
      label = { Text(label ?: stringResource(R.string.search_label)) },
      colors =
          TextFieldDefaults.outlinedTextFieldColors(
              textColor = style.compositionText,
              cursorColor = style.selectedLanguage,
              focusedBorderColor = style.selectedLanguage,
              unfocusedBorderColor = style.popupBorder,
              focusedLabelColor = style.selectedLanguage,
              unfocusedLabelColor = style.secondaryText,
              trailingIconColor = style.secondaryText,
          ),
      singleLine = true,
      keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = ImeAction.Done),
      keyboardActions =
          androidx.compose.foundation.text.KeyboardActions(onDone = { focusManager.clearFocus() }),
      trailingIcon = {
        Row {
          IconButton(onClick = onRequestVoice) {
            Icon(Icons.Default.Mic, contentDescription = stringResource(R.string.search_voice), tint = style.secondaryText)
          }
          SystemImeActionButton(
              onClick = {
                resolvedFocusRequester.requestFocus()
                onRequestSystemKeyboard()
              },
              tint = style.secondaryText,
          )
        }
      },
  )
}

@Composable
fun SearchCandidateModeSwitcher(
    modes: List<SearchCandidateModeItem>,
    selectedId: String,
    onSelect: (SearchCandidateModeItem) -> Unit,
    modifier: Modifier = Modifier,
    style: InputConsoleStyle = DefaultInputConsoleStyle,
) {
  Row(
      modifier = modifier.fillMaxWidth().height(36.dp),
      horizontalArrangement = Arrangement.spacedBy(6.dp),
  ) {
    modes.forEach { mode ->
      Button(
          onClick = { onSelect(mode) },
          modifier = Modifier.weight(1f),
          colors =
              ButtonDefaults.buttonColors(
                  backgroundColor =
                      if (mode.id == selectedId) {
                        style.candidate.selectedBackground
                      } else {
                        style.candidate.background
                      },
                  contentColor =
                      if (mode.id == selectedId) {
                        style.candidate.selectedContent
                      } else {
                        style.candidate.content
                      },
              ),
      ) {
        Text(if (mode.id == selectedId) "✓ ${mode.label}" else mode.label)
      }
    }
  }
}

@Composable
fun SearchCandidateStrip(
    candidates: List<SearchCandidateItem>,
    onSelect: (SearchCandidateItem) -> Unit,
    modifier: Modifier = Modifier,
    style: InputConsoleStyle = DefaultInputConsoleStyle,
) {
  Box(modifier = modifier.fillMaxWidth().height(style.skin.candidateStripHeight)) {
    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
      items(candidates, key = SearchCandidateItem::id) { candidate ->
        TextButton(
            onClick = { onSelect(candidate) },
            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp),
        ) {
          Text(candidate.label, color = style.candidate.content, maxLines = 1)
        }
      }
    }
  }
}

@Composable
fun SearchInputMethodBoard(
    rows: List<List<SearchKeyItem>>,
    onKeyPress: (SearchKeyItem) -> Unit,
    numberRows: List<List<SearchKeyItem>> = emptyList(),
    actionKeys: List<SearchKeyItem> = emptyList(),
    modifier: Modifier = Modifier,
    style: InputConsoleStyle = DefaultInputConsoleStyle,
) {
  if (numberRows.isEmpty() && actionKeys.isEmpty()) {
    KeyboardRows(rows = rows, onKeyPress = onKeyPress, style = style, modifier = modifier)
    return
  }

  Row(
      modifier = modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(style.skin.sectionSpacing),
  ) {
    KeyboardRows(
        rows = numberRows,
        onKeyPress = onKeyPress,
        style = style,
        keyStyle = style.numberKey,
        modifier = Modifier.weight(style.skin.numberColumnWeight),
    )
    KeyboardRows(
        rows = rows,
        onKeyPress = onKeyPress,
        style = style,
        keyStyle = style.alphabetKey,
        modifier = Modifier.weight(style.skin.mainColumnWeight),
    )
    Column(
        modifier = Modifier.weight(style.skin.actionColumnWeight).fillMaxHeight(),
        verticalArrangement = Arrangement.spacedBy(style.skin.sectionSpacing),
    ) {
      actionKeys.forEach { key ->
        InputConsoleKeyButton(
            key = key,
            onClick = { onKeyPress(key) },
            style = style.actionKey,
            modifier = Modifier.fillMaxWidth().weight(1f),
        )
      }
    }
  }
}

@Composable
private fun KeyboardRows(
    rows: List<List<SearchKeyItem>>,
    onKeyPress: (SearchKeyItem) -> Unit,
    style: InputConsoleStyle,
    keyStyle: InputConsoleKeyStyle = style.alphabetKey,
    modifier: Modifier = Modifier,
) {
  Column(
      modifier = modifier,
      verticalArrangement = Arrangement.spacedBy(style.skin.keyRowSpacing),
  ) {
    rows.forEach { row ->
      Row(
          modifier = Modifier.fillMaxWidth().weight(1f, fill = false),
          horizontalArrangement = Arrangement.spacedBy(style.skin.keyRowSpacing),
      ) {
        row.forEach { key ->
          InputConsoleKeyButton(
              key = key,
              onClick = { onKeyPress(key) },
              style = keyStyle,
              modifier = Modifier.weight(1f),
          )
        }
      }
    }
  }
}

@Composable
private fun InputConsoleKeyButton(
    key: SearchKeyItem,
    onClick: () -> Unit,
    style: InputConsoleKeyStyle,
    modifier: Modifier = Modifier,
) {
  Button(
      onClick = onClick,
      modifier = modifier.height(style.height),
      contentPadding = PaddingValues(horizontal = 3.dp, vertical = 2.dp),
      colors =
          ButtonDefaults.buttonColors(
              backgroundColor = style.background,
              contentColor = style.content,
              disabledBackgroundColor = style.disabledBackground,
              disabledContentColor = style.disabledContent,
          ),
  ) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
      Text(key.label, maxLines = 1)
      if (key.hint.isNotEmpty()) Text(key.hint, maxLines = 1)
    }
  }
}

@Composable
fun SystemImeActionButton(
    onClick: () -> Unit,
    tint: Color,
    contentDescription: String? = null,
) {
  val resolvedContentDescription = contentDescription ?: stringResource(R.string.search_system_ime)

  IconButton(onClick = onClick) {
    Icon(Icons.Default.Keyboard, contentDescription = resolvedContentDescription, tint = tint)
  }
}
@Composable
fun SearchActions(
    onBackspace: () -> Unit,
    onClear: () -> Unit,
    onSearch: () -> Unit,
    modifier: Modifier = Modifier,
    style: InputConsoleStyle = DefaultInputConsoleStyle,
    onVoice: () -> Unit = {},
    onSystemIme: () -> Unit = {},
    onDismiss: () -> Unit = {},
    focusRequester: FocusRequester? = null,
) {
  Row(
      modifier = modifier,
      horizontalArrangement = Arrangement.spacedBy(6.dp),
      verticalAlignment = Alignment.CenterVertically,
  ) {
    IconButton(onClick = onBackspace) {
      Icon(Icons.AutoMirrored.Filled.Backspace, contentDescription = stringResource(R.string.search_delete), tint = style.secondaryText)
    }
    IconButton(onClick = onVoice) {
      Icon(Icons.Default.Mic, contentDescription = stringResource(R.string.search_voice), tint = style.secondaryText)
    }
    SystemImeActionButton(
        onClick = {
          focusRequester?.requestFocus()
          onSystemIme()
        },
        tint = style.secondaryText,
    )
    IconButton(onClick = onDismiss) { Text(stringResource(R.string.search_collapse), color = style.secondaryText) }
    Button(
        onClick = onClear,
        modifier = Modifier.weight(1f),
        colors =
            ButtonDefaults.buttonColors(
                backgroundColor = style.actionKey.background,
                contentColor = style.actionKey.content,
            ),
    ) {
      Text(stringResource(R.string.search_clear_input))
    }
    Button(
        onClick = onSearch,
        modifier = Modifier.weight(1f),
        colors =
            ButtonDefaults.buttonColors(
                backgroundColor = style.alphabetKey.background,
                contentColor = style.alphabetKey.content,
            ),
    ) {
      Text(stringResource(R.string.search_submit))
    }
  }
}

@Composable
fun CinemaInputConsole(
    query: String,
    composition: String,
    candidateModes: List<SearchCandidateModeItem>,
    selectedCandidateModeId: String,
    candidates: List<SearchCandidateItem>,
    keyboardRows: List<List<SearchKeyItem>>,
    candidateExpanded: Boolean,
    onQueryChanged: (String) -> Unit,
    onSelectCandidateMode: (SearchCandidateModeItem) -> Unit,
    onSelectCandidate: (SearchCandidateItem) -> Unit,
    onToggleCandidates: () -> Unit,
    onKeyPress: (SearchKeyItem) -> Unit,
    actions: CinemaInputConsoleActions,
    numberRows: List<List<SearchKeyItem>> = emptyList(),
    actionKeys: List<SearchKeyItem> = emptyList(),
    modifier: Modifier = Modifier,
    style: InputConsoleStyle = DefaultInputConsoleStyle,
) {
  val focusRequester = remember { FocusRequester() }
  val softwareKeyboardController = LocalSoftwareKeyboardController.current
  val requestSystemIme = {
    focusRequester.requestFocus()
    softwareKeyboardController?.show()
    actions.onSystemIme()
  }
  SpatialPanelShell(
      modifier = modifier,
      style = style.shell.copy(sectionSpacing = style.skin.sectionSpacing),
      header = {
        SearchQueryField(
            value = query,
            onValueChange = onQueryChanged,
            onRequestSystemKeyboard = requestSystemIme,
            onRequestVoice = actions.onVoice,
            focusRequester = focusRequester,
            style = style,
            modifier = Modifier.fillMaxWidth(),
        )
      },
      mainArea = {
        Column(verticalArrangement = Arrangement.spacedBy(style.skin.sectionSpacing)) {
          Box(
              modifier = Modifier.fillMaxWidth().height(style.skin.compositionHeight),
          ) {
            Text(
                text = composition.ifBlank { " " },
                color = style.compositionText,
                modifier = Modifier.align(Alignment.CenterStart),
            )
          }
          SearchCandidateModeSwitcher(
              modes = candidateModes,
              selectedId = selectedCandidateModeId,
              onSelect = onSelectCandidateMode,
              style = style,
          )
          Box(modifier = Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(style.skin.sectionSpacing)) {
              Box(
                  modifier = Modifier.fillMaxWidth().height(style.skin.candidateStripHeight),
              ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                  SearchCandidateStrip(
                      candidates = candidates,
                      onSelect = onSelectCandidate,
                      style = style,
                      modifier = Modifier.weight(1f),
                  )
                  TextButton(
                      onClick = onToggleCandidates,
                      enabled = candidates.isNotEmpty(),
                      contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp),
                  ) {
                    Text(
                        if (candidateExpanded) stringResource(R.string.search_collapse)
                        else stringResource(R.string.search_expand_candidates),
                        color = style.secondaryText,
                    )
                  }
                }
              }
              SearchInputMethodBoard(
                  rows = keyboardRows,
                  numberRows = numberRows,
                  actionKeys = actionKeys,
                  onKeyPress = onKeyPress,
                  style = style,
                  modifier = Modifier.fillMaxWidth(),
              )
            }
            if (candidateExpanded && candidates.isNotEmpty() && style.skin.expandedCandidatesCoverBoard) {
              Surface(
                  color = style.popupBackground,
                  contentColor = style.popupContent,
                  shape = RoundedCornerShape(4.dp),
                  modifier =
                      Modifier.fillMaxSize()
                          .border(1.dp, style.popupBorder, RoundedCornerShape(4.dp)),
              ) {
                Column(modifier = Modifier.fillMaxSize()) {
                  Row(
                      modifier = Modifier.fillMaxWidth().height(style.skin.compositionHeight),
                      verticalAlignment = Alignment.CenterVertically,
                  ) {
                    Text(
                        text = composition.ifBlank { " " },
                        color = style.compositionText,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(
                        onClick = onToggleCandidates,
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp),
                    ) {
                      Text(stringResource(R.string.search_collapse), color = style.secondaryText)
                    }
                  }
                  LazyColumn(
                      modifier = Modifier.fillMaxWidth().weight(1f),
                  ) {
                    items(candidates, key = SearchCandidateItem::id) { candidate ->
                      TextButton(
                          onClick = { onSelectCandidate(candidate) },
                          modifier = Modifier.fillMaxWidth(),
                          contentPadding = PaddingValues(horizontal = 8.dp, vertical = 3.dp),
                      ) {
                        Text(
                            candidate.label,
                            color = style.popupContent,
                            modifier = Modifier.fillMaxWidth(),
                        )
                      }
                    }
                  }
                }
              }
            }
          }
        }
      },
      footer = {
        if (actionKeys.isEmpty()) {
          SearchActions(
              onBackspace = actions.onBackspace,
              onClear = actions.onClear,
              onSearch = actions.onSearch,
              onVoice = actions.onVoice,
              onSystemIme = requestSystemIme,
              onDismiss = actions.onDismiss,
              focusRequester = focusRequester,
              style = style,
              modifier = Modifier.fillMaxWidth(),
          )
        }
      },
  )
}
