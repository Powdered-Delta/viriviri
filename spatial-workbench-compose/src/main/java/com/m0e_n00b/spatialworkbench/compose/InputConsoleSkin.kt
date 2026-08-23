package com.m0e_n00b.spatialworkbench.compose

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Resource-free input-console skin descriptor.
 *
 * It follows the keyboard-skin separation used by traditional IMEs: input methods provide key
 * semantics, a skin provides geometry, and [InputConsoleStyle] supplies palette-derived visuals.
 * Bitmap, animation, and sound bindings deliberately remain outside this contract.
 */
data class InputConsoleSkin(
    val id: String,
    val numberColumnWeight: Float,
    val mainColumnWeight: Float,
    val actionColumnWeight: Float,
    val sectionSpacing: Dp,
    val keyRowSpacing: Dp,
    val compositionHeight: Dp,
    val candidateStripHeight: Dp,
    val expandedCandidatesCoverBoard: Boolean,
) {
  init {
    require(id.isNotBlank()) { "Input-console skin id must not be blank" }
    require(numberColumnWeight > 0f) { "Number-column weight must be positive" }
    require(mainColumnWeight > 0f) { "Main-column weight must be positive" }
    require(actionColumnWeight > 0f) { "Action-column weight must be positive" }
    require(sectionSpacing >= 0.dp) { "Section spacing must not be negative" }
    require(keyRowSpacing >= 0.dp) { "Key-row spacing must not be negative" }
    require(compositionHeight > 0.dp) { "Composition height must be positive" }
    require(candidateStripHeight > 0.dp) { "Candidate-strip height must be positive" }
  }
}

/** Default Gboard-inspired geometry for the app-owned Chinese QWERTY console. */
val GboardQwertyInputConsoleSkin =
    InputConsoleSkin(
        id = "gboard-qwerty-v1",
        numberColumnWeight = 0.28f,
        mainColumnWeight = 1f,
        actionColumnWeight = 0.2f,
        sectionSpacing = 6.dp,
        keyRowSpacing = 6.dp,
        compositionHeight = 20.dp,
        candidateStripHeight = 48.dp,
        expandedCandidatesCoverBoard = true,
    )
