package com.m0e_n00b.spatialworkbench.compose

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
import androidx.compose.material.icons.automirrored.filled.KeyboardReturn
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Mic
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp

val DefaultInputConsoleStyle: InputConsoleStyle =
    InputConsoleStyle.fromPalette(com.m0e_n00b.spatialworkbench.core.CinemaPalette.DARK)

data class SearchCandidateItem(val id: String, val label: String)

data class SearchKeyItem(
    val id: String,
    val label: String,
    val hint: String = "",
    val widthWeight: Float = 1f,
)

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
fun SearchCandidateStrip(
    candidates: List<SearchCandidateItem>,
    onSelect: (SearchCandidateItem) -> Unit,
    modifier: Modifier = Modifier,
    style: InputConsoleStyle = DefaultInputConsoleStyle,
    candidateExpanded: Boolean = false,
    onToggleExpanded: () -> Unit = {},
    onVisibleCountChanged: (Int) -> Unit = {},
) {
  // Gboard-style collapsed band: candidates are laid out at their NATURAL width and as
  // many as fit are shown; the rest are hidden (no horizontal scroll / no fixed count).
  // A chevron appears only when some candidates overflow; the expanded panel renders the
  // hidden remainder. Shares the letter-column surface.
  val visibleCountHolder = remember { intArrayOf(-1) }
  SideEffect {
    if (visibleCountHolder[0] >= 0) onVisibleCountChanged(visibleCountHolder[0])
  }

  Surface(
      color = style.compositionBackground,
      shape = RoundedCornerShape(4.dp),
      modifier = modifier.fillMaxWidth().height(style.skin.candidateStripHeight),
  ) {
    val gap = 4.dp
    Layout(
        modifier = Modifier.fillMaxSize().padding(4.dp),
        content = {
          candidates.forEach { candidate ->
            CandidateChip(
                label = candidate.label,
                style = style,
                onClick = { onSelect(candidate) },
                modifier = Modifier.fillMaxHeight(),
            )
          }
          // Expand toggle is always composed (last child) but only placed when items
          // overflow. It is a key-sized, filled button with an arrow icon so it is easy
          // to read and tap (the old tiny glyph was hard to hit in VR).
          Surface(
              color = style.candidate.background,
              contentColor = style.candidate.content,
              shape = RoundedCornerShape(4.dp),
              modifier =
                  Modifier.width(EXPAND_BUTTON_WIDTH)
                      .fillMaxHeight()
                      .clickable { onToggleExpanded() },
          ) {
            Box(contentAlignment = Alignment.Center) {
              Icon(
                  imageVector =
                      if (candidateExpanded) Icons.Default.KeyboardArrowUp
                      else Icons.Default.KeyboardArrowDown,
                  contentDescription = if (candidateExpanded) "收起候选" else "展开候选",
                  tint = style.candidate.content,
                  modifier = Modifier.size(26.dp),
              )
            }
          }
        },
    ) { measurables, constraints ->
      val gapPx = gap.roundToPx()
      val chevronMeasurable = measurables.last()
      val chipMeasurables = measurables.dropLast(1)
      val height = if (constraints.maxHeight != Constraints.Infinity) constraints.maxHeight else 40.dp.roundToPx()
      val loose = Constraints(minWidth = 0, maxWidth = Constraints.Infinity, minHeight = 0, maxHeight = height)
      val chevron = chevronMeasurable.measure(loose)
      val chips = chipMeasurables.map { it.measure(loose) }

      val totalWidth = constraints.maxWidth
      fun fit(reservedEnd: Int): Int {
        var used = 0
        var count = 0
        for (chip in chips) {
          val needed = chip.width + if (count > 0) gapPx else 0
          if (used + needed + reservedEnd > totalWidth) break
          used += needed
          count++
        }
        return count
      }
      // First see whether everything fits without a chevron; otherwise reserve its space.
      var visible = fit(reservedEnd = 0)
      val hasHidden = visible < chips.size
      if (hasHidden) visible = fit(reservedEnd = EXPAND_BUTTON_WIDTH.roundToPx() + gapPx)
      visibleCountHolder[0] = visible

      layout(totalWidth, height) {
        var x = 0
        chips.take(visible).forEachIndexed { index, chip ->
          chip.place(if (index == 0) 0 else x, 0)
          x += chip.width + gapPx
        }
        if (hasHidden) {
          chevron.place(totalWidth - chevron.width, (height - chevron.height) / 2)
        }
      }
    }
  }
}

/** Width of the strip's expand/collapse toggle button (a key-sized, easy-to-tap target). */
private val EXPAND_BUTTON_WIDTH = 44.dp

@Composable
private fun CandidateChip(
    label: String,
    style: InputConsoleStyle,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
  Surface(
      color = style.candidate.background,
      contentColor = style.candidate.content,
      shape = RoundedCornerShape(4.dp),
      modifier = modifier.clickable(onClick = onClick),
  ) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.padding(horizontal = 12.dp).fillMaxHeight(),
    ) {
      Text(label, color = style.candidate.content, maxLines = 1, softWrap = false)
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
    // Optional overlay (expanded candidates) rendered on top of the LETTER column only.
    mainOverlay: @Composable () -> Unit = {},
) {
  if (numberRows.isEmpty() && actionKeys.isEmpty()) {
    KeyboardRows(rows = rows, onKeyPress = onKeyPress, style = style, modifier = modifier)
    return
  }

  Row(
      modifier = modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(style.skin.sectionSpacing),
  ) {
    // Number and action columns keep their natural key height (4 rows / 4 keys) so they
    // line up exactly with the letter rows and are never stretched taller than the keys.
    InputConsoleZone(
        background = style.numberKey.background,
        border = style.popupBorder,
        modifier = Modifier.weight(style.skin.numberColumnWeight),
    ) {
      KeyboardRows(
          rows = numberRows,
          onKeyPress = onKeyPress,
          style = style,
          keyStyle = style.numberKey,
      )
    }
    InputConsoleZone(
        background = style.compositionBackground,
        border = style.popupBorder,
        modifier = Modifier.weight(style.skin.mainColumnWeight),
    ) {
      Box {
        KeyboardRows(
            rows = rows,
            onKeyPress = onKeyPress,
            style = style,
            keyStyle = style.alphabetKey,
        )
        mainOverlay()
      }
    }
    InputConsoleZone(
        background = style.actionKey.background,
        border = style.popupBorder,
        modifier = Modifier.weight(style.skin.actionColumnWeight),
    ) {
      Column(verticalArrangement = Arrangement.spacedBy(style.skin.keyRowSpacing)) {
        actionKeys.forEach { key ->
          InputConsoleKeyButton(
              key = key,
              onClick = { onKeyPress(key) },
              style = style.actionKey,
              modifier = Modifier.fillMaxWidth(),
          )
        }
      }
    }
  }
}

@Composable
private fun InputConsoleZone(
    background: Color,
    border: Color,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
  Surface(
      color = background,
      shape = RoundedCornerShape(4.dp),
      modifier = modifier.border(1.dp, border, RoundedCornerShape(4.dp)),
  ) {
    Box(modifier = Modifier.padding(4.dp)) { content() }
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
              modifier = Modifier.weight(key.widthWeight),
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
    when (key.id) {
      "enter" ->
          Icon(
              imageVector = Icons.AutoMirrored.Filled.KeyboardReturn,
              contentDescription = key.label,
              tint = style.content,
          )
      else ->
          Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(key.label, maxLines = 1)
            if (key.hint.isNotEmpty()) Text(key.hint, maxLines = 1)
          }
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
    candidates: List<SearchCandidateItem>,
    keyboardRows: List<List<SearchKeyItem>>,
    candidateExpanded: Boolean,
    onQueryChanged: (String) -> Unit,
    onSelectCandidate: (SearchCandidateItem) -> Unit,
    onToggleCandidates: () -> Unit,
    onKeyPress: (SearchKeyItem) -> Unit,
    actions: CinemaInputConsoleActions,
    showQueryField: Boolean = true,
    transparentRoot: Boolean = false,
    numberRows: List<List<SearchKeyItem>> = emptyList(),
    actionKeys: List<SearchKeyItem> = emptyList(),
    modifier: Modifier = Modifier,
    style: InputConsoleStyle = DefaultInputConsoleStyle,
) {
  val focusRequester = remember { FocusRequester() }
  // Number of candidates that fit in the collapsed strip; the remainder show expanded.
  var visibleCandidateCount by remember { mutableStateOf(candidates.size) }
  val hiddenCandidates = candidates.drop(visibleCandidateCount.coerceAtLeast(0))
  val softwareKeyboardController = LocalSoftwareKeyboardController.current
  val requestSystemIme = {
    focusRequester.requestFocus()
    softwareKeyboardController?.show()
    actions.onSystemIme()
  }
  SpatialPanelShell(
      modifier = modifier,
      style =
          style.shell.copy(
              background = if (transparentRoot) Color.Transparent else style.shell.background,
              sectionSpacing = style.skin.sectionSpacing,
          ),
      header = {
        if (showQueryField) {
          SearchQueryField(
              value = query,
              onValueChange = onQueryChanged,
              onRequestSystemKeyboard = requestSystemIme,
              onRequestVoice = actions.onVoice,
              focusRequester = focusRequester,
              style = style,
              modifier = Modifier.fillMaxWidth(),
          )
        }
      },
      mainArea = {
        // Composition and candidate bands are width-aligned to the middle LETTER column
        // (weighted spacers stand in for the number and action columns); the number and
        // action columns only render inside the board row below, at natural key height.
        Column(verticalArrangement = Arrangement.spacedBy(style.skin.sectionSpacing)) {
          Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(style.skin.sectionSpacing),
          ) {
            if (numberRows.isNotEmpty()) Spacer(Modifier.weight(style.skin.numberColumnWeight))
            Surface(
                color = style.compositionBackground,
                shape = RoundedCornerShape(4.dp),
                modifier =
                    Modifier.weight(style.skin.mainColumnWeight)
                        .height(style.skin.compositionHeight),
            ) {
              Box(modifier = Modifier.padding(horizontal = 8.dp), contentAlignment = Alignment.CenterStart) {
                Text(text = composition.ifBlank { " " }, color = style.compositionText)
              }
            }
            if (actionKeys.isNotEmpty()) Spacer(Modifier.weight(style.skin.actionColumnWeight))
          }

          Row(
              modifier = Modifier.fillMaxWidth(),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(style.skin.sectionSpacing),
          ) {
            if (numberRows.isNotEmpty()) Spacer(Modifier.weight(style.skin.numberColumnWeight))
            SearchCandidateStrip(
                candidates = candidates,
                onSelect = onSelectCandidate,
                style = style,
                candidateExpanded = candidateExpanded,
                onToggleExpanded = onToggleCandidates,
                onVisibleCountChanged = { visibleCandidateCount = it },
                modifier = Modifier.weight(style.skin.mainColumnWeight),
            )
            if (actionKeys.isNotEmpty()) Spacer(Modifier.weight(style.skin.actionColumnWeight))
          }

          SearchInputMethodBoard(
              rows = keyboardRows,
              numberRows = numberRows,
              actionKeys = actionKeys,
              onKeyPress = onKeyPress,
              style = style,
              modifier = Modifier.fillMaxWidth(),
              mainOverlay = {
                // Expanded panel renders ONLY the candidates that did not fit in the strip,
                // as a multi-column wrapping grid (the same surface as the strip, grown to
                // cover the letter keys) — not a separate, from-scratch list.
                if (candidateExpanded && hiddenCandidates.isNotEmpty() && style.skin.expandedCandidatesCoverBoard) {
                  Surface(
                      color = style.compositionBackground,
                      contentColor = style.candidate.content,
                      shape = RoundedCornerShape(4.dp),
                      modifier =
                          Modifier.fillMaxSize()
                              .border(1.dp, style.popupBorder, RoundedCornerShape(4.dp)),
                  ) {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 84.dp),
                        modifier = Modifier.fillMaxSize().padding(4.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                      items(hiddenCandidates, key = SearchCandidateItem::id) { candidate ->
                        Surface(
                            color = style.candidate.background,
                            contentColor = style.candidate.content,
                            shape = RoundedCornerShape(4.dp),
                            modifier =
                                Modifier.height(40.dp).clickable { onSelectCandidate(candidate) },
                        ) {
                          Box(contentAlignment = Alignment.Center) {
                            Text(
                                candidate.label,
                                color = style.candidate.content,
                                maxLines = 1,
                            )
                          }
                        }
                      }
                    }
                  }
                }
              },
          )
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
