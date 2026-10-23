package com.m0e_n00b.viriviri

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.*
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.m0e_n00b.spatialworkbench.core.*
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import androidx.compose.ui.text.input.ImeAction

/** Actual Compose-created InputConnections. Requires an explicitly authorized Android run. */
class AppInputFieldTest {
  @get:Rule val compose = createComposeRule()
  private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
  private val engine = object : InputEngine {
    override fun evaluate(language: InputLanguage, raw: String) = InputComposition(raw,
        listOf(InputCandidate("你好", CompositionRange(0, raw.length))))
  }

  @Test fun connectionReplacesSelectionAndDeletesWholeEmoji() {
    var value by mutableStateOf(TextFieldValue("ab😀cd", TextRange(2, 4)))
    val handle = InputFieldHandle()
    val controller = AppInputController(context, suppliedEngine = engine)
    try {
      compose.setContent { AppInputField("email", value, { value = it }, controller,
          options = InputFieldOptions(InputFieldKind.EMAIL), handle = handle) }
      compose.runOnIdle { handle.internal() }
      compose.waitForIdle()
      compose.runOnIdle { controller.dispatch(InputAction.Text("X")) }
      compose.waitUntil(5000) { value.text == "abXcd" }
      compose.runOnIdle { value = TextFieldValue("a😀", TextRange(3)) }
      compose.waitForIdle()
      compose.runOnIdle { controller.dispatch(InputAction.Backspace) }
      compose.waitUntil(5000) { value.text == "a" }
    } finally { compose.runOnIdle { controller.close() } }
  }

  @Test fun switchingTargetRejectsSlowCandidatesAndPasswordsNeverPredict() {
    val started = CountDownLatch(1)
    val release = CountDownLatch(1)
    val slow = object : InputEngine {
      override fun evaluate(language: InputLanguage, raw: String): InputComposition {
        started.countDown()
        check(release.await(5, TimeUnit.SECONDS))
        return engine.evaluate(language, raw)
      }
    }
    var username by mutableStateOf(TextFieldValue())
    var password by mutableStateOf(TextFieldValue())
    val first = InputFieldHandle()
    val second = InputFieldHandle()
    val controller = AppInputController(context, suppliedEngine = slow)
    try {
      compose.setContent {
        Column {
          AppInputField("username", username, { username = it }, controller, handle = first)
          AppInputField("password", password, { password = it }, controller,
              options = InputFieldOptions(InputFieldKind.PASSWORD), handle = second)
        }
      }
      compose.runOnIdle { first.internal() }
      compose.waitForIdle()
      compose.runOnIdle { controller.dispatch(InputAction.Text("n")) }
      assertTrue(started.await(5, TimeUnit.SECONDS))
      val oldEpoch = controller.state.value.epoch
      compose.runOnIdle { second.internal() }
      compose.waitForIdle()
      compose.runOnIdle {
        controller.dispatch(InputAction.Text("stale"), oldEpoch)
        controller.dispatch(InputAction.Text("pass123"))
      }
      release.countDown()
      compose.waitUntil(5000) { password.text == "pass123" }
      assertEquals("n", username.text)
      assertTrue(controller.state.value.session.sensitive)
      assertTrue(controller.state.value.session.raw.isEmpty())
      assertTrue(controller.state.value.session.candidates.isEmpty())
    } finally { release.countDown(); compose.runOnIdle { controller.close() } }
  }

  @Test fun explicitSystemHandoffPreservesTextAndDoesNotSubmit() {
    var value by mutableStateOf(TextFieldValue("user@example.com", TextRange(4)))
    var submitted = 0
    val handle = InputFieldHandle()
    val controller = AppInputController(context, suppliedEngine = engine)
    try {
      compose.setContent { AppInputField("email", value, { value = it }, controller,
          options = InputFieldOptions(InputFieldKind.EMAIL), handle = handle, onEditorAction = { submitted++ }) }
      compose.runOnIdle { handle.internal() }
      compose.waitForIdle()
      compose.runOnIdle { handle.system() }
      compose.waitForIdle()
      assertEquals(InputMode.SYSTEM, controller.state.value.mode)
      assertEquals("user@example.com", value.text)
      assertEquals(TextRange(4), value.selection)
      assertEquals(0, submitted)
      compose.runOnIdle { handle.internal() }
      compose.waitForIdle()
      compose.runOnIdle { controller.dispatch(InputAction.Enter) }
      compose.waitUntil(5000) { submitted == 1 }
    } finally { compose.runOnIdle { controller.close() } }
  }

  @Test fun submitWaitsForQueuedCompositionAndReadsTheCommittedField() {
    var value by mutableStateOf(TextFieldValue())
    var submitted: String? = null
    val handle = InputFieldHandle()
    val controller = AppInputController(context, suppliedEngine = engine)
    try {
      compose.setContent { AppInputField("search", value, { value = it }, controller,
          handle = handle, onEditorAction = { submitted = value.text }) }
      compose.runOnIdle { handle.internal() }
      compose.waitForIdle()
      compose.runOnIdle {
        controller.dispatch(InputAction.Text("n"))
        controller.dispatch(InputAction.Text("i"))
        controller.submit()
      }
      compose.waitUntil(5000) { submitted != null }
      assertEquals("你好", submitted)
      assertEquals("", controller.state.value.session.raw)
    } finally { compose.runOnIdle { controller.close() } }
  }

  @Test fun numericNextAndMultilineEditingUseFieldOptions() {
    var numeric by mutableStateOf(TextFieldValue())
    var multiline by mutableStateOf(TextFieldValue())
    var next = 0
    val first = InputFieldHandle()
    val second = InputFieldHandle()
    val controller = AppInputController(context, suppliedEngine = engine)
    try {
      compose.setContent {
        Column {
          AppInputField("number", numeric, { numeric = it }, controller,
              options = InputFieldOptions(InputFieldKind.NUMBER), imeAction = ImeAction.Next,
              handle = first, onEditorAction = { next++ })
          AppInputField("message", multiline, { multiline = it }, controller,
              options = InputFieldOptions(singleLine = false), handle = second)
        }
      }
      compose.runOnIdle { first.internal() }
      compose.waitForIdle()
      compose.runOnIdle {
        controller.dispatch(InputAction.Text("a"))
        controller.dispatch(InputAction.Text("123"))
        controller.dispatch(InputAction.Space)
        controller.dispatch(InputAction.Enter)
      }
      compose.waitUntil(5000) { next == 1 }
      assertEquals("123", numeric.text)
      compose.runOnIdle { second.internal() }
      compose.waitForIdle()
      compose.runOnIdle {
        controller.dispatch(InputAction.CycleLanguage)
        controller.dispatch(InputAction.Text("hello"))
        controller.dispatch(InputAction.Enter)
      }
      compose.waitUntil(5000) { multiline.text == "hello\n" }
      compose.runOnIdle { controller.dispatch(InputAction.Clear) }
      compose.waitUntil(5000) { multiline.text.isEmpty() }
    } finally { compose.runOnIdle { controller.close() } }
  }
}
