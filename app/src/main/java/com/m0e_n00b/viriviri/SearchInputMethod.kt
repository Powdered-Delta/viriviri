package com.m0e_n00b.viriviri

import android.icu.text.Transliterator
import java.text.Normalizer
import java.util.Locale

/** Pure input-method contract. The engine owns text editing and candidates only. */
interface SearchInputMethod {
  val id: String
  val displayName: String

  fun keyboard(session: SearchInputSession): List<List<SearchInputKey>>

  fun keyboardLayout(session: SearchInputSession): SearchInputKeyboard =
      SearchInputKeyboard(mainRows = keyboard(session))

  fun initialSession(committedText: String = ""): SearchInputSession

  fun replaceCommittedText(session: SearchInputSession, committedText: String): SearchInputSession

  fun reduce(session: SearchInputSession, action: SearchInputAction): SearchInputSession
}

data class SearchInputSession(
    val inputMethodId: String,
    val committedText: String = "",
    val composition: String = "",
    val candidates: List<SearchInputCandidate> = emptyList(),
    val language: SearchInputLanguage = SearchInputLanguage.CHINESE,
    val shiftState: SearchInputShiftState = SearchInputShiftState.OFF,
    val keyboardLayer: SearchInputKeyboardLayer = SearchInputKeyboardLayer.LETTERS,
    val engineData: Map<String, String> = emptyMap(),
)

enum class SearchInputLanguage {
  CHINESE,
  ENGLISH,
}

enum class SearchInputShiftState {
  OFF,
  SHIFTED,
  CAPS_LOCK,
}

enum class SearchInputKeyboardLayer {
  LETTERS,
  SYMBOLS,
}

data class SearchInputCandidate(
    val value: String,
    val label: String = value,
    /** UTF-16 length of the composition consumed by this candidate. */
    val consumedCompositionLength: Int = 0,
)

data class SearchInputKey(
    val id: String,
    val label: String,
    val hint: String = "",
    val widthWeight: Float = 1f,
    val action: SearchInputAction,
)

data class SearchInputKeyboard(
    val numberRows: List<List<SearchInputKey>> = emptyList(),
    val mainRows: List<List<SearchInputKey>> = emptyList(),
    val actionKeys: List<SearchInputKey> = emptyList(),
)

sealed interface SearchInputAction {
  data class PressKey(val keyId: String, val eventTimeMs: Long) : SearchInputAction

  data class SelectCandidate(val value: String) : SearchInputAction

  data object Backspace : SearchInputAction

  data object CommitComposition : SearchInputAction
}

class SearchInputMethodRegistry(methods: List<SearchInputMethod>) {
  private val methodsById = methods.associateBy(SearchInputMethod::id)
  private val defaultMethod = methods.firstOrNull() ?: error("At least one search input method is required")

  init {
    require(methodsById.size == methods.size) { "Search input method identifiers must be unique" }
  }

  fun initialSession(): SearchInputSession = defaultMethod.initialSession()

  fun methodFor(session: SearchInputSession): SearchInputMethod =
      methodsById[session.inputMethodId] ?: defaultMethod

  fun reduce(session: SearchInputSession, action: SearchInputAction): SearchInputSession =
      methodFor(session).reduce(session, action)

  fun replaceCommittedText(session: SearchInputSession, committedText: String): SearchInputSession =
      methodFor(session).replaceCommittedText(session, committedText)
}

object DefaultSearchInputMethods {
  private val chineseLexicon = DefaultOfflinePinyinLexicon()
  val registry = SearchInputMethodRegistry(listOf(ChinesePinyinQwertyInputMethod(chineseLexicon)))

  fun warmUp() = chineseLexicon.warmUp()
}

interface OfflinePinyinLexicon {
  fun candidatesFor(composition: String): List<SearchInputCandidate>
}

/** Pure Kotlin QWERTY Pinyin input method with an offline candidate source. */
class ChinesePinyinQwertyInputMethod(
    private val lexicon: OfflinePinyinLexicon = DefaultOfflinePinyinLexicon(),
) : SearchInputMethod {
  override val id: String = "zh-Hans-qwerty"
  override val displayName: String = "中文拼音"

  override fun keyboard(session: SearchInputSession): List<List<SearchInputKey>> =
      keyboardLayout(session).mainRows

  override fun keyboardLayout(session: SearchInputSession): SearchInputKeyboard =
      if (session.keyboardLayer == SearchInputKeyboardLayer.SYMBOLS) {
        SYMBOL_KEYBOARD
      } else {
        SearchInputKeyboard(
            numberRows = NUMBER_ROWS,
            mainRows = letterRows(session),
            actionKeys = ACTION_KEYS,
        )
      }

  override fun initialSession(committedText: String): SearchInputSession =
      SearchInputSession(inputMethodId = id, committedText = committedText)

  override fun replaceCommittedText(
      session: SearchInputSession,
      committedText: String,
  ): SearchInputSession =
      initialSession(committedText).copy(
          language = session.language,
      )

  override fun reduce(session: SearchInputSession, action: SearchInputAction): SearchInputSession =
      when (action) {
        is SearchInputAction.PressKey -> pressKey(session, action.keyId)
        is SearchInputAction.SelectCandidate -> commitCandidate(session, action.value)
        SearchInputAction.Backspace -> backspace(session)
        SearchInputAction.CommitComposition -> commitComposition(session)
      }

  private fun pressKey(session: SearchInputSession, keyId: String): SearchInputSession =
      when {
        keyId.startsWith("letter:") -> pressLetter(session, keyId.removePrefix("letter:"))
        keyId.startsWith("digit:") -> commitSymbol(session, keyId.removePrefix("digit:"))
        keyId.startsWith("operator:") -> commitSymbol(session, operatorFor(keyId))
        keyId.startsWith("symbol:") -> commitSymbol(session, keyId.removePrefix("symbol:"))
        keyId == KEY_LANGUAGE -> toggleLanguage(session)
        keyId == KEY_SHIFT -> toggleShift(session)
        keyId == KEY_SYMBOLS -> toggleKeyboardLayer(session)
        keyId == KEY_SPACE -> pressSpace(session)
        keyId == KEY_COMMA -> commitSymbol(session, ",")
        keyId == KEY_PERIOD -> commitSymbol(session, ".")
        keyId == KEY_EXCLAMATION -> commitSymbol(session, "!")
        keyId == KEY_QUESTION -> commitSymbol(session, "?")
        keyId == KEY_APOSTROPHE -> pressApostrophe(session)
        else -> session
      }

  private fun pressLetter(session: SearchInputSession, letter: String): SearchInputSession {
    val normalizedLetter = if (session.shiftState == SearchInputShiftState.OFF) letter else letter.uppercase()
    val nextShift =
        if (session.shiftState == SearchInputShiftState.SHIFTED) SearchInputShiftState.OFF
        else session.shiftState
    if (session.language == SearchInputLanguage.ENGLISH) {
      return session.copy(
          committedText = session.committedText + normalizedLetter,
          shiftState = nextShift,
      )
    }
    val nextComposition = normalizeComposition(session.composition + letter)
    return session.copy(
        composition = nextComposition,
        candidates = candidatesFor(nextComposition),
        shiftState = nextShift,
    )
  }

  private fun pressApostrophe(session: SearchInputSession): SearchInputSession {
    if (session.language == SearchInputLanguage.ENGLISH || session.composition.isBlank()) return session
    if (session.composition.endsWith("'")) return session
    return session.copy(composition = session.composition + "'")
  }

  private fun pressSpace(session: SearchInputSession): SearchInputSession {
    if (session.language == SearchInputLanguage.CHINESE && session.composition.isNotBlank()) {
      val committed = commitComposition(session)
      return committed.copy(committedText = committed.committedText + " ")
    }
    return commitSymbol(session, " ")
  }

  private fun commitSymbol(session: SearchInputSession, symbol: String): SearchInputSession {
    val committed =
        if (session.composition.isNotBlank()) commitComposition(session) else session
    return committed.copy(
        committedText = committed.committedText + symbol,
        shiftState = if (session.shiftState == SearchInputShiftState.SHIFTED) SearchInputShiftState.OFF else session.shiftState,
    )
  }

  private fun toggleLanguage(session: SearchInputSession): SearchInputSession =
      session.copy(
          language =
              if (session.language == SearchInputLanguage.CHINESE) SearchInputLanguage.ENGLISH
              else SearchInputLanguage.CHINESE,
          composition = "",
          candidates = emptyList(),
          shiftState = SearchInputShiftState.OFF,
      )

  private fun toggleShift(session: SearchInputSession): SearchInputSession =
      session.copy(
          shiftState =
              when (session.shiftState) {
                SearchInputShiftState.OFF -> SearchInputShiftState.SHIFTED
                SearchInputShiftState.SHIFTED -> SearchInputShiftState.CAPS_LOCK
                SearchInputShiftState.CAPS_LOCK -> SearchInputShiftState.OFF
              }
      )

  private fun toggleKeyboardLayer(session: SearchInputSession): SearchInputSession =
      session.copy(
          keyboardLayer =
              if (session.keyboardLayer == SearchInputKeyboardLayer.LETTERS) {
                SearchInputKeyboardLayer.SYMBOLS
              } else {
                SearchInputKeyboardLayer.LETTERS
              }
      )

  private fun commitCandidate(session: SearchInputSession, value: String): SearchInputSession {
    if (value.isBlank()) return session
    val candidate = session.candidates.firstOrNull { it.value == value }
    val consumedLength =
        (candidate?.consumedCompositionLength ?: session.composition.length)
            .coerceIn(0, session.composition.length)
    val remaining = session.composition.drop(consumedLength).let(::normalizeComposition)
    return initialSession(session.committedText + value).copy(
        composition = remaining,
        candidates = candidatesFor(remaining),
        language = session.language,
        shiftState = session.shiftState,
        keyboardLayer = session.keyboardLayer,
    )
  }

  private fun commitComposition(session: SearchInputSession): SearchInputSession {
    if (session.composition.isBlank()) return session
    val value = session.candidates.firstOrNull()?.value ?: canonicalComposition(session.composition)
    return commitCandidate(session, value)
  }

  private fun backspace(session: SearchInputSession): SearchInputSession {
    if (session.composition.isNotEmpty()) {
      val nextComposition = session.composition.dropLastCodePoint().trimEnd('\'')
      return session.copy(
          composition = nextComposition,
          candidates = candidatesFor(nextComposition),
          engineData = emptyMap(),
      )
    }
    return session.copy(committedText = session.committedText.dropLastCodePoint())
  }

  private fun candidatesFor(composition: String): List<SearchInputCandidate> =
      lexicon.candidatesFor(composition)

  companion object {
    private const val KEY_LANGUAGE = "language"
    private const val KEY_SHIFT = "shift"
    private const val KEY_SYMBOLS = "symbols"
    private const val KEY_SPACE = "space"
    private const val KEY_COMMA = "comma"
    private const val KEY_PERIOD = "period"
    private const val KEY_EXCLAMATION = "exclamation"
    private const val KEY_QUESTION = "question"
    private const val KEY_APOSTROPHE = "apostrophe"

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

    private val SYMBOL_KEYBOARD =
        SearchInputKeyboard(
            numberRows = NUMBER_ROWS,
            mainRows =
                listOf(
                    listOf(key("symbol:[", "["), key("symbol:]", "]"), key("symbol:{", "{"), key("symbol:}", "}"), key("symbol:#", "#")),
                    listOf(key("symbol:@", "@"), key("symbol:%", "%"), key("symbol:&", "&"), key("symbol:*", "*"), key("symbol:+", "+")),
                    listOf(key("symbol:-", "-"), key("symbol:=", "="), key("symbol:/", "/"), key("symbol:\\", "\\"), key("symbol:|", "|")),
                    listOf(
                        key(KEY_SYMBOLS, "ABC"),
                        key(KEY_LANGUAGE, "中/英"),
                        key(KEY_SPACE, "空格", widthWeight = 4f),
                        key(KEY_PERIOD, "."),
                        key(KEY_QUESTION, "?"),
                    ),
                ),
            actionKeys = ACTION_KEYS,
        )

    private fun letterRows(session: SearchInputSession): List<List<SearchInputKey>> {
      val display = { letter: String ->
        if (session.shiftState == SearchInputShiftState.OFF) letter else letter.uppercase()
      }
      return listOf(
          listOf("q", "w", "e", "r", "t", "y", "u", "i", "o", "p").map { key("letter:$it", display(it)) },
          listOf("a", "s", "d", "f", "g", "h", "j", "k", "l").map { key("letter:$it", display(it)) },
          listOf(key(KEY_SHIFT, if (session.shiftState == SearchInputShiftState.CAPS_LOCK) "⇧" else "Shift")) +
              listOf("z", "x", "c", "v", "b", "n", "m").map { key("letter:$it", display(it)) } +
              listOf(key(KEY_SYMBOLS, "?123")),
          listOf(
              key(KEY_LANGUAGE, if (session.language == SearchInputLanguage.CHINESE) "中/英" else "英/中"),
              key(KEY_COMMA, ","),
              key(KEY_PERIOD, "."),
              key(KEY_SPACE, "空格", widthWeight = 4f),
              key(KEY_EXCLAMATION, "!"),
              key(KEY_QUESTION, "?"),
              key(KEY_APOSTROPHE, "'"),
          ),
      )
    }

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
    ) = SearchInputKey(id, label, hint, widthWeight, SearchInputAction.PressKey(id, 0L))
  }
}

/** Bundled, offline, frequency-ordered candidates for the Chinese board. */
class DefaultOfflinePinyinLexicon : OfflinePinyinLexicon {
  fun warmUp() {
    reverseIndex
  }

  override fun candidatesFor(composition: String): List<SearchInputCandidate> {
    val normalized = normalizeComposition(composition)
    if (normalized.isBlank()) return emptyList()
    val compact = normalized.filterNot { it == '\'' }
    if (compact.isBlank()) return emptyList()

    val explicitPhraseCandidates =
        PHRASES.entries
            .filter { compact.startsWith(it.key) }
            .flatMap { entry ->
              entry.value.map { value ->
                SearchInputCandidate(
                    value = value,
                    consumedCompositionLength = sourceLengthForLetters(normalized, entry.key.length),
                )
              }
            }

    val segmentedCandidates =
        segment(compact, 0).flatMap { syllables ->
          sequenceCandidates(syllables, normalized)
        }

    val prefixCandidates = prefixCandidates(normalized, compact)
    return (explicitPhraseCandidates + segmentedCandidates + prefixCandidates)
        .distinctBy { it.value to it.consumedCompositionLength }
        .take(MAX_CANDIDATES)
  }

  private fun sequenceCandidates(
      syllables: List<String>,
      source: String,
  ): List<SearchInputCandidate> {
    var combinations = listOf("")
    for (syllable in syllables) {
      val characters = charactersFor(syllable).take(CHARACTERS_PER_SYLLABLE)
      if (characters.isEmpty()) return emptyList()
      combinations =
          combinations
              .flatMap { prefix -> characters.map { character -> prefix + character } }
              .take(MAX_CANDIDATES)
    }
    val consumedLetters = syllables.sumOf(String::length)
    val consumedLength = sourceLengthForLetters(source, consumedLetters)
    return combinations.map { value ->
      SearchInputCandidate(value = value, consumedCompositionLength = consumedLength)
    }
  }

  private fun prefixCandidates(source: String, compact: String): List<SearchInputCandidate> {
    val prefixKeys =
        (PREFERRED_CHARACTERS.keys + reverseIndex.keys)
            .filter { it.startsWith(compact) || compact.startsWith(it) }
            .sortedWith(compareBy<String> { kotlin.math.abs(it.length - compact.length) }.thenBy { it })
    val key = prefixKeys.firstOrNull() ?: return emptyList()
    val consumedLength = sourceLengthForLetters(source, key.length.coerceAtMost(compact.length))
    return charactersFor(key).take(MAX_CANDIDATES).map { character ->
      SearchInputCandidate(character, consumedCompositionLength = consumedLength)
    }
  }

  private fun charactersFor(syllable: String): List<String> =
      (PREFERRED_CHARACTERS[syllable].orEmpty() + reverseIndex[syllable].orEmpty()).distinct()

  private fun sourceLengthForLetters(source: String, letterCount: Int): Int {
    if (letterCount <= 0) return 0
    var letters = 0
    source.forEachIndexed { index, character ->
      if (character != '\'') letters++
      if (letters == letterCount) return index + 1
    }
    return source.length
  }

  private fun segment(composition: String, start: Int): List<List<String>> {
    if (start == composition.length) return listOf(emptyList())
    val result = mutableListOf<List<String>>()
    val maxEnd = minOf(composition.length, start + MAX_SYLLABLE_LENGTH)
    for (end in maxEnd downTo start + 1) {
      val syllable = composition.substring(start, end)
      if (charactersFor(syllable).isEmpty()) continue
      for (tail in segment(composition, end)) {
        result += listOf(syllable) + tail
        if (result.size >= MAX_SEGMENTATIONS) return result
      }
    }
    return result
  }

  private val reverseIndex: Map<String, List<String>> by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
    runCatching {
      val hanToLatin = Transliterator.getInstance("Han-Latin")
      val index = linkedMapOf<String, MutableList<String>>()
      for (codePoint in CJK_UNIFIED_IDEOGRAPHS) {
        val character = codePoint.toChar().toString()
        val pinyin =
            Normalizer.normalize(hanToLatin.transliterate(character), Normalizer.Form.NFD)
                .lowercase(Locale.ROOT)
                .filter { it in 'a'..'z' }
        if (pinyin.isNotEmpty()) index.getOrPut(pinyin) { mutableListOf() }.add(character)
      }
      index
    }.getOrDefault(emptyMap())
  }

  companion object {
    private const val MAX_CANDIDATES = 8
    private const val MAX_SEGMENTATIONS = 8
    private const val MAX_SYLLABLE_LENGTH = 6
    private const val CHARACTERS_PER_SYLLABLE = 4
    private val CJK_UNIFIED_IDEOGRAPHS = 0x4E00..0x9FFF

    private val PHRASES =
        linkedMapOf(
            "bilibili" to listOf("哔哩哔哩"),
            "donghua" to listOf("动画"),
            "dongman" to listOf("动漫"),
            "youxi" to listOf("游戏"),
            "yinyue" to listOf("音乐"),
            "keji" to listOf("科技"),
            "shenghuo" to listOf("生活"),
            "sousuo" to listOf("搜索"),
            "nihao" to listOf("你好"),
            "zhongguo" to listOf("中国"),
            "xian" to listOf("西安"),
            "changan" to listOf("长安"),
            "yule" to listOf("娱乐"),
            "wudao" to listOf("舞蹈"),
            "guichu" to listOf("鬼畜"),
            "fanyu" to listOf("番剧"),
            "dianying" to listOf("电影"),
            "dianshiju" to listOf("电视剧"),
            "jilu" to listOf("纪录片"),
            "zhishi" to listOf("知识"),
            "zixun" to listOf("资讯"),
            "shishang" to listOf("时尚"),
            "meishi" to listOf("美食"),
            "yingshi" to listOf("影视"),
        )

    private val PREFERRED_CHARACTERS =
        mapOf(
            "bi" to listOf("比", "笔", "必"),
            "li" to listOf("里", "理", "力"),
            "ni" to listOf("你", "呢", "尼"),
            "hao" to listOf("好", "号", "浩"),
            "zhong" to listOf("中", "种", "重"),
            "guo" to listOf("国", "过", "果"),
            "dong" to listOf("东", "动", "懂"),
            "hua" to listOf("花", "话", "画"),
            "man" to listOf("漫", "满", "慢"),
            "you" to listOf("有", "又", "游"),
            "xi" to listOf("西", "喜", "戏"),
            "yin" to listOf("音", "因", "银"),
            "yue" to listOf("月", "乐", "越"),
            "ke" to listOf("科", "可", "课"),
            "ji" to listOf("机", "级", "技"),
            "sheng" to listOf("生", "声", "省"),
            "huo" to listOf("活", "火", "或"),
            "sou" to listOf("搜", "艘", "嗖"),
            "suo" to listOf("索", "所", "锁"),
            "yu" to listOf("于", "与", "鱼"),
            "le" to listOf("乐", "了", "勒"),
            "wu" to listOf("无", "五", "舞"),
            "dao" to listOf("到", "道", "导"),
            "gui" to listOf("归", "鬼", "贵"),
            "chu" to listOf("出", "处", "初"),
            "fan" to listOf("番", "反", "饭"),
            "dian" to listOf("点", "电", "店"),
            "ying" to listOf("影", "应", "英"),
            "shi" to listOf("是", "时", "事"),
            "ju" to listOf("剧", "局", "句"),
            "zhi" to listOf("知", "之", "只"),
            "xun" to listOf("讯", "寻", "训"),
            "mei" to listOf("美", "没", "每"),
            "an" to listOf("安", "按", "暗"),
            "chang" to listOf("长", "常", "场"),
            "wo" to listOf("我", "窝", "握"),
        )
  }
}

private fun normalizeComposition(value: String): String {
  val filtered =
      value.lowercase(Locale.ROOT).filter { it in 'a'..'z' || it == '\'' }
  return filtered.replace(Regex("'{2,}"), "'").trimStart('\'')
}

private fun canonicalComposition(value: String): String = normalizeComposition(value).trimEnd('\'')

private fun String.dropLastCodePoint(): String =
    if (isEmpty()) this else substring(0, offsetByCodePoints(length, -1))
