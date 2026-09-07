# 04 — VR 键盘 / 射线输入 逆向笔记

样本：`libyoutube_vr_impress_jni.so`（imp 输入层，IDA 13338）；`libgvr_keyboard.so`(2.4MB) 为 GVR 射线键盘 UI，未单独加载。

## 1. 事件通路
- `com.google.ar.imp.core.input.KeyboardView.nProcessKeyboardEvent(handle, action, keycode, downUp, flags)`
  → native `sub_851ED8`：
  - keycode 经映射表 `dword_60B144[keycode-7]`（keycode 基数 7）转成 Android 键码/字符；
  - `downUp`：0 = down、1 = up；**up 时把文本 commit 给聚焦的 View**（构造 32 字节事件对象，含键码、modifier、时间戳，经 `sub_8372A0` 投递，magic 0x604455A5）；
  - down 时走 `sub_9C7AE0/sub_9C78C8` 注入 KeyEvent（`v10 | (modifier<<32)`）。
  - 本质：VR 键盘点击 → **标准 Android KeyEvent / 文本输入**，最终进的是普通 Android EditText 的输入通道。
- `InputManager.nProcessPointerEvent(...)`：射线/手柄指针 → 对空间面板 quad 做命中测试 → 把命中点 (x,y,button) 包成 MotionEvent 分发给光标下的 Android View。

## 2. 对 ViriViri 的结论
- YouTube VR 的 VR 键盘**没有绕过 Android 输入**：它把虚拟按键翻译成标准 KeyEvent/文本，交给 View 体系。
  我们的拼音 IME 走的是 Compose 文本输入/自研 reducer，**不依赖系统 EditText**，因此 VR 键盘要和我们对接时，
  应让「射线点击虚拟键」直接产出我们的 `SearchInputAction`（按键/选候选/回车），而不是 KeyEvent——
  这样候选条/展开面板的状态机才一致。YouTube 的 `down→up 才 commit`、`up 触发动作` 的时序可借鉴（避免按下即触发导致连发）。
- 射线→面板命中：Meta Spatial 的 Panel 已自带激光输入命中；无需自造命中测试。
- `libmozc.so`(11MB) 是 Google 日文输入法 mozc，**与中文拼音无关**，包体可剔除（若我们不做日文）。

## 3. 合规：仅记录架构思路。
