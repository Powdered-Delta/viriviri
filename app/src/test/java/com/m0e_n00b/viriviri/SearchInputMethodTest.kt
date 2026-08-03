package com.m0e_n00b.viriviri

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchInputMethodTest {
  @Test
  fun multiTapCyclesLettersOnTheSameKeyWithinTheInputWindow() {
    val method = ChineseT9InputMethod(StaticLexicon(), multiTapWindowMs = 650L)
    var session = method.initialSession()

    session = method.reduce(session, SearchInputAction.PressKey("2", 100L))
    session = method.reduce(session, SearchInputAction.PressKey("2", 200L))
    session = method.reduce(session, SearchInputAction.PressKey("2", 300L))

    assertEquals("c", session.composition)
  }

  @Test
  fun pausedSameKeyStartsTheNextPinyinLetter() {
    val method = ChineseT9InputMethod(StaticLexicon(), multiTapWindowMs = 650L)
    var session = method.initialSession()

    session = method.reduce(session, SearchInputAction.PressKey("4", 100L))
    session = method.reduce(session, SearchInputAction.PressKey("4", 200L))
    session = method.reduce(session, SearchInputAction.PressKey("4", 900L))

    assertEquals("hg", session.composition)
  }

  @Test
  fun defaultCandidateModeIsPhraseAndSingleCharacterModeIsAvailable() {
    val method = ChineseT9InputMethod(DefaultOfflinePinyinLexicon())
    var session = method.initialSession().copy(composition = "nihao")

    assertEquals(SearchCandidateMode.PHRASE, session.candidateMode)
    session = method.reduce(session, SearchInputAction.SetCandidateMode(SearchCandidateMode.PHRASE))
    assertTrue(session.candidates.any { it.value == "你好" })

    session = method.reduce(session, SearchInputAction.SetCandidateMode(SearchCandidateMode.SINGLE_CHARACTER))
    assertTrue(session.candidates.isNotEmpty())
    assertTrue(session.candidates.all { it.value.codePointCount(0, it.value.length) == 1 })
  }

  @Test
  fun singleCharacterModeReturnsCandidatesForAnIncompletePinyinPrefix() {
    val candidates = DefaultOfflinePinyinLexicon().singleCharacterCandidatesFor("n")

    assertTrue(candidates.isNotEmpty())
    assertTrue(candidates.all { it.value.codePointCount(0, it.value.length) == 1 })
  }

  @Test
  fun selectingCandidateModeKeepsCommittedTextAndComposition() {
    val method = ChineseT9InputMethod(DefaultOfflinePinyinLexicon())
    val session =
        method.initialSession("已提交").copy(
            composition = "ni",
            candidateMode = SearchCandidateMode.SINGLE_CHARACTER,
        )

    val updated = method.reduce(session, SearchInputAction.SetCandidateMode(SearchCandidateMode.PHRASE))

    assertEquals("已提交", updated.committedText)
    assertEquals("ni", updated.composition)
  }

  @Test
  fun offlineLexiconReturnsChinesePhraseCandidates() {
    val candidates = DefaultOfflinePinyinLexicon().candidatesFor("nihao")

    assertTrue(candidates.any { it.value == "你好" })
  }

  @Test
  fun candidateCommitAddsChineseTextAndClearsComposition() {
    val method = ChineseT9InputMethod(StaticLexicon())
    var session = method.initialSession().copy(composition = "nihao")

    session = method.reduce(session, SearchInputAction.SelectCandidate("你好"))

    assertEquals("你好", session.committedText)
    assertTrue(session.composition.isEmpty())
  }

  @Test
  fun backspaceEditsCompositionBeforeCommittedSearchText() {
    val method = ChineseT9InputMethod(StaticLexicon())
    var session = method.initialSession("已提交")
    session = method.reduce(session, SearchInputAction.PressKey("2", 100L))

    session = method.reduce(session, SearchInputAction.Backspace)
    assertEquals("已提交", session.committedText)
    assertTrue(session.composition.isEmpty())

    session = method.reduce(session, SearchInputAction.Backspace)
    assertEquals("已提", session.committedText)
  }

  @Test
  fun registrySelectsCustomLanguageMethodWithoutUiOrProviderChanges() {
    val custom = StaticInputMethod()
    val registry = SearchInputMethodRegistry(listOf(custom, ChineseT9InputMethod(StaticLexicon())))
    val session = registry.reduce(registry.initialSession(), SearchInputAction.PressKey("x", 1L))

    assertEquals("test", session.inputMethodId)
    assertEquals("x", session.committedText)
  }

  private fun typePinyin(
      method: ChineseT9InputMethod,
      initialSession: SearchInputSession,
      pinyin: String,
  ): SearchInputSession {
    var session = initialSession
    var timestamp = 0L
    val keyForLetter = mapOf(
        'a' to "2", 'b' to "2", 'c' to "2",
        'd' to "3", 'e' to "3", 'f' to "3",
        'g' to "4", 'h' to "4", 'i' to "4",
        'j' to "5", 'k' to "5", 'l' to "5",
        'm' to "6", 'n' to "6", 'o' to "6",
        'p' to "7", 'q' to "7", 'r' to "7", 's' to "7",
        't' to "8", 'u' to "8", 'v' to "8",
        'w' to "9", 'x' to "9", 'y' to "9", 'z' to "9",
    )
    val lettersByKey = mapOf(
        "2" to "abc", "3" to "def", "4" to "ghi", "5" to "jkl",
        "6" to "mno", "7" to "pqrs", "8" to "tuv", "9" to "wxyz",
    )
    var previousKey: String? = null
    for (letter in pinyin) {
      val key = checkNotNull(keyForLetter[letter])
      if (key == previousKey) timestamp += 700L else timestamp += 50L
      repeat(checkNotNull(lettersByKey[key]).indexOf(letter) + 1) {
        session = method.reduce(session, SearchInputAction.PressKey(key, timestamp))
        timestamp += 50L
      }
      previousKey = key
    }
    return session
  }

  private class StaticLexicon : OfflinePinyinLexicon {
    override fun candidatesFor(composition: String): List<SearchInputCandidate> =
        if (composition.isBlank()) emptyList() else listOf(SearchInputCandidate(composition))
  }

  private class StaticInputMethod : SearchInputMethod {
    override val id: String = "test"
    override val displayName: String = "Test"

    override fun keyboard(session: SearchInputSession): List<List<SearchInputKey>> = emptyList()

    override fun initialSession(committedText: String): SearchInputSession =
        SearchInputSession(inputMethodId = id, committedText = committedText)

    override fun replaceCommittedText(session: SearchInputSession, committedText: String): SearchInputSession =
        initialSession(committedText)

    override fun reduce(session: SearchInputSession, action: SearchInputAction): SearchInputSession =
        when (action) {
          is SearchInputAction.PressKey -> session.copy(committedText = session.committedText + action.keyId)
          else -> session
        }
  }
}
