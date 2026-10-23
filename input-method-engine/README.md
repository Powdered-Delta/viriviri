# Offline app input

This Android library implements `InputEngine` from workbench-core. It contains
only Wolvic-derived Pinyin lookup, the bundled SQLite database and OpenWnn
Japanese conversion. See [THIRD_PARTY.md](THIRD_PARTY.md) for pinned sources,
checksums and licenses. It has no Compose, Spatial SDK or speech dependency.

The app layer owns `AppInputController`, `AppInputField` and the existing
`AppInputConsole`. A field retains `TextFieldValue`; the controller's active
InputConnection is never stored in application UI state. Instantiate one
controller per app UI session and host the console once per active host.

For a future credential form, keep values in its temporary owner and expose the
explicit system-IME action alongside each field:

```kotlin
val handle = remember { InputFieldHandle() }
var password by remember { mutableStateOf(TextFieldValue()) }
AppInputField(
    id = "login-password",
    value = password,
    onValueChange = { password = it },
    controller = appState.inputController,
    options = InputFieldOptions(InputFieldKind.PASSWORD),
    handle = handle,
    onEditorAction = onLogin,
)
SystemImeActionButton(onClick = handle::system)
```

Use unique field IDs and stable options for the lifetime of a mounted field.
EMAIL and NUMBER select direct English input; PASSWORD additionally masks text,
disables prediction and marks system input as non-learning. Use `singleLine =
false` and `ImeAction.None` for multiline fields. Host callbacks implement
Search, Next and Done. Voice callbacks remain available but the voice key is
disabled. Language switching commits the visible preedit before rotating.

Windows checks: `scripts/build-windows-debug.ps1` includes the core, engine,
Compose and application JVM tests. Android instrumentation sources exercise
real SQLite/JNI and Compose InputConnections; compile with
`:app:assembleDebugAndroidTest :input-method-engine:assembleDebugAndroidTest`.
Installing/running those APKs requires explicit device-operation authorization.
