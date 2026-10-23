package com.m0e_n00b.viriviri

import com.m0e_n00b.spatialworkbench.core.*
import org.junit.Assert.*
import org.junit.Test

class InputKeyboardLayoutTest {
  @Test fun existingGeometryAndCalculatorKeysAreStableAcrossLanguages() {
    val layouts = InputLanguage.entries.map { InputKeyboardLayout.forSession(InputSession(language = it)) }
    assertEquals(listOf(10, 9, 9, 7), layouts.first().mainRows.map { it.size })
    assertEquals(listOf("digit:7", "digit:8", "digit:9", "operator:plus"), layouts.first().numberRows.first().map { it.id })
    assertEquals(listOf("backspace", "voice", "enter", "hide"), layouts.first().actionKeys.map { it.id })
    layouts.forEach { layout ->
      assertEquals(layouts.first().mainRows.map { row -> row.map { it.id to it.widthWeight } }, layout.mainRows.map { row -> row.map { it.id to it.widthWeight } })
      assertEquals(4f, layout.mainRows.last().single { it.id == "space" }.widthWeight)
    }
    assertEquals(listOf("中", "eng", "日"), layouts.map { it.mainRows.last().first().label })
  }
  @Test fun symbolLayerAndAsciiOperatorsPreserveTheirMeaning() {
    val layout = InputKeyboardLayout.forSession(InputSession(layer = InputLayer.SYMBOLS))
    assertEquals(InputAction.Text("*"), layout.numberRows.flatten().single { it.id == "operator:multiply" }.action)
    assertEquals(InputAction.ToggleSymbols, layout.mainRows[2].last().action)
    assertEquals(4f, layout.mainRows.last().single { it.id == "space" }.widthWeight)
  }
}
