package com.m0e_n00b.spatialworkbench.core

enum class InputLanguage { CHINESE, ENGLISH, JAPANESE }
enum class InputShift { OFF, SHIFTED, CAPS_LOCK }
enum class InputLayer { LETTERS, SYMBOLS }
enum class InputMode { INTERNAL, SYSTEM }

/** No editable document is stored here: the bound field owns its text and selection. */
data class InputSession(
    val language: InputLanguage = InputLanguage.CHINESE,
    val shift: InputShift = InputShift.OFF,
    val layer: InputLayer = InputLayer.LETTERS,
    val raw: String = "",
    val display: String = "",
    val candidates: List<InputCandidate> = emptyList(),
    val revision: Long = 0,
    val sensitive: Boolean = false,
)

data class InputComposition(val display: String, val candidates: List<InputCandidate>)

/** Evaluated only on the engine worker. Implementations must not touch Views or UI state. */
interface InputEngine : AutoCloseable {
  fun evaluate(language: InputLanguage, raw: String): InputComposition
  override fun close() {}
}

sealed interface InputAction {
  data class Text(val text: String) : InputAction
  data class SelectCandidate(val id: String) : InputAction
  data object Backspace : InputAction
  data object Space : InputAction
  data object Enter : InputAction
  data object Submit : InputAction
  data object Finish : InputAction
  data object Reset : InputAction
  data object Refresh : InputAction
  data object Clear : InputAction
  data object CycleLanguage : InputAction
  data object Shift : InputAction
  data object ToggleSymbols : InputAction
}

sealed interface InputEdit {
  data class Compose(val text: String) : InputEdit
  data class Commit(val text: String) : InputEdit
  data object Finish : InputEdit
  data object DeleteBackward : InputEdit
  data object Clear : InputEdit
  data object EditorAction : InputEdit
}

data class InputTransition(val session: InputSession, val edits: List<InputEdit> = emptyList())

/** Ordered input semantics shared by every target. No text buffers or platform objects. */
class InputComposer(private val engine: InputEngine) {
  fun reduce(state: InputSession, action: InputAction): InputTransition = when (action) {
    is InputAction.Text -> {
      val text = action.text
      if (state.language != InputLanguage.ENGLISH && !state.sensitive &&
          (text.matches(Regex("[a-zA-Z]+")) || (text == "'" && state.raw.isNotEmpty()))) {
        val next = if (text == "'" && state.raw.endsWith("'")) state.raw else state.raw + text.lowercase()
        compose(state, next)
      } else {
        val completed = completeCandidates(state)
        val value = if (state.language == InputLanguage.ENGLISH && state.shift != InputShift.OFF) text.uppercase() else text
        completed.copy(session = completed.session.copy(shift = releaseShift(state.shift)), edits = completed.edits + InputEdit.Commit(value))
      }
    }
    is InputAction.SelectCandidate -> select(state, state.candidates.firstOrNull { it.id == action.id })
    InputAction.Backspace -> if (state.raw.isNotEmpty()) compose(state, state.raw.dropLastCodePoint().trimEnd('\''))
        else InputTransition(state, listOf(InputEdit.DeleteBackward))
    InputAction.Space -> if (state.raw.isNotEmpty()) selectFirst(state) else InputTransition(state, listOf(InputEdit.Commit(" ")))
    InputAction.Enter -> if (state.raw.isNotEmpty()) finish(state) else InputTransition(state, listOf(InputEdit.EditorAction))
    InputAction.Submit -> completeCandidates(state).let { it.copy(edits = it.edits + InputEdit.EditorAction) }
    InputAction.Finish -> finish(state)
    InputAction.Reset -> InputTransition(clean(state))
    InputAction.Refresh -> compose(state, state.raw)
    InputAction.Clear -> InputTransition(clean(state), listOf(InputEdit.Clear))
    InputAction.CycleLanguage -> if (state.sensitive) InputTransition(state) else finish(state).let {
      it.copy(session = it.session.copy(language = InputLanguage.entries[(state.language.ordinal + 1) % 3], shift = InputShift.OFF))
    }
    InputAction.Shift -> InputTransition(state.copy(shift = InputShift.entries[(state.shift.ordinal + 1) % 3]))
    InputAction.ToggleSymbols -> InputTransition(state.copy(layer = if (state.layer == InputLayer.LETTERS) InputLayer.SYMBOLS else InputLayer.LETTERS))
  }

  private fun compose(state: InputSession, raw: String): InputTransition {
    if (raw.isEmpty()) return InputTransition(clean(state), listOf(InputEdit.Compose(""), InputEdit.Finish))
    val result = engine.evaluate(state.language, raw)
    val revision = state.revision + 1
    val candidates = result.candidates.filter { it.consumedRange.start == 0 && it.consumedRange.isValidFor(raw) && it.text.isNotEmpty() }
        .mapIndexed { index, candidate -> candidate.copy(id = "$revision:$index") }
    return InputTransition(state.copy(raw = raw, display = result.display, candidates = candidates,
        revision = revision, shift = releaseShift(state.shift)), listOf(InputEdit.Compose(result.display)))
  }

  private fun select(state: InputSession, candidate: InputCandidate?): InputTransition {
    if (candidate == null || candidate.consumedRange.start != 0 || !candidate.consumedRange.isValidFor(state.raw)) return InputTransition(state)
    val remaining = state.raw.substring(candidate.consumedRange.endExclusive).trimStart('\'')
    val rest = if (remaining.isEmpty()) InputTransition(clean(state)) else compose(state, remaining)
    return rest.copy(edits = listOf(InputEdit.Commit(candidate.text), InputEdit.Finish) + rest.edits)
  }

  private fun selectFirst(state: InputSession) = state.candidates.firstOrNull()?.let { select(state, it) } ?: finish(state)

  private fun completeCandidates(state: InputSession): InputTransition {
    var result = InputTransition(state)
    while (result.session.raw.isNotEmpty()) {
      val next = selectFirst(result.session)
      check(next.session.raw.length < result.session.raw.length)
      result = next.copy(edits = result.edits + next.edits)
    }
    return result
  }

  private fun finish(state: InputSession) = InputTransition(clean(state),
      if (state.raw.isEmpty()) listOf(InputEdit.Finish) else listOf(InputEdit.Commit(state.display), InputEdit.Finish))
  private fun clean(state: InputSession) = state.copy(raw = "", display = "", candidates = emptyList(), revision = state.revision + 1)
  private fun releaseShift(shift: InputShift) = if (shift == InputShift.SHIFTED) InputShift.OFF else shift
}

fun String.dropLastCodePoint(): String = if (isEmpty()) this else substring(0, offsetByCodePoints(length, -1))
