package com.m0e_n00b.viriviri

import com.m0e_n00b.spatialworkbench.core.*

/** Existing viriviri console geometry, independent of the candidate engine. */
data class InputKey(val id: String, val label: String, val hint: String = "", val widthWeight: Float = 1f, val action: InputAction)
data class InputKeyboard(val numberRows: List<List<InputKey>>, val mainRows: List<List<InputKey>>, val actionKeys: List<InputKey>)

object InputKeyboardLayout {
  private const val KEY_LANGUAGE = "language"
  private const val KEY_SHIFT = "shift"
  private const val KEY_SYMBOLS = "symbols"
  private const val KEY_SPACE = "space"
  private const val KEY_COMMA = "comma"
  private const val KEY_PERIOD = "period"
  private const val KEY_EXCLAMATION = "exclamation"
  private const val KEY_QUESTION = "question"
  private const val KEY_APOSTROPHE = "apostrophe"
  fun forSession(session: InputSession): InputKeyboard =
      if (session.layer == InputLayer.SYMBOLS) symbolKeyboard(session)
      else InputKeyboard(NUMBER_ROWS, letterRows(session), ACTION_KEYS)

  private fun actionFor(id: String): InputAction = when (id) {
    KEY_LANGUAGE -> InputAction.CycleLanguage
    KEY_SHIFT -> InputAction.Shift
    KEY_SYMBOLS -> InputAction.ToggleSymbols
    KEY_SPACE -> InputAction.Space
    "backspace" -> InputAction.Backspace
    "enter" -> InputAction.Enter
    KEY_COMMA -> InputAction.Text(",")
    KEY_PERIOD -> InputAction.Text(".")
    KEY_EXCLAMATION -> InputAction.Text("!")
    KEY_QUESTION -> InputAction.Text("?")
    KEY_APOSTROPHE -> InputAction.Text("'")
    else -> InputAction.Text(if (id.startsWith("operator:")) operatorFor(id) else id.substringAfter(':'))
  }
    private val NUMBER_ROWS =
        listOf(
            listOf(
                key("digit:7", "7"),
                key("digit:8", "8"),
                key("digit:9", "9"),
                key("operator:plus", "+"),
            ),
            listOf(
                key("digit:4", "4"),
                key("digit:5", "5"),
                key("digit:6", "6"),
                key("operator:minus", "-"),
            ),
            listOf(
                key("digit:1", "1"),
                key("digit:2", "2"),
                key("digit:3", "3"),
                key("operator:multiply", "×"),
            ),
            listOf(
                key("digit:0", "0"),
                key(KEY_PERIOD, "."),
                key("operator:equals", "="),
                key("operator:divide", "÷"),
            ),
        )

    private val ACTION_KEYS =
        listOf(
            key("backspace", "⌫"),
            key("voice", "麦克风"),
            key("enter", "↵"),
            key("hide", "收起"),
        )

    private fun symbolKeyboard(session: InputSession) =
        InputKeyboard(
            numberRows = NUMBER_ROWS,
            mainRows =
                listOf(
                    listOf(
                        key("symbol:@", "@"),
                        key("symbol:#", "#"),
                        key("symbol:¥", "¥"),
                        key("symbol:%", "%"),
                        key("symbol:&", "&"),
                        key("symbol:*", "*"),
                        key("symbol:-", "-"),
                        key("symbol:+", "+"),
                        key("symbol:(", "("),
                        key("symbol:)", ")"),
                    ),
                    listOf(
                        key("symbol:~", "~"),
                        key("symbol:`", "`"),
                        key("symbol:‘", "‘"),
                        key("symbol:’", "’"),
                        key("symbol::", ":"),
                        key("symbol:;", ";"),
                        key("symbol:_", "_"),
                        key("symbol:=", "="),
                        key("symbol:\\", "\\"),
                    ),
                    listOf(
                        key("symbol:{", "{"),
                        key("symbol:}", "}"),
                        key("symbol:[", "["),
                        key("symbol:]", "]"),
                        key("symbol:<", "<"),
                        key("symbol:>", ">"),
                        key("symbol:《", "《"),
                        key("symbol:》", "》"),
                        key(KEY_SYMBOLS, "字母"),
                    ),
                    listOf(
                        key("symbol:，", "，"),
                        key("symbol:。", "。"),
                        key("symbol:、", "、"),
                        key(KEY_SPACE, spaceKeyLabel(session), widthWeight = 4f),
                        key("symbol:！", "！"),
                        key("symbol:？", "？"),
                        key(KEY_APOSTROPHE, "'"),
                    ),
                ),
            actionKeys = ACTION_KEYS,
        )

    private fun letterRows(session: InputSession): List<List<InputKey>> {
      val display = { letter: String ->
        if (session.shift == InputShift.OFF) letter else letter.uppercase()
      }
      return listOf(
          listOf("q", "w", "e", "r", "t", "y", "u", "i", "o", "p").map { key("letter:$it", display(it)) },
          listOf("a", "s", "d", "f", "g", "h", "j", "k", "l").map { key("letter:$it", display(it)) },
          listOf(key(KEY_SHIFT, if (session.shift == InputShift.CAPS_LOCK) "⇧" else "Shift")) +
              listOf("z", "x", "c", "v", "b", "n", "m").map { key("letter:$it", display(it)) } +
              listOf(key(KEY_SYMBOLS, "符号")),
          listOf(
              key(KEY_LANGUAGE, languageKeyLabel(session)),
              key(KEY_COMMA, ","),
              key(KEY_PERIOD, "."),
              key(KEY_SPACE, spaceKeyLabel(session), widthWeight = 4f),
              key(KEY_EXCLAMATION, "!"),
              key(KEY_QUESTION, "?"),
              key(KEY_APOSTROPHE, "'"),
          ),
      )
    }

    private fun languageKeyLabel(session: InputSession): String =
        if (session.language == InputLanguage.CHINESE) {
          "中"
        } else if (session.language == InputLanguage.JAPANESE) {
          "日"
        } else {
          when (session.shift) {
            InputShift.OFF -> "eng"
            InputShift.SHIFTED -> "Eng"
            InputShift.CAPS_LOCK -> "ENG"
          }
        }

    private fun spaceKeyLabel(session: InputSession): String =
        when (session.language) { InputLanguage.CHINESE -> "拼音"; InputLanguage.ENGLISH -> "English"; InputLanguage.JAPANESE -> "日本語" }

    private fun operatorFor(keyId: String): String =
        when (keyId) {
          "operator:plus" -> "+"
          "operator:minus" -> "-"
          "operator:multiply" -> "*"
          "operator:divide" -> "/"
          "operator:equals" -> "="
          else -> ""
        }

    private fun key(
        id: String,
        label: String,
        hint: String = "",
        widthWeight: Float = 1f,
    ) = InputKey(id, label, hint, widthWeight, actionFor(id))
}
