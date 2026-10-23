/* Derived from Wolvic ChinesePinyinKeyboard / JapaneseKeyboard, MPL-2.0. */
package com.m0e_n00b.inputmethod.engine

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import com.m0e_n00b.spatialworkbench.core.*
import java.io.File
import java.security.MessageDigest
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import jp.co.omronsoft.openwnn.ComposingText
import jp.co.omronsoft.openwnn.StrSegment
import jp.co.omronsoft.openwnn.JAJP.OpenWnnEngineJAJP
import jp.co.omronsoft.openwnn.JAJP.Romkan

/** Lazy, offline engines. Construction, evaluation and disposal belong to one worker. */
class WolvicInputEngine(context: Context) : InputEngine {
  private val context = context.applicationContext
  private var database: SQLiteDatabase? = null
  private var japanese: OpenWnnEngineJAJP? = null
  private val romkan by lazy { Romkan() }
  private val cache = object : LinkedHashMap<String, List<PinyinWord>>(128, .75f, true) {
    override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, List<PinyinWord>>) = size > 128
  }
  private val pinyin = PinyinCandidates(PinyinWords(::lookup))

  override fun evaluate(language: InputLanguage, raw: String): InputComposition {
    if (raw.isEmpty()) return InputComposition("", emptyList())
    // Bound prediction work without truncating or rejecting the user's text.
    if (raw.length > 128) return InputComposition(raw, emptyList())
    return when (language) {
      InputLanguage.CHINESE -> pinyin.evaluate(raw)
      InputLanguage.ENGLISH -> InputComposition(raw, emptyList())
      InputLanguage.JAPANESE -> japanese(raw)
    }
  }

  private fun lookup(key: String): List<PinyinWord> = cache.getOrPut(key) {
    val db = database ?: openDictionary().also { database = it }
    buildList {
      db.rawQuery("SELECT display,candidates FROM keymaps WHERE keymap=? ORDER BY _id LIMIT 128", arrayOf(key)).use { cursor ->
        while (cursor.moveToNext()) for (column in 0..1) if (!cursor.isNull(column))
          cursor.getString(column).split('|').forEach { add(PinyinWord(key, it)) }
      }
      db.rawQuery("SELECT displaycode,display FROM autocorrect WHERE inputcode=? ORDER BY _id LIMIT 128", arrayOf(key)).use { cursor ->
        while (cursor.moveToNext()) cursor.getString(1).split('|').forEach { add(PinyinWord(cursor.getString(0), it)) }
      }
      // Prefix completion for unfinished syllables, after the exact Wolvic dictionary rows.
      if (isEmpty()) db.rawQuery("SELECT displaycode,display FROM autocorrect WHERE inputcode LIKE ? ORDER BY length(inputcode),_id LIMIT 32", arrayOf("$key%")).use { cursor ->
        while (cursor.moveToNext()) add(PinyinWord(cursor.getString(0), cursor.getString(1)))
      }
    }
  }

  private fun openDictionary(): SQLiteDatabase {
    val file = File(context.filesDir, "ime/google_pinyin-$PINYIN_SHA.db")
    file.parentFile!!.mkdirs()
    if (!file.exists() || sha(file) != PINYIN_SHA) {
      val temporary = File(file.parentFile, "google_pinyin.tmp")
      try {
        context.assets.open("ime/google_pinyin.db").use { input -> temporary.outputStream().use { output -> input.copyTo(output) } }
        check(sha(temporary) == PINYIN_SHA) { "Pinyin resource checksum mismatch" }
        Files.move(temporary.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
      } finally { temporary.delete() }
    }
    val db = SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READONLY)
    try {
      db.rawQuery("PRAGMA quick_check", null).use { check(it.moveToFirst() && it.getString(0) == "ok") }
      return db
    } catch (e: Exception) {
      db.close()
      throw e
    }
  }

  private fun japanese(raw: String): InputComposition {
    val converter = japanese ?: OpenWnnEngineJAJP().also {
      it.setKeyboardType(OpenWnnEngineJAJP.KEYBOARD_QWERTY)
      it.setDictionary(OpenWnnEngineJAJP.DIC_LANG_JP)
      it.init()
      japanese = it
    }
    val composing = ComposingText()
    // Resolve a terminal n for the displayed Kana. Re-evaluating from raw still allows
    // the next vowel to form na/ni/etc. without committing that prediction prematurely.
    val romaji = if (raw.endsWith("n") && !raw.endsWith("nn")) "$raw'" else raw
    romaji.forEach {
      composing.insertStrSegment(ComposingText.LAYER0, ComposingText.LAYER1, StrSegment(it.toString()))
      romkan.convert(composing)
    }
    val result = mutableListOf<InputCandidate>()
    if (converter.predict(composing, 0, -1) > 0) {
      var scanned = 0
      while (result.size < 128 && scanned++ < 512) {
        val word = converter.nextCandidate ?: break
        if (word.candidate.isNotEmpty() && result.none { it.text == word.candidate })
          result.add(InputCandidate(word.candidate, CompositionRange(0, raw.length)))
      }
    }
    return InputComposition(composing.toString(ComposingText.LAYER2).ifEmpty { raw }, result)
  }

  override fun close() {
    database?.close(); database = null
    japanese?.close(); japanese = null
    cache.clear()
  }

  private fun sha(file: File): String {
    val digest = MessageDigest.getInstance("SHA-256")
    file.inputStream().use { input ->
      val buffer = ByteArray(8192)
      while (true) { val count = input.read(buffer); if (count < 0) break; digest.update(buffer, 0, count) }
    }
    return digest.digest().joinToString("") { "%02x".format(it) }
  }

  companion object {
    const val PINYIN_SHA = "920f621c690355d33ed6b9732bb7fa3e297b36c379d6b352ef3643be43b83635"
  }
}
