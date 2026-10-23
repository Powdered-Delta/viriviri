package com.m0e_n00b.viriviri

import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.*
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.platform.InterceptPlatformTextInput
import androidx.compose.ui.platform.PlatformTextInputInterceptor
import androidx.compose.ui.platform.PlatformTextInputMethodRequest
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.*
import android.view.inputmethod.EditorInfo
import com.m0e_n00b.spatialworkbench.core.InputMode
import kotlinx.coroutines.awaitCancellation

/** Per-field explicit actions; callbacks are removed when the owning composition is disposed. */
class InputFieldHandle {
  internal var request: ((InputMode) -> Unit)? = null
  fun internal() { request?.invoke(InputMode.INTERNAL) }
  fun system() { request?.invoke(InputMode.SYSTEM) }
}

/** The field owns TextFieldValue; the console only obtains a lifecycle-bound InputConnection. */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun AppInputField(
    id: String,
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    controller: AppInputController,
    modifier: Modifier = Modifier,
    options: InputFieldOptions = InputFieldOptions(),
    imeAction: ImeAction = if (options.singleLine) ImeAction.Done else ImeAction.None,
    handle: InputFieldHandle = remember { InputFieldHandle() },
    textStyle: TextStyle = TextStyle.Default,
    cursorColor: Color = Color.White,
    onEditorAction: () -> Unit = {},
    decorationBox: @Composable (innerTextField: @Composable () -> Unit) -> Unit = { it() },
) {
  val owner = remember(controller, id) { Any() }
  val focus = remember { FocusRequester() }
  val keyboard = LocalSoftwareKeyboardController.current
  val action by rememberUpdatedState(onEditorAction)
  val changed by rememberUpdatedState(onValueChange)
  val snapshot by controller.state.collectAsState()
  var requestedMode by remember(owner) { mutableStateOf(InputMode.INTERNAL) }
  // Capture the value, rather than only its State container, so changing modes replaces
  // the interceptor and restarts the upstream platform session.
  val mode = requestedMode
  fun request(mode: InputMode) {
    requestedMode = mode
    if (mode == InputMode.INTERNAL) keyboard?.hide()
    controller.activate(owner, id, options, mode, { action() }, { handle.system() })
    focus.requestFocus()
  }
  DisposableEffect(handle, owner, options) {
    handle.request = { request(it) }
    onDispose { handle.request = null; controller.release(owner) }
  }
  LaunchedEffect(requestedMode, snapshot.mode, snapshot.targetId) {
    if (controller.isActiveOwner(owner) && requestedMode == InputMode.SYSTEM && snapshot.mode == InputMode.SYSTEM) {
      withFrameNanos { }
      keyboard?.show()
    }
  }
  LaunchedEffect(value.composition, value.text) {
    if (value.composition == null && controller.state.value.session.raw.isNotEmpty()) controller.externalEdit(owner)
  }
  val interceptor = remember(controller, owner, mode, options) {
    PlatformTextInputInterceptor { platformRequest, next ->
      if (mode == InputMode.SYSTEM) {
        val request = object : PlatformTextInputMethodRequest {
          override fun createInputConnection(outAttributes: EditorInfo): android.view.inputmethod.InputConnection {
            val connection = platformRequest.createInputConnection(outAttributes)
            if (options.kind == InputFieldKind.PASSWORD)
              outAttributes.imeOptions = outAttributes.imeOptions or EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING
            return connection
          }
        }
        next.startInputMethod(request)
      } else {
        val info = EditorInfo()
        val connection = platformRequest.createInputConnection(info)
        val token = controller.connect(owner, connection, info)
        try { awaitCancellation() } finally {
          controller.disconnect(token)
          connection.closeConnection()
        }
      }
    }
  }
  InterceptPlatformTextInput(interceptor = interceptor) {
    BasicTextField(
        value = value,
        onValueChange = {
          // Our InputConnection callbacks arrive within an applying transaction. Pointer,
          // hardware-key and system-IME changes reset the old candidate generation.
          val external = controller.externalEdit(owner)
          changed(if (external) it.copy(composition = null) else it)
        },
        modifier = modifier.pointerInput(owner, requestedMode) {
          awaitEachGesture {
            awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            request(InputMode.INTERNAL)
          }
        }.focusRequester(focus).onFocusChanged {
          if (it.isFocused) controller.activate(owner, id, options, requestedMode, { action() }, { handle.system() })
        },
        textStyle = textStyle,
        singleLine = options.singleLine,
        visualTransformation = if (options.kind == InputFieldKind.PASSWORD) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = KeyboardOptions(
            keyboardType = when (options.kind) {
              InputFieldKind.TEXT -> KeyboardType.Text
              InputFieldKind.PASSWORD -> KeyboardType.Password
              InputFieldKind.EMAIL -> KeyboardType.Email
              InputFieldKind.NUMBER -> KeyboardType.Number
            },
            autoCorrectEnabled = !options.direct,
            imeAction = imeAction,
        ),
        keyboardActions = KeyboardActions(onSearch = { action() }, onDone = { action() }, onNext = { action() }),
        cursorBrush = SolidColor(cursorColor),
        decorationBox = decorationBox,
    )
  }
}
