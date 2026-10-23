package com.m0e_n00b.spatialworkbench.core

import org.junit.Assert.*
import org.junit.Test

class InputComposerTest {
  private val engine = object : InputEngine {
    override fun evaluate(language: InputLanguage, raw: String) = InputComposition(
        if (language == InputLanguage.JAPANESE) "にほんご" else raw,
        listOf(InputCandidate(if (language == InputLanguage.JAPANESE) "日本語" else "你好", CompositionRange(0, raw.length))),
    )
  }
  private val composer = InputComposer(engine)
  private fun typed(text: String, language: InputLanguage = InputLanguage.CHINESE) =
      text.fold(InputSession(language = language)) { state, letter -> composer.reduce(state, InputAction.Text(letter.toString())).session }

  @Test fun spaceCommitsCandidateWithoutTrailingSpace() {
    val result = composer.reduce(typed("nihao"), InputAction.Space)
    assertEquals(listOf(InputEdit.Commit("你好"), InputEdit.Finish), result.edits)
    assertEquals("", result.session.raw)
  }

  @Test fun enterCommitsLiteralPinyinAndKanaBeforeEditorAction() {
    assertEquals(InputEdit.Commit("nihao"), composer.reduce(typed("nihao"), InputAction.Enter).edits.first())
    assertEquals(InputEdit.Commit("にほんご"), composer.reduce(typed("nihongo", InputLanguage.JAPANESE), InputAction.Enter).edits.first())
    assertEquals(listOf(InputEdit.EditorAction), composer.reduce(InputSession(), InputAction.Enter).edits)
  }

  @Test fun partialCandidateConsumesOnlyItsDeclaredPrefix() {
    val state = typed("nihao").let { it.copy(candidates = listOf(InputCandidate("你", CompositionRange(0, 2), "partial"))) }
    val result = composer.reduce(state, InputAction.SelectCandidate("partial"))
    assertEquals("hao", result.session.raw)
    assertEquals(InputEdit.Commit("你"), result.edits.first())
    assertEquals(InputEdit.Compose("hao"), result.edits.last())
  }

  @Test fun sameTextCandidatesAreIdentifiedByIdAndOldIdsAreRejected() {
    val state = typed("nihao").copy(candidates = listOf(
        InputCandidate("你", CompositionRange(0, 2), "short"), InputCandidate("你", CompositionRange(0, 5), "whole")))
    assertEquals("hao", composer.reduce(state, InputAction.SelectCandidate("short")).session.raw)
    assertEquals("", composer.reduce(state, InputAction.SelectCandidate("whole")).session.raw)
    assertTrue(composer.reduce(state, InputAction.SelectCandidate("old")).edits.isEmpty())
  }

  @Test fun punctuationCommitsBeforeInsertingSymbol() {
    assertEquals(listOf(InputEdit.Commit("你好"), InputEdit.Finish, InputEdit.Commit("!")),
        composer.reduce(typed("nihao"), InputAction.Text("!")).edits)
  }

  @Test fun emptyCandidatesStillPreserveTextAndSubmitOnce() {
    val empty = InputComposer(object : InputEngine {
      override fun evaluate(language: InputLanguage, raw: String) = InputComposition(raw, emptyList())
    })
    val state = empty.reduce(InputSession(), InputAction.Text("unknown")).session
    val result = empty.reduce(state, InputAction.Submit)
    assertEquals(listOf(InputEdit.Commit("unknown"), InputEdit.Finish, InputEdit.EditorAction), result.edits)
  }

  @Test fun cyclingFinishesThePreviewAndRotatesAllThreeLanguages() {
    var state = typed("ni")
    val result = composer.reduce(state, InputAction.CycleLanguage)
    assertEquals(InputEdit.Commit("ni"), result.edits.first())
    state = result.session
    assertEquals(InputLanguage.ENGLISH, state.language)
    state = composer.reduce(state, InputAction.CycleLanguage).session
    assertEquals(InputLanguage.JAPANESE, state.language)
    assertEquals(InputLanguage.CHINESE, composer.reduce(state, InputAction.CycleLanguage).session.language)
  }

  @Test fun passwordNeverPredictsOrExposesComposition() {
    val noPrediction = InputComposer(object : InputEngine {
      override fun evaluate(language: InputLanguage, raw: String): InputComposition = error("Sensitive input predicted")
    })
    val state = InputSession(language = InputLanguage.ENGLISH, sensitive = true)
    val result = noPrediction.reduce(state, InputAction.Text("secret"))
    assertEquals(state, result.session)
    assertEquals(listOf(InputEdit.Commit("secret")), result.edits)
    assertEquals(state, noPrediction.reduce(state, InputAction.CycleLanguage).session)
  }

  @Test fun deletionIsCodePointSafeAndEditsCompositionFirst() {
    assertEquals("x", "x😀".dropLastCodePoint())
    val result = composer.reduce(typed("ni"), InputAction.Backspace)
    assertEquals("n", result.session.raw)
    assertEquals(listOf(InputEdit.DeleteBackward), composer.reduce(InputSession(), InputAction.Backspace).edits)
  }

  @Test fun singleShiftReleasesAndCapsLockSurvives() {
    var state = InputSession(language = InputLanguage.ENGLISH)
    state = composer.reduce(state, InputAction.Shift).session
    val letter = composer.reduce(state, InputAction.Text("a"))
    assertEquals(listOf(InputEdit.Commit("A")), letter.edits)
    assertEquals(InputShift.OFF, letter.session.shift)
    state = composer.reduce(state, InputAction.Shift).session
    assertEquals(InputShift.CAPS_LOCK, composer.reduce(state, InputAction.Text("b")).session.shift)
  }
}
