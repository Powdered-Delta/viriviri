package com.m0e_n00b.viriviri

import android.content.Context
import android.view.KeyEvent
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.ExtractedTextRequest
import android.view.inputmethod.InputConnection
import com.m0e_n00b.inputmethod.engine.WolvicInputEngine
import com.m0e_n00b.spatialworkbench.core.*
import java.util.concurrent.Executors
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class InputFieldKind { TEXT, PASSWORD, EMAIL, NUMBER }
data class InputFieldOptions(val kind: InputFieldKind = InputFieldKind.TEXT, val singleLine: Boolean = true) {
  val direct get() = kind != InputFieldKind.TEXT
}
data class InputUiState(
    val targetId: String? = null,
    val mode: InputMode = InputMode.INTERNAL,
    val visible: Boolean = false,
    val expanded: Boolean = false,
    val session: InputSession = InputSession(),
    val unavailable: Boolean = false,
    val direct: Boolean = false,
    val kind: InputFieldKind = InputFieldKind.TEXT,
    val epoch: Long = 0,
)

/** UI-thread owner of one editor. It never stores the document or passwords. */
class AppInputController(
    context: Context,
    private val onStateChanged: (InputUiState) -> Unit = {},
    suppliedEngine: InputEngine? = null,
) : AutoCloseable {
  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
  private val worker = Executors.newSingleThreadExecutor { runnable -> Thread(runnable, "InputEngine") }.asCoroutineDispatcher()
  private val mutableState = MutableStateFlow(InputUiState())
  val state = mutableState.asStateFlow()
  private var failed = false
  private val engine = suppliedEngine ?: WolvicInputEngine(context)
  private val composer = InputComposer(object : InputEngine {
    override fun evaluate(language: InputLanguage, raw: String): InputComposition = try {
      engine.evaluate(language, raw)
    } catch (e: Exception) {
      failed = true
      InputComposition(raw, emptyList())
    } catch (e: LinkageError) {
      failed = true
      InputComposition(raw, emptyList())
    }
  })
  private data class Target(val owner: Any, val id: String, val options: InputFieldOptions,
      val action: () -> Unit, val system: () -> Unit)
  private data class Binding(val target: Target, val connection: InputConnection, val info: EditorInfo)
  private data class Command(val binding: Binding, val generation: Long, val action: InputAction)
  private var target: Target? = null
  private var binding: Binding? = null
  private var generation = 0L
  private var preferredLanguage = InputLanguage.CHINESE
  private var applying = false
  private val commands = Channel<Command>(Channel.UNLIMITED)

  init {
    scope.launch {
      for (command in commands) {
        if (!valid(command)) continue
        val before = state.value.session
        failed = false
        // Show the literal preedit before a potentially slow dictionary query. A focus handoff
        // can finish this preview without losing the key whose candidate query is in flight.
        if (command.action is InputAction.Text && !state.value.direct &&
            before.language != InputLanguage.ENGLISH && command.action.text.matches(Regex("[a-zA-Z]+"))) {
          edit(command.binding) { setComposingText(before.raw + command.action.text.lowercase(), 1) }
        }
        val transition = withContext(worker) { composer.reduce(before, command.action) }
        if (!valid(command)) continue
        publish(state.value.copy(session = transition.session, expanded = false, unavailable = failed))
        applying = true
        try {
          command.binding.connection.beginBatchEdit()
          try { transition.edits.filterNot { it == InputEdit.EditorAction }.forEach { apply(command.binding, it) } }
          finally { command.binding.connection.endBatchEdit() }
          // The field must have received the final text before a Search/Done callback reads it.
          if (InputEdit.EditorAction in transition.edits) apply(command.binding, InputEdit.EditorAction)
        } finally { applying = false }
        if (!valid(command)) continue
        if (!state.value.direct) preferredLanguage = transition.session.language
        publish(state.value.copy(session = transition.session, expanded = false, unavailable = failed))
      }
    }
  }

  private fun valid(command: Command) = binding === command.binding && generation == command.generation
  private fun publish(value: InputUiState) { mutableState.value = value; onStateChanged(value) }

  fun activate(owner: Any, id: String, options: InputFieldOptions, mode: InputMode,
      action: () -> Unit, requestSystem: () -> Unit) {
    if (target?.owner !== owner || state.value.mode != mode) {
      binding?.let { edit(it) { finishComposingText() } }
      binding = null
      generation++
      publish(InputUiState(targetId = id, mode = mode, visible = mode == InputMode.INTERNAL,
          direct = options.direct, kind = options.kind, epoch = generation,
          session = InputSession(language = if (options.direct) InputLanguage.ENGLISH else preferredLanguage,
              sensitive = options.kind == InputFieldKind.PASSWORD)))
    } else if (!state.value.visible && mode == InputMode.INTERNAL) publish(state.value.copy(visible = true))
    target = Target(owner, id, options, action, requestSystem)
  }

  fun connect(owner: Any, connection: InputConnection, info: EditorInfo): Any? {
    val active = target?.takeIf { it.owner === owner } ?: return null
    val result = Binding(active, connection, info)
    binding = result
    generation++
    publish(state.value.copy(epoch = generation, visible = state.value.mode == InputMode.INTERNAL))
    return result
  }

  fun disconnect(token: Any?) {
    if (binding === token) {
      binding?.let { edit(it) { finishComposingText() } }
      binding = null
      generation++
      publish(state.value.copy(visible = false, session = state.value.session.copy(raw = "", display = "", candidates = emptyList())))
    }
  }

  fun release(owner: Any) {
    if (target?.owner === owner) {
      binding?.let { edit(it) { finishComposingText() } }
      binding = null; target = null; generation++
      publish(InputUiState(session = InputSession(language = preferredLanguage)))
    }
  }

  fun releaseTarget(id: String) { target?.takeIf { it.id == id }?.let { release(it.owner) } }
  fun isActiveOwner(owner: Any): Boolean = target?.owner === owner

  fun submit(): Boolean {
    if (binding == null || state.value.mode != InputMode.INTERNAL) return false
    dispatch(InputAction.Submit)
    return true
  }

  fun dispatch(action: InputAction, epoch: Long = state.value.epoch) {
    if (epoch != state.value.epoch) return
    val current = binding ?: return
    if (state.value.direct && action == InputAction.CycleLanguage) return
    if (current.target.options.kind == InputFieldKind.NUMBER &&
        (action is InputAction.Text && !action.text.all(Char::isDigit) || action == InputAction.Space)) return
    commands.trySend(Command(current, generation, action))
  }

  fun hide() { publish(state.value.copy(visible = false, expanded = false)) }
  fun toggleCandidates() { publish(state.value.copy(expanded = !state.value.expanded)) }
  fun requestSystem() { target?.system?.invoke() }
  fun retry() { dispatch(InputAction.Refresh) }

  /** Called only for external selection/text edits; echoes from our own edits are ignored. */
  fun externalEdit(owner: Any): Boolean {
    if (target?.owner !== owner || applying || state.value.mode == InputMode.SYSTEM) return false
    generation++
    binding?.let { edit(it) { finishComposingText() } }
    publish(state.value.copy(session = state.value.session.copy(raw = "", display = "", candidates = emptyList()), expanded = false, epoch = generation))
    return true
  }

  private fun edit(current: Binding, block: InputConnection.() -> Unit) {
    val wasApplying = applying
    applying = true
    try { current.connection.block() } finally { applying = wasApplying }
  }

  private fun apply(current: Binding, edit: InputEdit) = with(current.connection) {
    when (edit) {
      is InputEdit.Compose -> { setComposingText(edit.text, 1); Unit }
      is InputEdit.Commit -> { commitText(edit.text, 1); Unit }
      InputEdit.Finish -> { finishComposingText(); Unit }
      InputEdit.DeleteBackward -> {
        if (!getSelectedText(0).isNullOrEmpty()) commitText("", 1)
        else if (!deleteSurroundingTextInCodePoints(1, 0)) {
          sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DEL))
          sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DEL))
        }
        Unit
      }
      InputEdit.Clear -> {
        val text = getExtractedText(ExtractedTextRequest(), 0)
        if (text?.text != null) {
          finishComposingText()
          setSelection(text.startOffset, text.startOffset + text.text.length)
          commitText("", 1)
        }
        Unit
      }
      InputEdit.EditorAction -> {
        finishComposingText()
        if (!current.target.options.singleLine && current.info.imeOptions and EditorInfo.IME_MASK_ACTION == EditorInfo.IME_ACTION_NONE)
          commitText("\n", 1)
        else current.target.action()
      }
    }
  }

  override fun close() {
    target?.let { release(it.owner) }
    commands.close()
    scope.cancel()
    // Disposal stays on the same worker as JNI and SQLite access.
    CoroutineScope(worker).launch { try { engine.close() } finally { worker.close() } }
  }
}
