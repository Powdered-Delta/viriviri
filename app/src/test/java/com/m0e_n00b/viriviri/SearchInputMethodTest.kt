package com.m0e_n00b.viriviri

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchInputMethodTest {
  private val method = ChinesePinyinQwertyInputMethod(DefaultOfflinePinyinLexicon())

  @Test
  fun qwertyLayoutHasSeparateNumberMainAndActionZones() {
    val layout = method.keyboardLayout(method.initialSession())

    assertEquals(4, layout.numberRows.size)
    assertEquals(4, layout.mainRows.size)
    assertEquals(4, layout.actionKeys.size)
    assertEquals("letter:q", layout.mainRows.first().first().id)
    assertEquals("digit:1", layout.numberRows.first().first().id)
  }

  @Test
  fun qwertyLettersBuildChineseCompositionAndPhraseCandidatesFirst() {
    var session = method.initialSession()
    for (letter in "nihao") {
      session = method.reduce(session, SearchInputAction.PressKey("letter:$letter", 0L))
    }

    assertEquals("nihao", session.composition)
    assertEquals("你好", session.candidates.first().value)
    assertTrue(session.candidates.first().consumedCompositionLength > 0)
  }

  @Test
  fun apostropheIsSupportedInsidePinyinComposition() {
    var session = method.initialSession()
    for (letter in "xi") {
      session = method.reduce(session, SearchInputAction.PressKey("letter:$letter", 0L))
    }
    session = method.reduce(session, SearchInputAction.PressKey("apostrophe", 0L))
    for (letter in "an") {
      session = method.reduce(session, SearchInputAction.PressKey("letter:$letter", 0L))
    }

    assertEquals("xi'an", session.composition)
    assertEquals("西安", session.candidates.first().value)
  }

  @Test
  fun leadingAndRepeatedApostrophesAreIgnored() {
    var session = method.initialSession()
    session = method.reduce(session, SearchInputAction.PressKey("apostrophe", 0L))
    session = method.reduce(session, SearchInputAction.PressKey("letter:x", 0L))
    session = method.reduce(session, SearchInputAction.PressKey("apostrophe", 0L))
    session = method.reduce(session, SearchInputAction.PressKey("apostrophe", 0L))

    assertEquals("x'", session.composition)
  }

  @Test
  fun selectingPartialCandidateKeepsUnconsumedComposition() {
    val partialLexicon = object : OfflinePinyinLexicon {
      override fun candidatesFor(composition: String): List<SearchInputCandidate> =
          if (composition == "nihao") {
            listOf(SearchInputCandidate("你", consumedCompositionLength = 2))
          } else {
            emptyList()
          }
    }
    val partialMethod = ChinesePinyinQwertyInputMethod(partialLexicon)
    val session =
        partialMethod.initialSession().copy(
            composition = "nihao",
            candidates = partialLexicon.candidatesFor("nihao"),
        )

    val updated = partialMethod.reduce(session, SearchInputAction.SelectCandidate("你"))

    assertEquals("你", updated.committedText)
    assertEquals("hao", updated.composition)
  }

  @Test
  fun chineseAndEnglishSwitchKeepsCommittedTextAndClearsComposition() {
    var session = method.initialSession("已提交")
    session = method.reduce(session, SearchInputAction.PressKey("letter:n", 0L))
    session = method.reduce(session, SearchInputAction.PressKey("language", 0L))
    session = method.reduce(session, SearchInputAction.PressKey("letter:a", 0L))

    assertEquals(SearchInputLanguage.ENGLISH, session.language)
    assertEquals("已提交a", session.committedText)
    assertTrue(session.composition.isEmpty())

    session = method.reduce(session, SearchInputAction.PressKey("language", 0L))
    assertEquals(SearchInputLanguage.CHINESE, session.language)
    assertEquals("已提交a", session.committedText)
  }

  @Test
  fun shiftIsOneShotAndCapsLockIsPersistent() {
    var session = method.initialSession()
    session = method.reduce(session, SearchInputAction.PressKey("language", 0L))
    session = method.reduce(session, SearchInputAction.PressKey("shift", 0L))
    session = method.reduce(session, SearchInputAction.PressKey("letter:a", 0L))
    session = method.reduce(session, SearchInputAction.PressKey("letter:b", 0L))

    assertEquals("Ab", session.committedText)
    session = method.reduce(session, SearchInputAction.PressKey("shift", 0L))
    session = method.reduce(session, SearchInputAction.PressKey("shift", 0L))
    session = method.reduce(session, SearchInputAction.PressKey("letter:c", 0L))
    assertEquals("AbC", session.committedText)
  }

  @Test
  fun symbolAndOperatorKeysCommitSymbols() {
    var session = method.initialSession()
    session = method.reduce(session, SearchInputAction.PressKey("digit:2", 0L))
    session = method.reduce(session, SearchInputAction.PressKey("operator:multiply", 0L))
    session = method.reduce(session, SearchInputAction.PressKey("operator:equals", 0L))

    assertEquals("2*=", session.committedText)
  }

  @Test
  fun backspaceEditsCompositionBeforeCommittedTextAndHandlesCodePoints() {
    var session = method.initialSession("😀a")
    session = method.reduce(session, SearchInputAction.PressKey("letter:n", 0L))
    session = method.reduce(session, SearchInputAction.Backspace)
    assertEquals("😀a", session.committedText)
    assertTrue(session.composition.isEmpty())

    session = method.reduce(session, SearchInputAction.Backspace)
    assertEquals("😀", session.committedText)
  }

  @Test
  fun enterCommitsCurrentCandidateWithoutClearingCommittedText() {
    var session = method.initialSession("前缀")
    for (letter in "nihao") {
      session = method.reduce(session, SearchInputAction.PressKey("letter:$letter", 0L))
    }

    session = method.reduce(session, SearchInputAction.CommitComposition)

    assertEquals("前缀你好", session.committedText)
    assertTrue(session.composition.isEmpty())
  }

  @Test
  fun phraseModePlacesSingleCharacterSamplesAfterPhraseCandidates() {
    var session = method.initialSession()
    for (letter in "ni") {
      session = method.reduce(session, SearchInputAction.PressKey("letter:$letter", 0L))
    }

    assertFalse(session.candidates.isEmpty())
    assertTrue(session.candidates.all { it.value.isNotBlank() })
    assertTrue(session.candidates.first().consumedCompositionLength >= 1)
  }

  @Test
  fun registryUsesOnlyQwertyAsDefaultMethod() {
    val session = DefaultSearchInputMethods.registry.initialSession()
    assertEquals("zh-Hans-qwerty", session.inputMethodId)
    assertEquals("zh-Hans-qwerty", DefaultSearchInputMethods.registry.methodFor(session).id)
  }
}
