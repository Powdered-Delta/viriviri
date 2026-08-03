package com.m0e_n00b.viriviri

import android.icu.text.Transliterator
import java.text.Normalizer
import java.util.Locale

/**
 * Pure input-method contract. Language engines own key processing and candidate generation;
 * hosts render the returned session without knowing a language or dictionary format.
 */
interface SearchInputMethod {
  val id: String
  val displayName: String

  fun keyboard(session: SearchInputSession): List<List<SearchInputKey>>

  fun initialSession(committedText: String = ""): SearchInputSession

  fun replaceCommittedText(session: SearchInputSession, committedText: String): SearchInputSession

  fun reduce(session: SearchInputSession, action: SearchInputAction): SearchInputSession
}

data class SearchInputSession(
    val inputMethodId: String,
    val committedText: String = "",
    val composition: String = "",
    val candidates: List<SearchInputCandidate> = emptyList(),
    val candidateMode: SearchCandidateMode = SearchCandidateMode.PHRASE,
    val engineData: Map<String, String> = emptyMap(),
)

enum class SearchCandidateMode {
  SINGLE_CHARACTER,
  PHRASE,
}

data class SearchInputCandidate(val value: String, val label: String = value)

data class SearchInputKey(
    val id: String,
    val label: String,
    val hint: String,
    val action: SearchInputAction,
)

sealed interface SearchInputAction {
  data class PressKey(val keyId: String, val eventTimeMs: Long) : SearchInputAction

  data class SelectCandidate(val value: String) : SearchInputAction

  data class SetCandidateMode(val mode: SearchCandidateMode) : SearchInputAction

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
  val registry = SearchInputMethodRegistry(listOf(ChineseT9InputMethod(chineseLexicon)))

  fun warmUp() = chineseLexicon.warmUp()
}

interface OfflinePinyinLexicon {
  fun candidatesFor(composition: String): List<SearchInputCandidate>

  fun singleCharacterCandidatesFor(composition: String): List<SearchInputCandidate> =
      candidatesFor(composition).filter { it.value.codePointCount(0, it.value.length) == 1 }
}

/**
 * Multi-tap T9 Pinyin input. Tapping the same number inside the multi-tap window cycles its
 * letters; pause before entering another adjacent letter on that number.
 */
class ChineseT9InputMethod(
    private val lexicon: OfflinePinyinLexicon = DefaultOfflinePinyinLexicon(),
    private val multiTapWindowMs: Long = MULTI_TAP_WINDOW_MS,
) : SearchInputMethod {
  override val id: String = "zh-Hans-t9"
  override val displayName: String = "中文九宫格"

  override fun keyboard(session: SearchInputSession): List<List<SearchInputKey>> = KEYBOARD_ROWS

  override fun initialSession(committedText: String): SearchInputSession =
      SearchInputSession(inputMethodId = id, committedText = committedText)

  override fun replaceCommittedText(
      session: SearchInputSession,
      committedText: String,
  ): SearchInputSession = initialSession(committedText).copy(candidateMode = session.candidateMode)

  override fun reduce(session: SearchInputSession, action: SearchInputAction): SearchInputSession =
      when (action) {
        is SearchInputAction.PressKey -> pressKey(session, action)
        is SearchInputAction.SelectCandidate -> commit(session, action.value)
        is SearchInputAction.SetCandidateMode ->
            session.copy(
                candidateMode = action.mode,
                candidates = candidatesFor(session.composition, action.mode),
            )
        SearchInputAction.Backspace -> backspace(session)
        SearchInputAction.CommitComposition ->
            commit(session, session.candidates.firstOrNull()?.value ?: session.composition)
      }

  private fun pressKey(
      session: SearchInputSession,
      action: SearchInputAction.PressKey,
  ): SearchInputSession {
    val letters = LETTERS_BY_KEY[action.keyId] ?: return session
    val lastKey = session.engineData[LAST_KEY]
    val lastPressAt = session.engineData[LAST_PRESS_AT]?.toLongOrNull()
    val canCycle =
        lastKey == action.keyId &&
            lastPressAt != null &&
            action.eventTimeMs - lastPressAt in 0 until multiTapWindowMs &&
            session.composition.isNotEmpty()
    val cycleIndex =
        if (canCycle) (session.engineData[CYCLE_INDEX]?.toIntOrNull()?.plus(1) ?: 0) % letters.length else 0
    val nextComposition =
        if (canCycle) session.composition.dropLast(1) + letters[cycleIndex] else session.composition + letters[0]
    return session.copy(
        composition = nextComposition,
        candidates = candidatesFor(nextComposition, session.candidateMode),
        engineData =
            mapOf(
                LAST_KEY to action.keyId,
                LAST_PRESS_AT to action.eventTimeMs.toString(),
                CYCLE_INDEX to cycleIndex.toString(),
            ),
    )
  }

  private fun candidatesFor(
      composition: String,
      mode: SearchCandidateMode,
  ): List<SearchInputCandidate> =
      when (mode) {
        SearchCandidateMode.SINGLE_CHARACTER -> lexicon.singleCharacterCandidatesFor(composition)
        SearchCandidateMode.PHRASE -> lexicon.candidatesFor(composition)
      }

  private fun commit(session: SearchInputSession, value: String): SearchInputSession {
    if (value.isBlank()) return session
    return initialSession(session.committedText + value)
  }

  private fun backspace(session: SearchInputSession): SearchInputSession {
    if (session.composition.isNotEmpty()) {
      val nextComposition = session.composition.dropLast(1)
      return session.copy(
          composition = nextComposition,
          candidates = candidatesFor(nextComposition, session.candidateMode),
          engineData = emptyMap(),
      )
    }
    return initialSession(session.committedText.dropLast(1))
  }

  companion object {
    private const val MULTI_TAP_WINDOW_MS = 650L
    private const val LAST_KEY = "lastKey"
    private const val LAST_PRESS_AT = "lastPressAt"
    private const val CYCLE_INDEX = "cycleIndex"

    private val LETTERS_BY_KEY =
        linkedMapOf(
            "2" to "abc",
            "3" to "def",
            "4" to "ghi",
            "5" to "jkl",
            "6" to "mno",
            "7" to "pqrs",
            "8" to "tuv",
            "9" to "wxyz",
        )

    private val KEYBOARD_ROWS =
        listOf(
            listOf(key("2", "2", "ABC"), key("3", "3", "DEF"), key("4", "4", "GHI")),
            listOf(key("5", "5", "JKL"), key("6", "6", "MNO"), key("7", "7", "PQRS")),
            listOf(
                key("8", "8", "TUV"),
                key("9", "9", "WXYZ"),
                SearchInputKey(
                    id = "commit",
                    label = "上屏",
                    hint = "",
                    action = SearchInputAction.CommitComposition,
                ),
            ),
        )

    private fun key(id: String, label: String, hint: String) =
        SearchInputKey(
            id = id,
            label = label,
            hint = hint,
            action = SearchInputAction.PressKey(id, 0L),
        )
  }
}

/**
 * Bundled, offline candidates for the first-party Chinese board. Product-specific language packs
 * can replace this lexicon with a larger, frequency-ranked dictionary without changing the input
 * board or search workflow.
 */
class DefaultOfflinePinyinLexicon : OfflinePinyinLexicon {
  fun warmUp() {
    reverseIndex
  }

  override fun candidatesFor(composition: String): List<SearchInputCandidate> {
    val normalized = composition.lowercase(Locale.ROOT).filter { it in 'a'..'z' || it == 'v' }
    if (normalized.isBlank()) return emptyList()

    val phrases =
        PHRASES.filterKeys { it.startsWith(normalized) }
            .flatMap { it.value }
    if (phrases.isNotEmpty()) return phrases.distinct().take(MAX_CANDIDATES).map(::SearchInputCandidate)

    return candidateSequences(normalized).map(::SearchInputCandidate).take(MAX_CANDIDATES)
  }

  override fun singleCharacterCandidatesFor(composition: String): List<SearchInputCandidate> {
    val normalized = composition.lowercase(Locale.ROOT).filter { it in 'a'..'z' || it == 'v' }
    if (normalized.isBlank()) return emptyList()
    val syllables = segmentPreferred(normalized, 0).firstOrNull().orEmpty()
    val exactCharacters = syllables.flatMap { syllable -> PREFERRED_CHARACTERS[syllable].orEmpty() }
    val prefixCharacters =
        PREFERRED_CHARACTERS
            .filterKeys { it.startsWith(normalized) }
            .values
            .flatten()
    return (exactCharacters + prefixCharacters)
        .distinct()
        .take(MAX_CANDIDATES)
        .map(::SearchInputCandidate)
  }

  private fun candidateSequences(composition: String): List<String> {
    val segments = segment(composition, 0).take(MAX_SEGMENTATIONS)
    return buildList {
      for (segment in segments) {
        var combinations = listOf("")
        for (syllable in segment) {
          val characters = charactersFor(syllable).take(CHARACTERS_PER_SYLLABLE)
          if (characters.isEmpty()) {
            combinations = emptyList()
            break
          }
          combinations =
              combinations.flatMap { prefix -> characters.map { character -> prefix + character } }
                  .take(MAX_CANDIDATES)
        }
        addAll(combinations)
      }
    }.distinct()
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

  private fun segmentPreferred(composition: String, start: Int): List<List<String>> {
    if (start == composition.length) return listOf(emptyList())
    val result = mutableListOf<List<String>>()
    val maxEnd = minOf(composition.length, start + MAX_SYLLABLE_LENGTH)
    for (end in maxEnd downTo start + 1) {
      val syllable = composition.substring(start, end)
      if (syllable !in PREFERRED_CHARACTERS) continue
      for (tail in segmentPreferred(composition, end)) {
        result += listOf(syllable) + tail
        if (result.size >= MAX_SEGMENTATIONS) return result
      }
    }
    return result
  }

  private fun charactersFor(syllable: String): List<String> =
      (PREFERRED_CHARACTERS[syllable].orEmpty() + reverseIndex[syllable].orEmpty()).distinct()

  private val reverseIndex: Map<String, List<String>> by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
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
  }

  companion object {
    private const val MAX_CANDIDATES = 8
    private const val MAX_SEGMENTATIONS = 4
    private const val MAX_SYLLABLE_LENGTH = 6
    private const val CHARACTERS_PER_SYLLABLE = 3
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
            "youxi" to listOf("游戏"),
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
        )
  }
}
