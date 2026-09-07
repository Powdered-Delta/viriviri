package com.m0e_n00b.viriviri

import org.junit.Assert.assertEquals
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
    assertEquals("digit:7", layout.numberRows.first().first().id)
    assertEquals(listOf("digit:7", "digit:8", "digit:9", "operator:plus"), layout.numberRows.first().map { it.id })
    assertEquals(listOf("digit:0", "period", "operator:equals", "operator:divide"), layout.numberRows.last().map { it.id })
  }

  @Test
  fun bottomRowGivesSpaceTheWidestTouchTarget() {
    val bottomRow = method.keyboardLayout(method.initialSession()).mainRows.last()
    val space = bottomRow.single { it.id == "space" }

    assertEquals(4f, space.widthWeight, 0f)
    assertTrue(bottomRow.filter { it.id != "space" }.all { it.widthWeight < space.widthWeight })
  }

  @Test
  fun symbolLayerKeepsTheWideSpaceTouchTarget() {
    val symbolSession = method.reduce(method.initialSession(), SearchInputAction.PressKey("symbols", 0L))
    val symbolSpace = method.keyboardLayout(symbolSession).mainRows.last().single { it.id == "space" }

    assertEquals(4f, symbolSpace.widthWeight, 0f)
  }

  @Test
  fun symbolLayerKeepsTheSwitchKeyAndSpaceAtStableMainKeyboardPositions() {
    val letterRows = method.keyboardLayout(method.initialSession()).mainRows
    val symbolSession = method.reduce(method.initialSession(), SearchInputAction.PressKey("symbols", 0L))
    val symbolRows = method.keyboardLayout(symbolSession).mainRows

    assertEquals(letterRows.size, symbolRows.size)
    assertEquals("symbols", letterRows[2].last().id)
    assertEquals("symbols", symbolRows[2].last().id)
    assertEquals("space", letterRows.last().single { it.id == "space" }.id)
    assertEquals("space", symbolRows.last().single { it.id == "space" }.id)
    assertEquals(4f, symbolRows.last().single { it.id == "space" }.widthWeight, 0f)
    assertTrue(symbolRows.flatten().none { it.id == "language" })
  }

  @Test
  fun qwertyLettersBuildContinuousChineseComposition() {
    var session = method.initialSession()
    for (letter in "nihao") {
      session = method.reduce(session, SearchInputAction.PressKey("letter:$letter", 0L))
    }

    assertEquals("nihao", session.composition)
    assertEquals("你好", session.candidates.first().value)
    assertEquals(5, session.candidates.first().consumedCompositionLength)
  }

  @Test
  fun continuousPinyinProducesWholeWordCandidatesInsteadOfSingleCharacterSteps() {
    var session = method.initialSession()
    for (letter in "woshi") {
      session = method.reduce(session, SearchInputAction.PressKey("letter:$letter", 0L))
    }

    assertEquals("woshi", session.composition)
    assertTrue(session.candidates.any { it.value == "我是" })
    assertTrue(session.candidates.first().value.length >= 2)
  }

  @Test
  fun continuousPinyinWithNoExplicitPhraseStillProducesCandidates() {
    var session = method.initialSession()
    for (letter in "nishi") {
      session = method.reduce(session, SearchInputAction.PressKey("letter:$letter", 0L))
    }

    assertEquals("nishi", session.composition)
    assertTrue(session.candidates.isNotEmpty())
    assertTrue(session.candidates.any { it.value == "你是" })
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
  fun enterInChineseModeCommitsRawPinyinComposition() {
    var session = method.initialSession("前缀")
    for (letter in "qiu") {
      session = method.reduce(session, SearchInputAction.PressKey("letter:$letter", 0L))
    }
    assertEquals("qiu", session.composition)

    session = method.reduce(session, SearchInputAction.CommitComposition)

    assertTrue(session.committedText.endsWith("qiu"))
    assertEquals("前缀qiu", session.committedText)
    assertTrue(session.composition.isEmpty())
  }

  @Test
  fun blankCompositionEnterIsANoOp() {
    val session = method.initialSession("已有")

    val updated = method.reduce(session, SearchInputAction.CommitComposition)

    assertEquals(session, updated)
    assertEquals("已有", updated.committedText)
    assertTrue(updated.composition.isEmpty())
  }

  @Test
  fun spaceStillPicksFirstChineseCandidateInsteadOfRawPinyin() {
    var session = method.initialSession()
    for (letter in "qiu") {
      session = method.reduce(session, SearchInputAction.PressKey("letter:$letter", 0L))
    }

    session = method.reduce(session, SearchInputAction.PressKey("space", 0L))

    assertTrue(session.committedText.contains("求"))
    assertTrue(!session.committedText.contains("qiu"))
    assertTrue(session.composition.isEmpty())
  }

  @Test
  fun commonHighFrequencyCharactersLeadCandidateOrdering() {
    fun afterTyping(pinyin: String): List<String> {
      var session = method.initialSession()
      for (letter in pinyin) {
        session = method.reduce(session, SearchInputAction.PressKey("letter:$letter", 0L))
      }
      return session.candidates.map { it.value }
    }

    val qiuValues = afterTyping("qiu")
    assertTrue(listOf("求", "球", "秋", "丘").contains(qiuValues.first()))
    for (expected in listOf("求", "球", "秋", "丘")) {
      assertTrue("qiu candidates should contain $expected", qiuValues.contains(expected))
    }

    assertEquals("的", afterTyping("de").first())
    assertEquals("是", afterTyping("shi").first())
    assertEquals("我", afterTyping("wo").first())
    assertEquals("你", afterTyping("ni").first())
  }

  @Test
  fun multiSyllablePhraseStillYieldsCommonPhraseFirst() {
    var session = method.initialSession()
    for (letter in "nihao") {
      session = method.reduce(session, SearchInputAction.PressKey("letter:$letter", 0L))
    }

    assertEquals("你好", session.candidates.first().value)
  }

  @Test
  fun commonOfflinePhrasesRankAheadOfRandomSegmentations() {
    fun firstCandidate(pinyin: String): String =
        DefaultOfflinePinyinLexicon().candidatesFor(pinyin).first().value

    assertEquals("手机", firstCandidate("shouji"))
    assertEquals("电脑", firstCandidate("diannao"))
    assertEquals("网络", firstCandidate("wangluo"))
    assertEquals("相册", firstCandidate("xiangce"))
    assertEquals("视频", firstCandidate("shipin"))
    assertEquals("播放", firstCandidate("bofang"))
    assertEquals("我们", firstCandidate("women"))
    assertEquals("什么", firstCandidate("shenme"))
    assertEquals("谢谢", firstCandidate("xiexie"))
    assertEquals("再见", firstCandidate("zaijian"))
    assertEquals("弹幕", firstCandidate("danmu"))
    assertEquals("输入法", firstCandidate("shurufa"))
  }

  @Test
  fun partialPinyinAggregatesLeadingCharactersAcrossSyllables() {
    fun afterTyping(pinyin: String): List<String> {
      var session = method.initialSession()
      for (letter in pinyin) {
        session = method.reduce(session, SearchInputAction.PressKey("letter:$letter", 0L))
      }
      return session.candidates.map { it.value }
    }

    // Without the bundled dictionary, a single letter aggregates ALL common characters of
    // every syllable starting with it (hundreds, not just one char per syllable). Ordering is
    // by the curated table; true frequency ordering is covered by the dictionary test below.
    val sValues = afterTyping("s")
    assertTrue("expected hundreds of aggregated suggestions, got " + sValues.size, sValues.size >= 100)
    for (expected in listOf("是", "三", "四", "上", "说", "时", "生")) {
      assertTrue("s suggestions should contain $expected", sValues.contains(expected))
    }

    // An exact complete syllable still surfaces its own characters first.
    assertEquals("测", afterTyping("ce").first())
  }

  @Test
  fun bundledFrequencyDictionaryDrivesWordsAndPrefixCompletion() {
    val table =
        BundledPinyinData.parse(
            sequenceOf(
                "# header",
                "P\tceshi\t测试:500807",
                "P\tceshiji\t测试机:12",
                "P\tshouji\t手机:9000",
                "C\tshi\t是:31422712,时:10000000,事:8000000",
                "C\tsan\t三:2747794,散:100",
                "C\tsa\t撒:140910,洒:5000",
            )
        )
    val lexicon = DefaultOfflinePinyinLexicon(table)

    assertEquals("测试", lexicon.candidatesFor("ceshi").first().value)
    assertEquals("手机", lexicon.candidatesFor("shouji").first().value)

    val ces = lexicon.candidatesFor("ces").map { it.value }
    assertTrue("ces should complete to 测试, got $ces", ces.contains("测试"))

    val s = lexicon.candidatesFor("s").map { it.value }
    assertTrue("s should contain 是, got $s", s.contains("是"))
    assertEquals("是", s.first())
  }

  @Test
  fun registryUsesOnlyQwertyAsDefaultMethod() {
    val session = DefaultSearchInputMethods.registry.initialSession()
    assertEquals("zh-Hans-qwerty", session.inputMethodId)
    assertEquals("zh-Hans-qwerty", DefaultSearchInputMethods.registry.methodFor(session).id)
  }
}
