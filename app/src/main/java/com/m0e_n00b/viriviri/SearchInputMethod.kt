package com.m0e_n00b.viriviri

import android.content.Context
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

  /**
   * Loads the bundled frequency dictionary off the main thread and injects it into the
   * lexicon once available. Safe to call from Application/Activity startup; the lexicon
   * works (degraded to the curated table) until the load completes.
   */
  fun preloadDictionary(context: Context) {
    if (loaded) return
    loaded = true
    Thread {
        runCatching {
          val table = BundledPinyinData.loadFromAssets(context.applicationContext)
          chineseLexicon.setTable(table)
        }
      }
      .apply { start() }
  }

  @Volatile private var loaded = false
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
        symbolKeyboard(session)
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
        SearchInputAction.CommitComposition -> commitCompositionOnEnter(session)
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

  /**
   * Enter (CommitComposition). In Chinese mode a non-blank composition is committed as
   * raw pinyin/ASCII text rather than the first Chinese candidate; Space keeps the
   * Gboard-style "pick first candidate" behavior via [commitComposition].
   */
  private fun commitCompositionOnEnter(session: SearchInputSession): SearchInputSession =
      if (session.language == SearchInputLanguage.CHINESE && session.composition.isNotBlank()) {
        commitRawComposition(session)
      } else {
        commitComposition(session)
      }

  /** Commits the literal composition text (e.g. "qiu") and starts a fresh session. */
  private fun commitRawComposition(session: SearchInputSession): SearchInputSession {
    if (session.composition.isBlank()) return session
    val raw = canonicalComposition(session.composition)
    return initialSession(session.committedText + raw).copy(
        language = session.language,
        shiftState = session.shiftState,
        keyboardLayer = session.keyboardLayer,
    )
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

    private fun symbolKeyboard(session: SearchInputSession) =
        SearchInputKeyboard(
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

    private fun letterRows(session: SearchInputSession): List<List<SearchInputKey>> {
      val display = { letter: String ->
        if (session.shiftState == SearchInputShiftState.OFF) letter else letter.uppercase()
      }
      return listOf(
          listOf("q", "w", "e", "r", "t", "y", "u", "i", "o", "p").map { key("letter:$it", display(it)) },
          listOf("a", "s", "d", "f", "g", "h", "j", "k", "l").map { key("letter:$it", display(it)) },
          listOf(key(KEY_SHIFT, if (session.shiftState == SearchInputShiftState.CAPS_LOCK) "⇧" else "Shift")) +
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

    private fun languageKeyLabel(session: SearchInputSession): String =
        if (session.language == SearchInputLanguage.CHINESE) {
          "中"
        } else {
          when (session.shiftState) {
            SearchInputShiftState.OFF -> "eng"
            SearchInputShiftState.SHIFTED -> "Eng"
            SearchInputShiftState.CAPS_LOCK -> "ENG"
          }
        }

    private fun spaceKeyLabel(session: SearchInputSession): String =
        if (session.language == SearchInputLanguage.CHINESE) "拼音" else "English"

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

/**
 * Bundled, offline, frequency-ordered candidates for the Chinese board.
 *
 * Word and single-character candidates come primarily from [table], a pruned rime-ice
 * dictionary (real word/character frequencies; loaded off the main thread, see
 * [DefaultSearchInputMethods.warmUp]). Until it loads (e.g. in JVM unit tests) the lexicon
 * degrades gracefully to the curated [COMMON_CHARACTERS] table and the ICU reverse index.
 */
class DefaultOfflinePinyinLexicon(
    @Volatile private var table: PinyinTable? = null,
) : OfflinePinyinLexicon {
  fun setTable(table: PinyinTable) {
    this.table = table
  }

  fun warmUp() {
    reverseIndex
    table
  }

  override fun candidatesFor(composition: String): List<SearchInputCandidate> {
    val normalized = normalizeComposition(composition)
    if (normalized.isBlank()) return emptyList()
    val compact = normalized.filterNot { it == '\'' }
    if (compact.isBlank()) return emptyList()
    val t = table

    val prefixWord = t?.prefixWord(compact) { hasSyllable(it) }
    val exactWord = t?.exactWord(compact)

    // Words: curated app-context phrases rank first (consumed length desc), then the
    // frequency dictionary's exact/prefix word.
    val curatedPhraseCandidates =
        ALL_PHRASES.entries
            .filter { compact.startsWith(it.key) }
            .flatMap { entry ->
              entry.value.map { value ->
                SearchInputCandidate(
                    value = value,
                    consumedCompositionLength = sourceLengthForLetters(normalized, entry.key.length),
                )
              }
            }
            .sortedByDescending { it.consumedCompositionLength }

    val dictionaryWordCandidates =
        buildList {
          if (exactWord != null) {
            add(SearchInputCandidate(exactWord, consumedCompositionLength = sourceLengthForLetters(normalized, compact.length)))
          }
          if (prefixWord != null && prefixWord != exactWord) {
            // Suggested completion for the still-incomplete prefix (consumes what was typed).
            add(SearchInputCandidate(prefixWord, consumedCompositionLength = sourceLengthForLetters(normalized, compact.length)))
          }
        }

    // When the frequency dictionary has a word for the input (exact, or a completion for the
    // typed prefix), that word is the strong signal: skip noisy per-syllable segmentation and
    // single-syllable chars so e.g. "ces" completes to 测试 rather than splitting into 测+森.
    val hasDictionaryWord = exactWord != null || prefixWord != null
    val segmentedCandidates =
        if (!hasDictionaryWord) {
          segment(compact, 0).flatMap { syllables -> sequenceCandidates(syllables, normalized) }
        } else {
          emptyList()
        }

    val charCandidates = if (hasDictionaryWord) emptyList() else prefixCandidates(normalized, compact)
    // No display cap: the candidate strip measures how many fit and the expanded grid
    // scrolls through the rest. Dedup by value only so a higher-priority word and a lower
    // single character (same Chinese text) never both appear.
    return (curatedPhraseCandidates + dictionaryWordCandidates + segmentedCandidates + charCandidates)
        .distinctBy { it.value }
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
              .take(MAX_SEQUENCE_COMBINATIONS)
    }
    val consumedLetters = syllables.sumOf(String::length)
    val consumedLength = sourceLengthForLetters(source, consumedLetters)
    return combinations.map { value ->
      SearchInputCandidate(value = value, consumedCompositionLength = consumedLength)
    }
  }

  private fun prefixCandidates(source: String, compact: String): List<SearchInputCandidate> {
    // Exact, complete syllable: surface its own frequency-ordered characters (no
    // pollution from longer syllables that merely share the prefix).
    if (hasSyllable(compact)) {
      val consumedLength = sourceLengthForLetters(source, compact.length)
      return charactersFor(compact).map { character ->
        SearchInputCandidate(character, consumedCompositionLength = consumedLength)
      }
    }

    val consumedLength = sourceLengthForLetters(source, compact.length)

    // Aggregation for an in-progress / partial pinyin (e.g. typing "s" or "ces"): gather
    // ALL characters of every syllable that starts with what was typed (so a single letter
    // surfaces hundreds of candidates). Each syllable list is already frequency-ordered;
    // merge by that order so the most common chars float up and rare ones sink.
    val aggregatingKeys = knownSyllables().filter { it.startsWith(compact) }
    if (aggregatingKeys.isNotEmpty()) {
      // When the frequency dictionary is loaded, its per-syllable char lists are ordered by
      // real character frequency. Assign every character its BEST (smallest) rank across the
      // matching syllables and sort by that rank, so the single most frequent character
      // overall (是 from shi) comes first regardless of the syllable map's iteration order.
      // Without the dictionary (curated table only) lists share no global frequency, so a
      // rank-wise round-robin keeps the most common chars of each syllable up top.
      val syllableCharLists = aggregatingKeys.map { charactersFor(it) }
      val merged: List<String> =
          table?.let { t ->
            // Use the real corpus frequency weight of each character (its highest weight
            // across the matching syllables) so 是(31M) outranks 三(2.7M) outranks 撒(0.1M),
            // independent of the syllable map's iteration order.
            val bestWeight = HashMap<String, Long>()
            for (syllable in aggregatingKeys) {
              val weights = t.syllableWeights[syllable].orEmpty()
              for ((ch, w) in weights) {
                bestWeight.merge(ch, w) { a, b -> maxOf(a, b) }
              }
            }
            val distinct = LinkedHashSet<String>()
            syllableCharLists.forEach { it.forEach(distinct::add) }
            distinct.sortedByDescending { bestWeight[it] ?: 0L }.toList()
          } ?: run {
            val maxLen = syllableCharLists.maxOf { it.size }
            val out = LinkedHashSet<String>()
            for (rank in 0 until maxLen) {
              for (chars in syllableCharLists) {
                if (rank < chars.size) out.add(chars[rank])
              }
            }
            out.toList()
          }
      if (merged.isNotEmpty()) {
        return merged.map { character ->
          SearchInputCandidate(character, consumedCompositionLength = consumedLength)
        }
      }
    }

    // Fallback: the typed letters already contain a complete syllable as a prefix
    // (e.g. leftover trailing letters); surface that syllable's characters.
    val fallbackKey =
        knownSyllables()
            .filter { compact.startsWith(it) }
            .sortedWith(compareBy<String> { kotlin.math.abs(it.length - compact.length) }.thenBy { it })
            .firstOrNull()
        ?: return emptyList()
    val fallbackConsumed = sourceLengthForLetters(source, fallbackKey.length.coerceAtMost(compact.length))
    return charactersFor(fallbackKey).map { character ->
      SearchInputCandidate(character, consumedCompositionLength = fallbackConsumed)
    }
  }

  private fun hasSyllable(syllable: String): Boolean =
      table?.syllableChars?.containsKey(syllable) ?: false || COMMON_CHARACTERS.containsKey(syllable)

  private fun knownSyllables(): Set<String> {
    val t = table
    return if (t != null && t.syllableChars.isNotEmpty()) t.syllableChars.keys else COMMON_CHARACTERS.keys
  }

  private fun charactersFor(syllable: String): List<String> {
    // When the frequency dictionary is loaded it is authoritative for ordering (real
    // per-syllable character frequencies); the curated table and ICU index only fill gaps
    // at the tail. Without the dictionary (e.g. unit tests) the curated table is the source.
    val tableChars = table?.syllableChars?.get(syllable).orEmpty()
    if (tableChars != null && tableChars.isNotEmpty()) {
      val taken = LinkedHashSet(tableChars)
      COMMON_CHARACTERS[syllable].orEmpty().forEach(taken::add)
      val fallback =
          reverseIndex[syllable].orEmpty().filter { taken.add(it) }.take(MAX_FALLBACK_CHARACTERS)
      return taken.toList() + fallback
    }
    val curated = COMMON_CHARACTERS[syllable].orEmpty()
    val taken = LinkedHashSet(curated)
    val fallback =
        reverseIndex[syllable].orEmpty().filter { taken.add(it) }.take(MAX_FALLBACK_CHARACTERS)
    return curated + fallback
  }

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
      if (!hasSyllable(syllable)) continue
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
    /**
     * Per-segmentation guard against cartesian-explosion of single-character combinations.
     * Multi-character words are expected to come from the dictionary; segmentation is only a
     * fallback, so this bound only trims low-value combinatorial noise, never displayed words.
     */
    private const val MAX_SEQUENCE_COMBINATIONS = 64
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

    /**
     * High-frequency offline words/short phrases (tone-insensitive concatenated pinyin,
     * no spaces; 'v' is u-umlaut). Values are ordered most-common-first. Exact-pinyin
     * matches from this table rank above random per-syllable segmentations, so typing
     * e.g. "shouji" yields 手机 rather than 手几.
     */
    private val COMMON_PHRASES: Map<String, List<String>> =
        linkedMapOf(
            "women" to listOf("我们"),
            "nimen" to listOf("你们"),
            "tamen" to listOf("他们"),
            "shenme" to listOf("什么"),
            "zenme" to listOf("怎么"),
            "zenmeyang" to listOf("怎么样"),
            "weishenme" to listOf("为什么"),
            "keyi" to listOf("可以"),
            "meiyou" to listOf("没有"),
            "zhidao" to listOf("知道"),
            "xihuan" to listOf("喜欢"),
            "juede" to listOf("觉得"),
            "yinwei" to listOf("因为"),
            "suoyi" to listOf("所以"),
            "danshi" to listOf("但是"),
            "ruguo" to listOf("如果"),
            "ranhou" to listOf("然后"),
            "xianzai" to listOf("现在"),
            "jintian" to listOf("今天"),
            "mingtian" to listOf("明天"),
            "zuotian" to listOf("昨天"),
            "yihuir" to listOf("一会儿"),
            "zhege" to listOf("这个"),
            "nage" to listOf("那个"),
            "zheli" to listOf("这里"),
            "nali" to listOf("那里"),
            "ziji" to listOf("自己"),
            "dajia" to listOf("大家"),
            "pengyou" to listOf("朋友"),
            "tongxue" to listOf("同学"),
            "laoshi" to listOf("老师"),
            "gongzuo" to listOf("工作"),
            "xuexi" to listOf("学习"),
            "chifan" to listOf("吃饭"),
            "shuijiao" to listOf("睡觉"),
            "shangban" to listOf("上班"),
            "xiaban" to listOf("下班"),
            "kaiche" to listOf("开车"),
            "zoulu" to listOf("走路"),
            "paobu" to listOf("跑步"),
            "lvyou" to listOf("旅游"),
            "gouwu" to listOf("购物"),
            "xiuxi" to listOf("休息"),
            "yundong" to listOf("运动"),
            "dianhua" to listOf("电话"),
            "dadianhua" to listOf("打电话"),
            "xiaoxi" to listOf("消息"),
            "faxiaoxi" to listOf("发消息"),
            "shouji" to listOf("手机"),
            "diannao" to listOf("电脑"),
            "dianshi" to listOf("电视"),
            "wangluo" to listOf("网络"),
            "xiangce" to listOf("相册"),
            "tupian" to listOf("图片"),
            "zhaopian" to listOf("照片"),
            "shipin" to listOf("视频"),
            "bofang" to listOf("播放"),
            "shengyin" to listOf("声音"),
            "yinliang" to listOf("音量"),
            "shijian" to listOf("时间"),
            "dongxi" to listOf("东西"),
            "difang" to listOf("地方"),
            "shijie" to listOf("世界"),
            "guojia" to listOf("国家"),
            "chengshi" to listOf("城市"),
            "wenti" to listOf("问题"),
            "daan" to listOf("答案"),
            "xinxi" to listOf("信息"),
            "xinwen" to listOf("新闻"),
            "lishi" to listOf("历史"),
            "wenhua" to listOf("文化"),
            "yuyan" to listOf("语言"),
            "yingyu" to listOf("英语"),
            "zhongwen" to listOf("中文"),
            "pinyin" to listOf("拼音"),
            "shuru" to listOf("输入"),
            "shurufa" to listOf("输入法"),
            "jianpan" to listOf("键盘"),
            "pingmu" to listOf("屏幕"),
            "quanping" to listOf("全屏"),
            "qingxidu" to listOf("清晰度"),
            "gaoqing" to listOf("高清"),
            "languang" to listOf("蓝光"),
            "zimu" to listOf("字幕"),
            "biaoti" to listOf("标题"),
            "neirong" to listOf("内容"),
            "tuijian" to listOf("推荐"),
            "remen" to listOf("热门"),
            "zuixin" to listOf("最新"),
            "fenlei" to listOf("分类"),
            "pindao" to listOf("频道"),
            "zhuanqu" to listOf("专区"),
            "huiyuan" to listOf("会员"),
            "chongzhi" to listOf("充值"),
            "mianfei" to listOf("免费"),
            "fufei" to listOf("付费"),
            "guanggao" to listOf("广告"),
            "tiaoguo" to listOf("跳过"),
            "dakai" to listOf("打开"),
            "guanbi" to listOf("关闭"),
            "tingzhi" to listOf("停止"),
            "kaishi" to listOf("开始"),
            "jixu" to listOf("继续"),
            "zanting" to listOf("暂停"),
            "kuaijin" to listOf("快进"),
            "houtui" to listOf("后退"),
            "xunhuan" to listOf("循环"),
            "shezhi" to listOf("设置"),
            "xiazai" to listOf("下载"),
            "shoucang" to listOf("收藏"),
            "guanzhu" to listOf("关注"),
            "fensi" to listOf("粉丝"),
            "zhubo" to listOf("主播"),
            "zhibo" to listOf("直播"),
            "lixian" to listOf("离线"),
            "zaixian" to listOf("在线"),
            "zhanghao" to listOf("账号"),
            "mima" to listOf("密码"),
            "denglu" to listOf("登录"),
            "tuichu" to listOf("退出"),
            "fanhui" to listOf("返回"),
            "queren" to listOf("确认"),
            "quxiao" to listOf("取消"),
            "danmu" to listOf("弹幕"),
            "pinglun" to listOf("评论"),
            "dianzan" to listOf("点赞"),
            "toubi" to listOf("投币"),
            "chongdian" to listOf("充电"),
            "sanlian" to listOf("三连"),
            "mingchangmian" to listOf("名场面"),
            "duanshipin" to listOf("短视频"),
            "zongyi" to listOf("综艺"),
            "zongyijiemu" to listOf("综艺节目"),
            "dianyingyuan" to listOf("电影院"),
            "donghuapian" to listOf("动画片"),
            "fanju" to listOf("番剧"),
            "xiexie" to listOf("谢谢"),
            "zaijian" to listOf("再见"),
            "duibuqi" to listOf("对不起"),
            "meiguanxi" to listOf("没关系"),
            "buhaoyisi" to listOf("不好意思"),
            "kaiwanxiao" to listOf("开玩笑"),
            "haha" to listOf("哈哈"),
            "beijing" to listOf("北京"),
            "shanghai" to listOf("上海"),
            "xianggang" to listOf("香港"),
            "taiwan" to listOf("台湾"),
        )

    /** Union of curated [PHRASES] (app tags first) and [COMMON_PHRASES]. */
    private val ALL_PHRASES: Map<String, List<String>> =
        (COMMON_PHRASES.keys + PHRASES.keys).associateWith { key ->
          (PHRASES[key].orEmpty() + COMMON_PHRASES[key].orEmpty()).distinct()
        }

    /**
     * Frequency-ordered common characters for every standard Mandarin syllable
     * (tone-insensitive). Each list is ordered most-common-first; these always rank
     * ahead of the ICU reverse-index fallback. 'v' is treated as u-umlaut (lv, nv,
     * lve/nve alongside lue/nue).
     */
    private const val MAX_FALLBACK_CHARACTERS = 2

    private val COMMON_CHARACTERS: Map<String, List<String>> =
        linkedMapOf(
            "a" to "阿啊呵嗄".map(Char::toString),
            "ai" to "爱哀挨矮艾癌碍唉".map(Char::toString),
            "an" to "安按暗案岸俺胺庵".map(Char::toString),
            "ang" to "昂肮盎枊".map(Char::toString),
            "ao" to "奥熬澳傲凹袄懊敖".map(Char::toString),
            "ba" to "八把吧巴爸罢拔霸".map(Char::toString),
            "bai" to "百白摆败拜柏佰捭".map(Char::toString),
            "ban" to "半办班版板般拌搬斑".map(Char::toString),
            "bang" to "帮棒榜绑傍膀蚌谤".map(Char::toString),
            "bao" to "报包保宝抱薄爆鲍".map(Char::toString),
            "bei" to "北被背杯悲备辈贝倍".map(Char::toString),
            "ben" to "本奔苯笨贲".map(Char::toString),
            "beng" to "崩绷甭蹦迸泵".map(Char::toString),
            "bi" to "比笔必壁币毕闭逼鼻避".map(Char::toString),
            "bian" to "边变便编遍辨辩贬扁".map(Char::toString),
            "biao" to "表标彪膘飙镖婊".map(Char::toString),
            "bie" to "别憋瘪鳖".map(Char::toString),
            "bin" to "宾滨斌濒彬殡摈".map(Char::toString),
            "bing" to "并病兵冰饼丙秉柄炳".map(Char::toString),
            "bo" to "波博播伯薄搏驳拨勃".map(Char::toString),
            "bu" to "不步布部补簿捕卜".map(Char::toString),
            "ca" to "擦嚓礤".map(Char::toString),
            "cai" to "才菜采材财彩踩蔡".map(Char::toString),
            "can" to "参餐残惨灿蚕惭".map(Char::toString),
            "cang" to "藏仓舱沧苍".map(Char::toString),
            "cao" to "操草槽曹糙嘈".map(Char::toString),
            "ce" to "测册侧厕策恻".map(Char::toString),
            "ceng" to "层曾蹭噌".map(Char::toString),
            "cha" to "查茶差察插叉岔诧刹".map(Char::toString),
            "chai" to "拆柴差钗豺".map(Char::toString),
            "chan" to "产颤缠搀馋蝉铲禅".map(Char::toString),
            "chang" to "长常场厂唱尝畅倡昌".map(Char::toString),
            "chao" to "超朝潮吵炒钞巢嘲".map(Char::toString),
            "che" to "车彻扯撤掣澈".map(Char::toString),
            "chen" to "陈沉晨尘臣称趁衬辰".map(Char::toString),
            "cheng" to "成城程承称诚盛乘惩".map(Char::toString),
            "chi" to "吃持尺迟池齿赤斥翅".map(Char::toString),
            "chong" to "冲虫重充崇宠舂".map(Char::toString),
            "chou" to "抽仇丑酬愁臭筹稠".map(Char::toString),
            "chu" to "出处初除楚触厨础储".map(Char::toString),
            "chuai" to "揣搋踹膗".map(Char::toString),
            "chuan" to "穿船传川串喘椽".map(Char::toString),
            "chuang" to "床窗创闯疮幢".map(Char::toString),
            "chui" to "吹垂锤炊槌捶".map(Char::toString),
            "chun" to "春纯唇蠢醇淳".map(Char::toString),
            "chuo" to "戳绰辍啜".map(Char::toString),
            "ci" to "此次词刺磁慈瓷辞雌".map(Char::toString),
            "cong" to "从聪匆葱丛囱".map(Char::toString),
            "cou" to "凑辏腠".map(Char::toString),
            "cu" to "促醋簇粗蹴卒".map(Char::toString),
            "cuan" to "窜攒篡蹿汆爨".map(Char::toString),
            "cui" to "催翠脆崔摧粹悴".map(Char::toString),
            "cun" to "村存寸忖".map(Char::toString),
            "cuo" to "错措挫搓撮锉".map(Char::toString),
            "da" to "大打达答搭瘩".map(Char::toString),
            "dai" to "代带待袋戴呆逮怠贷".map(Char::toString),
            "dan" to "但单蛋担淡弹胆旦诞".map(Char::toString),
            "dang" to "当党档挡荡铛".map(Char::toString),
            "dao" to "到道倒刀导岛盗悼蹈".map(Char::toString),
            "de" to "的得地德".map(Char::toString),
            "deng" to "等灯登凳邓瞪".map(Char::toString),
            "di" to "地第低底弟敌迪递滴帝".map(Char::toString),
            "dian" to "点电店典垫颠淀惦殿".map(Char::toString),
            "diao" to "掉调吊钓雕叼貂".map(Char::toString),
            "die" to "跌爹碟蝶叠迭谍".map(Char::toString),
            "ding" to "定顶订丁钉盯鼎锭".map(Char::toString),
            "diu" to "丢铥".map(Char::toString),
            "dong" to "东动懂冬洞栋冻董".map(Char::toString),
            "dou" to "都斗豆逗抖窦兜".map(Char::toString),
            "du" to "度独读杜毒堵渡督赌肚".map(Char::toString),
            "duan" to "断段短端缎锻".map(Char::toString),
            "dui" to "对队堆兑碓".map(Char::toString),
            "dun" to "顿吨盾蹲敦钝炖".map(Char::toString),
            "duo" to "多夺朵躲惰堕舵掇".map(Char::toString),
            "e" to "俄恶鹅额饿鄂娥遏".map(Char::toString),
            "en" to "恩嗯摁蒽".map(Char::toString),
            "er" to "二而儿耳尔饵洱贰".map(Char::toString),
            "fa" to "发法罚乏伐阀".map(Char::toString),
            "fan" to "反饭烦番犯翻范泛繁".map(Char::toString),
            "fang" to "方放房防访纺芳仿".map(Char::toString),
            "fei" to "非费飞肥废肺沸匪".map(Char::toString),
            "fen" to "分份粉奋纷芬坟愤".map(Char::toString),
            "feng" to "风丰封蜂峰锋逢缝凤".map(Char::toString),
            "fo" to "佛坲".map(Char::toString),
            "fou" to "否缶".map(Char::toString),
            "fu" to "服福父付夫府富复负副符".map(Char::toString),
            "ga" to "嘎噶尬尕".map(Char::toString),
            "gai" to "该改概盖溉钙芥".map(Char::toString),
            "gan" to "干感敢赶甘杆肝赣".map(Char::toString),
            "gang" to "刚钢港岗缸杠纲".map(Char::toString),
            "gao" to "高告搞稿糕膏皋".map(Char::toString),
            "ge" to "个各哥歌格革阁隔葛鸽".map(Char::toString),
            "gei" to "给".map(Char::toString),
            "gen" to "根跟亘艮".map(Char::toString),
            "geng" to "更耕梗耿埂哽".map(Char::toString),
            "gong" to "工公共功供宫巩贡攻".map(Char::toString),
            "gou" to "够狗构购沟勾钩苟".map(Char::toString),
            "gu" to "古故股骨顾鼓谷孤姑估".map(Char::toString),
            "gua" to "挂瓜刮寡卦褂".map(Char::toString),
            "guai" to "怪乖拐".map(Char::toString),
            "guan" to "关观管官馆惯灌冠".map(Char::toString),
            "guang" to "光广逛".map(Char::toString),
            "gui" to "归鬼贵规柜跪桂轨".map(Char::toString),
            "gun" to "滚棍辊磙".map(Char::toString),
            "guo" to "国过果锅裹郭".map(Char::toString),
            "ha" to "哈蛤铪".map(Char::toString),
            "hai" to "还海孩害骇嗨".map(Char::toString),
            "han" to "汉寒含喊汗韩旱函".map(Char::toString),
            "hang" to "行航杭巷夯".map(Char::toString),
            "hao" to "好号豪浩耗毫嚎".map(Char::toString),
            "he" to "和河何合喝贺核荷盒赫".map(Char::toString),
            "hei" to "黑嘿".map(Char::toString),
            "hen" to "很恨痕狠".map(Char::toString),
            "heng" to "横恒哼衡".map(Char::toString),
            "hong" to "红宏洪虹鸿轰哄".map(Char::toString),
            "hou" to "后候厚猴喉吼".map(Char::toString),
            "hu" to "湖胡户虎护互乎忽壶".map(Char::toString),
            "hua" to "花话画华化划滑哗".map(Char::toString),
            "huai" to "坏怀淮槐".map(Char::toString),
            "huan" to "换欢环缓幻唤患焕".map(Char::toString),
            "huang" to "黄荒慌皇晃凰幌".map(Char::toString),
            "hui" to "会回灰汇挥辉惠慧毁".map(Char::toString),
            "hun" to "婚混浑魂昏".map(Char::toString),
            "huo" to "活火或货获祸惑伙".map(Char::toString),
            "ji" to "机几级己记技集即济计".map(Char::toString),
            "jia" to "家加价假架佳嘉夹甲".map(Char::toString),
            "jian" to "见间建件简坚尖检剪减".map(Char::toString),
            "jiang" to "将江讲奖蒋酱僵疆".map(Char::toString),
            "jiao" to "叫交教脚角觉较轿胶".map(Char::toString),
            "jie" to "解结接节街姐界阶借捷".map(Char::toString),
            "jin" to "进金今近紧尽斤劲禁".map(Char::toString),
            "jing" to "经京精惊静景境敬警".map(Char::toString),
            "jiong" to "窘炯迥".map(Char::toString),
            "jiu" to "就九久酒旧救纠舅".map(Char::toString),
            "ju" to "局据举句具剧聚巨居拒".map(Char::toString),
            "juan" to "卷娟倦眷绢捐".map(Char::toString),
            "jue" to "觉绝决角爵掘诀".map(Char::toString),
            "jun" to "军君均俊菌峻骏".map(Char::toString),
            "ka" to "卡喀咖咯".map(Char::toString),
            "kai" to "开凯慨楷".map(Char::toString),
            "kan" to "看刊堪勘砍坎".map(Char::toString),
            "kang" to "康抗扛炕糠".map(Char::toString),
            "kao" to "考靠烤拷犒".map(Char::toString),
            "ke" to "可课克客刻科颗棵".map(Char::toString),
            "ken" to "肯恳垦啃".map(Char::toString),
            "keng" to "坑铿".map(Char::toString),
            "kong" to "空孔恐控".map(Char::toString),
            "kou" to "口扣寇抠".map(Char::toString),
            "ku" to "苦哭库裤酷枯".map(Char::toString),
            "kua" to "跨夸垮挎胯".map(Char::toString),
            "kuai" to "快块筷会".map(Char::toString),
            "kuan" to "宽款".map(Char::toString),
            "kuang" to "况矿狂框筐旷".map(Char::toString),
            "kui" to "亏葵愧溃馈魁".map(Char::toString),
            "kun" to "困昆捆坤".map(Char::toString),
            "kuo" to "扩括阔廓".map(Char::toString),
            "la" to "拉啦辣腊蜡喇".map(Char::toString),
            "lai" to "来赖莱睐".map(Char::toString),
            "lan" to "蓝兰栏懒烂拦篮览".map(Char::toString),
            "lang" to "浪郎朗狼廊".map(Char::toString),
            "lao" to "老劳牢捞姥涝".map(Char::toString),
            "le" to "了乐勒".map(Char::toString),
            "lei" to "类累雷泪垒擂".map(Char::toString),
            "leng" to "冷棱愣".map(Char::toString),
            "li" to "里理立力李历利礼丽".map(Char::toString),
            "lia" to "俩".map(Char::toString),
            "lian" to "连联练脸恋链廉莲".map(Char::toString),
            "liang" to "两量亮良凉粮梁".map(Char::toString),
            "liao" to "了料聊辽疗燎廖".map(Char::toString),
            "lie" to "列烈猎裂劣咧".map(Char::toString),
            "lin" to "林临邻淋琳凛".map(Char::toString),
            "ling" to "领令灵零龄陵凌铃".map(Char::toString),
            "liu" to "六留刘流柳溜".map(Char::toString),
            "long" to "龙隆笼聋拢".map(Char::toString),
            "lou" to "楼漏陋搂篓".map(Char::toString),
            "lu" to "路陆录鲁卢炉鹿露".map(Char::toString),
            "lv" to "绿旅律虑率吕屡履".map(Char::toString),
            "luan" to "乱卵滦".map(Char::toString),
            "lue" to "略掠".map(Char::toString),
            "lve" to "略掠".map(Char::toString),
            "lun" to "论轮伦仑沦".map(Char::toString),
            "luo" to "落罗络洛骆螺裸".map(Char::toString),
            "ma" to "妈马吗嘛麻骂码".map(Char::toString),
            "mai" to "买卖麦迈埋脉".map(Char::toString),
            "man" to "满慢漫蛮瞒馒".map(Char::toString),
            "mang" to "忙芒茫盲莽".map(Char::toString),
            "mao" to "毛猫冒帽贸茅矛".map(Char::toString),
            "me" to "么麽".map(Char::toString),
            "mei" to "美没每妹眉梅媒煤".map(Char::toString),
            "men" to "们门闷扪".map(Char::toString),
            "meng" to "梦蒙猛盟孟萌".map(Char::toString),
            "mi" to "米密迷蜜秘眯觅".map(Char::toString),
            "mian" to "面棉免眠绵缅".map(Char::toString),
            "miao" to "秒妙苗描渺庙".map(Char::toString),
            "mie" to "灭蔑咩".map(Char::toString),
            "min" to "民敏闽悯".map(Char::toString),
            "ming" to "名明命鸣铭".map(Char::toString),
            "miu" to "谬".map(Char::toString),
            "mo" to "莫摸末模磨膜墨默".map(Char::toString),
            "mou" to "某谋眸".map(Char::toString),
            "mu" to "母木目牧穆幕慕暮".map(Char::toString),
            "na" to "那拿哪纳娜呐".map(Char::toString),
            "nai" to "奶耐乃奈".map(Char::toString),
            "nan" to "南男难楠".map(Char::toString),
            "nang" to "囊囔".map(Char::toString),
            "nao" to "脑闹恼挠".map(Char::toString),
            "ne" to "呢讷".map(Char::toString),
            "nei" to "内馁".map(Char::toString),
            "nen" to "嫩".map(Char::toString),
            "neng" to "能".map(Char::toString),
            "ni" to "你尼呢泥拟逆腻".map(Char::toString),
            "nian" to "年念粘捻碾".map(Char::toString),
            "niang" to "娘酿".map(Char::toString),
            "niao" to "鸟尿脲".map(Char::toString),
            "nie" to "捏涅聂蹑镍".map(Char::toString),
            "nin" to "您恁".map(Char::toString),
            "ning" to "宁凝拧柠".map(Char::toString),
            "niu" to "牛扭纽钮".map(Char::toString),
            "nong" to "农浓弄".map(Char::toString),
            "nu" to "努怒奴".map(Char::toString),
            "nv" to "女".map(Char::toString),
            "nuan" to "暖".map(Char::toString),
            "nue" to "疟虐".map(Char::toString),
            "nve" to "疟虐".map(Char::toString),
            "nuo" to "诺挪糯懦".map(Char::toString),
            "o" to "哦噢喔".map(Char::toString),
            "ou" to "欧偶殴呕藕".map(Char::toString),
            "pa" to "怕爬帕趴扒".map(Char::toString),
            "pai" to "排派牌拍徘".map(Char::toString),
            "pan" to "判盘盼攀潘畔".map(Char::toString),
            "pang" to "旁胖庞彷".map(Char::toString),
            "pao" to "跑炮泡抛袍".map(Char::toString),
            "pei" to "配陪培佩赔".map(Char::toString),
            "pen" to "喷盆湓".map(Char::toString),
            "peng" to "朋碰捧彭棚蓬".map(Char::toString),
            "pi" to "皮批疲脾屁匹辟".map(Char::toString),
            "pian" to "片偏篇骗".map(Char::toString),
            "piao" to "票飘漂瓢".map(Char::toString),
            "pie" to "撇瞥".map(Char::toString),
            "pin" to "品拼贫频聘".map(Char::toString),
            "ping" to "平评瓶凭屏乒".map(Char::toString),
            "po" to "破迫婆泼魄".map(Char::toString),
            "pou" to "剖裒".map(Char::toString),
            "pu" to "普铺朴葡仆蒲谱".map(Char::toString),
            "qi" to "起七其期气奇器汽齐".map(Char::toString),
            "qia" to "恰洽掐".map(Char::toString),
            "qian" to "前钱千浅签欠迁潜".map(Char::toString),
            "qiang" to "强枪墙抢腔".map(Char::toString),
            "qiao" to "桥巧瞧悄敲翘".map(Char::toString),
            "qie" to "切且窃茄".map(Char::toString),
            "qin" to "亲琴侵勤秦寝".map(Char::toString),
            "qing" to "情清青请轻庆晴".map(Char::toString),
            "qiong" to "穷琼穹".map(Char::toString),
            "qiu" to "求球秋丘囚仇".map(Char::toString),
            "qu" to "去取区曲趣驱屈".map(Char::toString),
            "quan" to "全权圈劝拳泉犬".map(Char::toString),
            "que" to "却确缺雀鹊".map(Char::toString),
            "qun" to "群裙".map(Char::toString),
            "ran" to "然燃染冉".map(Char::toString),
            "rang" to "让嚷壤".map(Char::toString),
            "rao" to "扰绕饶".map(Char::toString),
            "re" to "热惹".map(Char::toString),
            "ren" to "人认任仁忍刃".map(Char::toString),
            "reng" to "仍扔".map(Char::toString),
            "ri" to "日".map(Char::toString),
            "rong" to "容荣融溶戎".map(Char::toString),
            "rou" to "肉柔揉".map(Char::toString),
            "ru" to "如入乳儒辱".map(Char::toString),
            "ruan" to "软阮".map(Char::toString),
            "rui" to "瑞锐蕊睿".map(Char::toString),
            "run" to "润闰".map(Char::toString),
            "ruo" to "若弱".map(Char::toString),
            "sa" to "撒洒萨".map(Char::toString),
            "sai" to "赛塞腮".map(Char::toString),
            "san" to "三散伞".map(Char::toString),
            "sang" to "桑丧嗓".map(Char::toString),
            "sao" to "扫嫂骚搔".map(Char::toString),
            "se" to "色塞涩瑟".map(Char::toString),
            "sen" to "森".map(Char::toString),
            "seng" to "僧".map(Char::toString),
            "sha" to "杀沙傻啥纱刹".map(Char::toString),
            "shai" to "晒筛".map(Char::toString),
            "shan" to "山善闪删衫扇单".map(Char::toString),
            "shang" to "上商伤赏尚".map(Char::toString),
            "shao" to "少烧稍绍勺".map(Char::toString),
            "she" to "设社射蛇舍涉".map(Char::toString),
            "shei" to "谁".map(Char::toString),
            "shen" to "什身深神申审甚婶".map(Char::toString),
            "sheng" to "生声省胜盛升绳".map(Char::toString),
            "shi" to "是时事十石市识实使".map(Char::toString),
            "shou" to "手受收首瘦守售".map(Char::toString),
            "shu" to "书数树属熟输术叔束".map(Char::toString),
            "shua" to "刷耍唰".map(Char::toString),
            "shuai" to "帅衰摔甩".map(Char::toString),
            "shuan" to "栓拴涮".map(Char::toString),
            "shuang" to "双爽霜".map(Char::toString),
            "shui" to "水睡税谁".map(Char::toString),
            "shun" to "顺瞬舜".map(Char::toString),
            "shuo" to "说硕朔烁".map(Char::toString),
            "si" to "四思死私司丝似斯".map(Char::toString),
            "song" to "送松宋颂诵".map(Char::toString),
            "sou" to "搜艘嗖".map(Char::toString),
            "su" to "苏素速诉肃宿俗".map(Char::toString),
            "suan" to "算酸蒜".map(Char::toString),
            "sui" to "岁随虽碎穗隋".map(Char::toString),
            "sun" to "孙损笋".map(Char::toString),
            "suo" to "所索锁缩".map(Char::toString),
            "ta" to "他她它塔踏".map(Char::toString),
            "tai" to "太台抬态泰汰".map(Char::toString),
            "tan" to "谈弹探叹炭坦".map(Char::toString),
            "tang" to "堂唐糖躺汤趟".map(Char::toString),
            "tao" to "套逃桃淘讨涛".map(Char::toString),
            "te" to "特忒".map(Char::toString),
            "teng" to "疼腾藤".map(Char::toString),
            "ti" to "提体题替踢惕".map(Char::toString),
            "tian" to "天田填甜添".map(Char::toString),
            "tiao" to "条跳调挑眺".map(Char::toString),
            "tie" to "铁贴帖".map(Char::toString),
            "ting" to "听停挺亭庭".map(Char::toString),
            "tong" to "同通统痛童筒".map(Char::toString),
            "tou" to "头投透偷".map(Char::toString),
            "tu" to "图土突途涂徒兔".map(Char::toString),
            "tuan" to "团湍".map(Char::toString),
            "tui" to "退推腿蜕".map(Char::toString),
            "tun" to "吞屯臀".map(Char::toString),
            "tuo" to "脱拖托拓妥驼".map(Char::toString),
            "wa" to "瓦挖娃袜蛙".map(Char::toString),
            "wai" to "外歪".map(Char::toString),
            "wan" to "万完晚玩弯碗挽".map(Char::toString),
            "wang" to "王往望网忘亡".map(Char::toString),
            "wei" to "为位未围维委卫伟微".map(Char::toString),
            "wen" to "文问闻温稳".map(Char::toString),
            "weng" to "翁瓮嗡".map(Char::toString),
            "wo" to "我握窝卧沃".map(Char::toString),
            "wu" to "五无物武务屋误舞".map(Char::toString),
            "xi" to "西喜戏息希细习系洗".map(Char::toString),
            "xia" to "下夏吓峡霞瞎".map(Char::toString),
            "xian" to "先现线限险鲜显县".map(Char::toString),
            "xiang" to "想向象项香乡响相".map(Char::toString),
            "xiao" to "小笑消校效晓".map(Char::toString),
            "xie" to "写些谢鞋血斜协械".map(Char::toString),
            "xin" to "新心信欣辛薪".map(Char::toString),
            "xing" to "行星形性醒兴幸".map(Char::toString),
            "xiong" to "雄熊凶胸".map(Char::toString),
            "xiu" to "修休秀袖绣锈".map(Char::toString),
            "xu" to "许需续序虚须畜".map(Char::toString),
            "xuan" to "选宣悬旋玄".map(Char::toString),
            "xue" to "学雪血穴".map(Char::toString),
            "xun" to "寻训讯迅巡".map(Char::toString),
            "ya" to "呀牙压亚雅鸭".map(Char::toString),
            "yan" to "言眼烟演验严颜沿".map(Char::toString),
            "yang" to "样阳养羊洋杨央".map(Char::toString),
            "yao" to "要药摇腰邀咬".map(Char::toString),
            "ye" to "也夜业叶野爷液".map(Char::toString),
            "yi" to "一以已意义医易衣".map(Char::toString),
            "yin" to "因音引银印阴隐".map(Char::toString),
            "ying" to "应影英营迎硬映".map(Char::toString),
            "yo" to "哟唷".map(Char::toString),
            "yong" to "用永勇拥涌庸".map(Char::toString),
            "you" to "有又友右由游优".map(Char::toString),
            "yu" to "于与鱼语雨育余玉".map(Char::toString),
            "yuan" to "元原员远院园愿圆".map(Char::toString),
            "yue" to "月乐越约跃岳".map(Char::toString),
            "yun" to "云运允孕韵匀".map(Char::toString),
            "za" to "杂砸咋".map(Char::toString),
            "zai" to "在再灾载栽宰".map(Char::toString),
            "zan" to "咱赞暂攒".map(Char::toString),
            "zang" to "脏葬藏".map(Char::toString),
            "zao" to "早造遭糟灶".map(Char::toString),
            "ze" to "则责泽择".map(Char::toString),
            "zeng" to "曾增赠憎".map(Char::toString),
            "zha" to "炸扎眨渣闸".map(Char::toString),
            "zhai" to "摘窄宅债寨".map(Char::toString),
            "zhan" to "战站展占沾盏".map(Char::toString),
            "zhang" to "长张章掌丈涨".map(Char::toString),
            "zhao" to "找照着赵招".map(Char::toString),
            "zhe" to "这着者折哲浙".map(Char::toString),
            "zhen" to "真阵镇震珍振".map(Char::toString),
            "zheng" to "正政整争证郑挣".map(Char::toString),
            "zhi" to "知之只直制治至质".map(Char::toString),
            "zhong" to "中种重钟众终".map(Char::toString),
            "zhou" to "周州洲舟粥宙".map(Char::toString),
            "zhu" to "主住注助猪竹祝".map(Char::toString),
            "zhua" to "抓爪".map(Char::toString),
            "zhuai" to "拽".map(Char::toString),
            "zhuan" to "转专赚传砖".map(Char::toString),
            "zhuang" to "装壮状庄撞".map(Char::toString),
            "zhui" to "追坠缀锥".map(Char::toString),
            "zhun" to "准".map(Char::toString),
            "zhuo" to "着桌捉卓灼".map(Char::toString),
            "zi" to "子自字资紫".map(Char::toString),
            "zong" to "总宗纵棕踪".map(Char::toString),
            "zou" to "走邹奏揍".map(Char::toString),
            "zu" to "足组祖阻族".map(Char::toString),
            "zuan" to "钻攥".map(Char::toString),
            "zui" to "最嘴罪醉".map(Char::toString),
            "zun" to "尊遵".map(Char::toString),
            "zuo" to "做坐左作昨座".map(Char::toString),
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
