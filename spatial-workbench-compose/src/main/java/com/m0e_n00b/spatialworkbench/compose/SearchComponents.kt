package com.m0e_n00b.spatialworkbench.compose

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.Button
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Text
import androidx.compose.material.TextFieldDefaults
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions

data class SearchCandidateItem(val id: String, val label: String)

data class SearchCandidateModeItem(val id: String, val label: String)

data class SearchKeyItem(val id: String, val label: String, val hint: String = "")

@Composable
fun SearchQueryField(
    value: String,
    onValueChange: (String) -> Unit,
    onRequestSystemKeyboard: () -> Unit = {},
    label: String = "搜索词",
    modifier: Modifier = Modifier,
) {
  val focusRequester = remember { FocusRequester() }
  val focusManager = LocalFocusManager.current
  OutlinedTextField(
      value = value,
      onValueChange = onValueChange,
      modifier = modifier.focusRequester(focusRequester),
      label = { Text(label) },
      colors =
          TextFieldDefaults.outlinedTextFieldColors(
              textColor = Color(0xFFF1F4F7),
              cursorColor = Color(0xFF80CBC4),
              focusedBorderColor = Color(0xFF80CBC4),
              unfocusedBorderColor = Color(0xFF8FA3A8),
              focusedLabelColor = Color(0xFF80CBC4),
              unfocusedLabelColor = Color(0xFFCFD8DC),
              trailingIconColor = Color(0xFFCFD8DC),
          ),
      singleLine = true,
      keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
      keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
      trailingIcon = {
        IconButton(
            onClick = {
              focusRequester.requestFocus()
              onRequestSystemKeyboard()
            }
        ) {
          Icon(Icons.Default.Keyboard, contentDescription = "呼出系统键盘")
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
) {
  Row(
      modifier = modifier.fillMaxWidth().height(36.dp),
      horizontalArrangement = Arrangement.spacedBy(6.dp),
  ) {
    modes.forEach { mode ->
      Button(
          onClick = { onSelect(mode) },
          modifier = Modifier.weight(1f),
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
) {
  Box(modifier = modifier.fillMaxWidth().height(48.dp)) {
    if (candidates.isNotEmpty()) {
      LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        items(candidates, key = SearchCandidateItem::id) { candidate ->
          Button(onClick = { onSelect(candidate) }) { Text(candidate.label) }
        }
      }
    }
  }
}

@Composable
fun SearchInputMethodBoard(
    rows: List<List<SearchKeyItem>>,
    onKeyPress: (SearchKeyItem) -> Unit,
    modifier: Modifier = Modifier,
) {
  Column(
      modifier = modifier,
      verticalArrangement = Arrangement.spacedBy(6.dp),
  ) {
    rows.forEach { row ->
      Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(6.dp),
      ) {
        row.forEach { key ->
          Button(
              onClick = { onKeyPress(key) },
              modifier = Modifier.weight(1f).height(44.dp),
          ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
              Text(key.label)
              if (key.hint.isNotEmpty()) Text(key.hint)
            }
          }
        }
      }
    }
  }
}

@Composable
fun SearchActions(
    onBackspace: () -> Unit,
    onClear: () -> Unit,
    onSearch: () -> Unit,
    modifier: Modifier = Modifier,
) {
  Row(
      modifier = modifier,
      horizontalArrangement = Arrangement.spacedBy(6.dp),
      verticalAlignment = Alignment.CenterVertically,
  ) {
    IconButton(onClick = onBackspace) {
      Icon(Icons.AutoMirrored.Filled.Backspace, contentDescription = "删除")
    }
    Button(onClick = onClear, modifier = Modifier.weight(1f)) { Text("清空输入") }
    Button(onClick = onSearch, modifier = Modifier.weight(1f)) { Text("确定搜索") }
  }
}
