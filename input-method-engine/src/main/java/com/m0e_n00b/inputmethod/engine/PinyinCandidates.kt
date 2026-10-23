/* Derived from Wolvic ChinesePinyinKeyboard, MPL-2.0. See THIRD_PARTY.md. */
package com.m0e_n00b.inputmethod.engine

import com.m0e_n00b.spatialworkbench.core.*

data class PinyinWord(val code: String, val text: String)
fun interface PinyinWords { fun lookup(key: String): List<PinyinWord> }

/** Wolvic's longest-prefix composition with explicit raw-coordinate candidate consumption. */
class PinyinCandidates(private val words: PinyinWords) {
  fun evaluate(raw: String): InputComposition {
    val positions = raw.indices.filter { raw[it] != '\'' }
    val key = positions.joinToString("") { raw[it].lowercaseChar().toString() }
    if (key.isEmpty()) return InputComposition(raw, emptyList())
    val boundaries = raw.indices.filter { raw[it] == '\'' }.map { offset -> raw.take(offset).count { it != '\'' } }.toSet()
    fun lookup(prefix: String, offset: Int = 0): List<PinyinWord> = words.lookup(prefix).filter { word ->
      val code = word.code.replace(" ", "").replace("'", "")
      if (!code.startsWith(prefix)) false else {
        val wordBoundaries = mutableSetOf<Int>()
        var count = 0
        for (c in word.code) if (c == ' ' || c == '\'') wordBoundaries.add(count) else count++
        boundaries.filter { it > offset && it < offset + prefix.length }.all { it - offset in wordBoundaries }
      }
    }
    val candidates = mutableListOf<InputCandidate>()
    fun add(value: String, consumed: Int) {
      if (consumed > 0 && value.isNotEmpty()) {
        val end = positions[consumed - 1] + 1
        if (candidates.none { it.text == value && it.consumedRange.endExclusive == end })
          candidates.add(InputCandidate(value, CompositionRange(0, end)))
      }
    }
    // Greedily join the best known words, retaining unrecognized suffixes literally.
    var offset = 0
    val sentence = StringBuilder()
    while (offset < key.length) {
      var consumed = 0
      for (end in key.length downTo offset + 1) {
        val found = lookup(key.substring(offset, end), offset).firstOrNull() ?: continue
        sentence.append(found.text)
        consumed = end - offset
        break
      }
      if (consumed == 0) { sentence.append(key[offset]); consumed = 1 }
      offset += consumed
    }
    add(sentence.toString(), key.length)
    for (end in key.length downTo 1) lookup(key.take(end)).forEach { add(it.text, end) }
    add(raw, key.length)
    return InputComposition(raw, candidates.take(128))
  }
}
