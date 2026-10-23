package com.m0e_n00b.inputmethod.engine

import org.junit.Assert.*
import org.junit.Test

class PinyinCandidatesTest {
  private val entries = mapOf(
      "nihao" to listOf(PinyinWord("ni hao", "你好")),
      "ni" to listOf(PinyinWord("ni", "你")),
      "hao" to listOf(PinyinWord("hao", "好")),
      "xian" to listOf(PinyinWord("xian", "先"), PinyinWord("xi an", "西安")),
      "xi" to listOf(PinyinWord("xi", "西")),
      "an" to listOf(PinyinWord("an", "安")),
      "shurufa" to listOf(PinyinWord("shu ru fa", "输入法")),
  )
  private val pinyin = PinyinCandidates(PinyinWords { entries[it].orEmpty() })

  @Test fun phrasesAndPartialConsumptionUseRawCoordinates() {
    val result = pinyin.evaluate("ni'hao")
    assertEquals("你好", result.candidates.first().text)
    assertEquals(6, result.candidates.first().consumedRange.endExclusive)
    assertEquals(2, result.candidates.first { it.text == "你" }.consumedRange.endExclusive)
    assertEquals("输入法", pinyin.evaluate("shurufa").candidates.first().text)
  }
  @Test fun explicitApostropheRejectsCrossBoundarySyllable() {
    assertEquals("先", pinyin.evaluate("xian").candidates.first().text)
    val separated = pinyin.evaluate("xi'an")
    assertEquals("西安", separated.candidates.first().text)
    assertFalse(separated.candidates.any { it.text == "先" })
  }
  @Test fun unknownSuffixRemainsLiteralAndRangesStayValid() {
    val raw = "ni'zzz"
    val result = pinyin.evaluate(raw)
    assertEquals("你zzz", result.candidates.first().text)
    assertTrue(result.candidates.all { it.consumedRange.isValidFor(raw) })
  }
}
