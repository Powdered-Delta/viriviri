# YouTube VR (1.79.11) IDA 逆向分析目标规划

> 目标样本：`temp/YouTube_1.79.11.apk`（99 MB）。
> 目的：为 ViriViri（Meta Spatial SDK / OpenXR VR Bilibili 客户端）做**功能与体积参考**，
> 属个人学习/互操作研究。**仅限静态分析与思路借鉴；不得复制其专有代码/素材、不得再分发其产物**，
> YouTube/Google 商标与内容版权归权利人所有。

## 0. 一句话结论（已通过免 IDA 静态清点得到）

- YouTube VR **不是 Unity，没有 il2cpp / global-metadata**。它是 **原生 C++（OpenXR / 自家 GVR）+ Java/Kotlin（~21MB dex）通过 JNI 桥接** 的架构。
- 仅打包 **arm64-v8a 单 ABI**（39 个 .so，解压后约 112MB；APK 内压缩）。
- 沉浸式 UI 用的是 Google 的 `com.google.ar.imp`（Impress / Immersive Platform）框架，与 Meta Spatial 的 Panel/Surface 思路类似。
- 体积对比要公平：ViriViri 当前 350MB 是 **debug 包**；release + 单 ABI + R8/资源裁剪 后才有可比性（YouTube VR 的 99MB 不含 x86/armv7、不含调试符号、做过裁剪）。

## 1. 分析目标与优先级

| 优先级 | 目标 | 回答的问题 | 对 ViriViri 的价值 |
| --- | --- | --- | --- |
| P0 | **曲面视频幕布** | 曲面网格如何生成/按曲率更新？纹理如何贴到曲面？ | 直接对应待办「curved-video-stage」 |
| P0 | **视频纹理/渲染管线** | 解码器输出 SurfaceTexture 如何进 VR 合成器？swapchain/时间扭曲/刷新率？ | 单播放器单 Surface 架构参考、帧 pacing |
| P1 | **沉浸式 UI 面板（imp 框架）** | 2D 面板如何作为 View/SwapChain 挂到 3D？锚点/碰撞/可见性如何更新？ | Panel 布局、锚点、命中检测参考 |
| P1 | **输入法/键盘** | VR 内键盘（gvr_keyboard、libmozc）如何接入？ | 我们自研拼音 IME 的交互/射线输入参考 |
| P2 | **包体构成与瘦身** | 112MB .so 各是什么？哪些可裁？资源/so 如何压缩？ | release 瘦身、ABI、动态特性分发 |
| P2 | **OpenXR 落点** | libopenxr_loader 之上调用了哪些扩展（foveated、passthrough、hand tracking）？ | 性能/特性取舍参考 |
| P3 | **Co-Watch/播放控制/语音** | ReelPlayerControls、OculusCowatch、VoiceInput | 远期一起看/控制条参考（低优先） |

## 2. 分层工具链（IDA 只管 native，Java 侧别用 IDA）

| 层 | 工具 | 产出 |
| --- | --- | --- |
| 清单/体积 | unzip + 脚本统计、apkanalyzer、aapt | ABI、so/dex/资源占比、AndroidManifest 组件 |
| Java/Kotlin（dex） | **jadx-gui / jadx CLI**（不要用 IDA 看 dex） | 类结构、JNI 方法声明、调用 native 的上层逻辑 |
| Native（.so） | **IDA Pro（arm64）** 或 Ghidra（免费、反编译器够用） | JNI 函数实现、渲染/曲面/纹理算法、OpenXR 调用 |
| JNI 符号恢复 | 导出符号本就是明文 `Java_...`；用 jadx 找到对应 Java native 方法签名，反推参数 | 把 `Java_xxx_nativeUpdateCurvature(...)` 的参数语义对齐 |
| 动态（可选） | frida / renderdoc（VR 合成器抓帧较难，按需） | 验证参数取值、纹理格式 |

> 关键技巧：**JNI 导出名是 `Java_<类>_<方法>`，参数语义从 jadx 里的 Java `native` 声明拿**，
> 比硬读汇编高效得多。IDA 里先在 Exports 窗搜 `Java_com_google_..._youtube_vr`，再交叉引用。

## 3. IDA 切入点（已从样本提取的锚点符号）

### 3.1 曲面幕布 / 视频面板（P0）— `libyoutubevrjni.so` (6.6MB)
- `Java_..._framework_view_VrViewFacade_nativeCreateContainer / DestroyContainer`
- `Java_..._VrViewFacade_nativeUpdateCurva...`（曲面参数，名字被截断，IDA 里看全名与参数）
- `nativeUpdateTexture` / `nativeUpdateViewP...` / `nativeUpdateGradie...` / `nativeUpdateName` / `nativeUpdateVisibility`
- `nativeLookUpAnchor` / `nativeUpdateAnchor` / `nativeUpdateCollision` / `nativeUpdateDismi...`

分析要点：
1. jadx 打开 `com.google.android.apps.youtube.vr.framework.view.VrViewFacade`，记录每个 native 方法的 Java 参数（曲率半径？行列数？纹理 id？）。
2. IDA 里对 `nativeUpdateCurvature`（按全名）F5 反编译：看它把曲率写进哪个 uniform/顶点缓冲；网格是 CPU 生成还是 shader 曲面。
3. 跟 `nativeUpdateTexture`：看 GL/OpenXR texture handle 如何从 SurfaceTexture 拿、是否走 `XR_KHR_swapchain`/external sampler。

### 3.2 渲染器 / 设备能力（P0/P2）— `libyoutubevrjni.so`
- `Java_..._framework_VrRenderer_nativeCreate...`
- `VrApiRunner_nativeGetSupportedRefre...`（刷新率）、`nativeGetDeviceModel`、`nativeGetEnvironmentDec...`、`nativeIsHandTrackingEna...`、`nativeGetBrowseUiPitchA...`（浏览 UI 俯仰角，对应我们的面板 pitch！）。

### 3.3 沉浸式 UI 框架 imp/Impress（P1）— `libyoutube_vr_impress_jni.so` (10.8MB) / `libimpress_api_jni.so`
- 面板/交换链：`com.google.ar.imp.view.View_nCreateView / nCreateSwapChain / nDestroySwapChain / nIsolatedPreRender / nIsolatedPostRender`
- 输入：`input.InputManager_nProcessPointerEvent`、`input.KeyboardView_nProcessKeyboardEvent`
- Web/脚本桥：`web.ImpWebViewBridge_nPostMessage`、`scripting.ScriptBridge_nPostMessage`、`scripting_viewtexture.RenderViewToSurfaceTexture_nSetRend...`

分析要点：这是「2D Android View → VR 纹理面板」的官方实现，等价于 Meta Spatial 的 Panel。重点看
`RenderViewToSurfaceTexture` 与 `View_nCreateSwapChain`：UI 如何渲染进 SurfaceTexture 再贴到 3D（对应我们 Compose Panel 的合成路径）。

### 3.4 键盘 / 输入法（P1）
- `libgvr_keyboard.so` (2.4MB)：射线/激光键盘交互。
- `libmozc.so` (11.4MB)：Google 日文输入法（mozc）原生引擎，**体积大但与我们中文拼音无关**；主要看 VR 键盘如何把射线事件 → 输入法事件（`KeyboardView_nProcessKeyboardEvent`）。

### 3.5 体积构成（P2，免 IDA 即可出结论）
- 大头：`libcrashlytics*.so`(~33MB)、`libgoogle3.so`(11.9MB)、`libmozc.so`(11.4MB)、`libimpress_jni`(10.8MB)、`libopenxr_loader`(8.5MB)、`libyoutubevrjni`(6.6MB)、`libcronet`(6.6MB)。
- 结论方向：业务 .so（youtubevrjni 6.6 + impress 10.8 + openxr 8.5 ≈ 26MB）才是核心；crashlytics/mozc/cronet 是可替换/可裁的第三方。
  ViriViri 瘦身应先做：**release 构建、仅 arm64、R8、so 压缩/按需加载、去掉未用 ABI 与调试符号**。

## 4. IDA 作业流程（每个目标函数）
1. jadx 定位 Java 侧 `native` 方法，抄下参数类型与调用上下文（谁在什么时机调）。
2. IDA 加载对应 `.so`（arm64-v8a），Exports 搜 `Java_...`，F5 反编译。
3. 重命名参数/结构体（按 Java 类型：jint 曲率、jobject SurfaceTexture、jfloatArray 变换等）。
4. 跟关键外部调用：OpenXR (`xrCreateSwapchain`/`xrEnumerate...`)、EGL/GLES、ANativeWindow。
5. 记录「输入参数 → 渲染效果」映射（例如曲率半径取值范围、网格密度、纹理目标）。
6. 只提炼**算法思路/参数范围/架构决策**，不复制代码，写进本目录的发现笔记。

## 5. 交付物
- `docs/research/youtube-vr/` 下每个主题一份笔记：曲面屏、纹理管线、imp 面板、键盘、体积。
- 对 ViriViri 的**可执行结论**：例如「曲面幕布可用 N×M 网格顶点 + 半径 uniform 实现，参考其 nativeUpdateCurvature 的参数维度」。
- 一份 release 瘦身清单（对照我们 build.gradle）。

## 6. 合规红线
- 仅静态/个人学习研究；不反编译绕过授权、不提取专有素材/商标再分发。
- 借鉴**思路与架构**，ViriViri 代码保持独立实现（GPL-3.0 项目也不应与 YouTube 专有代码混用）。


---

## 7. 新 Session 交接：用 ida-pro-mcp 直接驱动 IDA

本机已装 **IDA Professional 9.2 + ida-pro-mcp v1.0.0**（MCP 协议 2025-06-18）。
逆向在**另一个新 session** 进行；该 session 把 ida-pro-mcp 作为 MCP 工具接入即可，无需手动点 IDA。

### 7.1 服务端点（已验证在跑）
- HTTP 传输（streamable HTTP）：`http://127.0.0.1:<port>/mcp`（POST JSON-RPC；根路径 `/` 返回 404 属正常）。
- 当前有**两个 IDA 实例**：`13337`（PID 13940）与 `13338`（PID 10200），各对应一个打开的 IDB。
- 新 session 开始时先调 MCP `get_metadata`（或 `check_connection`）确认每个实例加载的是哪个文件；
  建议分配：**13337 = `libyoutubevrjni.so`**（曲面/渲染/面板，P0/P1 主战场），
  **13338 = `libyoutube_vr_impress_jni.so`**（imp UI 框架）。键盘用 `libgvr_keyboard.so`，按需在空闲实例打开。

### 7.2 标准 JSON-RPC 握手（脚本/curl 参考）
```
POST http://127.0.0.1:13337/mcp   Content-Type: application/json
  Accept: application/json, text/event-stream
{"jsonrpc":"2.0","id":1,"method":"initialize",
 "params":{"protocolVersion":"2025-06-18","capabilities":{},"clientInfo":{"name":"viriviri-re","version":"0.1"}}}
# 然后 tools/list 看实际工具名；再 tools/call 调用。
```
> 注意：响应可能是 SSE（`text/event-stream`，`data: {...}` 帧），客户端要按 SSE 解析。
> 多数 MCP 客户端（含新 session 的 MCP 接入）自动处理；手写 HTTP 时记得按 SSE 取 `data:` 负载。

### 7.3 ida-pro-mcp 常用工具（以 tools/list 实际为准，典型如下）
- 元信息/导航：`check_connection`、`get_metadata`、`get_entry_points`、`get_current_address/function`。
- 查符号：`list_exports`、`list_imports`、`list_functions`、`get_function_by_name`、`get_function_by_address`。
- 字符串/交叉引用：`list_strings`、`search_strings`、`get_xrefs_to`。
- 反编译/反汇编：`decompile_function(address|name)`、`disassemble_function`、`read_bytes/get_bytes`。
- 标注（写回 IDB，方便后续）：`set_comment`、`rename_function`、`rename_variable`、`set_function_prototype`。

### 7.4 建议作业顺序（每个目标）
1. `get_metadata` 确认当前实例文件。
2. jadx（见 §2/§4）拿到 Java `native` 方法签名 → 参数语义。
3. MCP `get_function_by_name`（名形如 `Java_com_google_android_apps_youtube_vr_framework_view_VrViewFacade_nativeUpdateCurvature`，
   导出名被截断时先 `list_exports` 用前缀 `Java_..._VrViewFacade_native` 过滤拿全名）。
4. `decompile_function` 拿伪代码；重点记录：参数含义、曲率/网格/纹理常量、OpenXR/GLES 调用。
5. `get_xrefs_to` 找调用者，确认调用时机；`set_comment/rename_*` 把结论写回 IDB。
6. 产出到 `docs/research/youtube-vr/<topic>.md`，并在末尾给「对 ViriViri 的可执行结论」。

### 7.5 第一批 MCP 任务清单（按优先级，可直接逐条执行）
- [x] T1 曲面网格/曲率模型 → 见 `01-curved-stage.md`（lull quad mesh 按曲率重建 + 柱面投影公式）。
- [x] T2 视频纹理：`StreamingTextureContainer_nativeUpdateTextureMatrix` + 面板 `nativeUpdateTextureMatrix`（SurfaceTexture texture transform），见 01/03。
- [~] T3 面板锚点/命中体：导出名已定位（nativeUpdateAnchor/Collidability/DismissedOnTouchOutside），未深入反编译（后续需要再挖）。
- [x] T4 渲染初始化/刷新率/俯仰角 → 见 `02-renderer.md`（俯仰角运行时弧度×57.296，刷新率设备枚举 int[]）。
- [x] T5 imp 2D View→VR 纹理面板 → 见 `03-imp-panels.md`（Surface→ANativeWindow→XR swapchain；aspect-fit）。
- [x] T6 射线键盘 → 见 `04-keyboard.md`（VR 键事件翻译成标准 Android KeyEvent/文本；建议我们直接产出 SearchInputAction）。
- [x] T7 包体瘦身清单 → 见 `05-size-budget.md`（核心 so 仅 ~14MB；单 arm64+R8+裁剪）。

### 7.6 产物落点
- 反编译笔记：`docs/research/youtube-vr/01-curved-stage.md`、`02-video-texture.md`、`03-imp-panels.md`、`04-keyboard.md`、`05-size-budget.md`。
- IDA 里重命名/注释随 IDB 保存；关键伪代码片段（短、仅作思路说明）可引到笔记，避免大段照抄专有代码。

## 附：快速复现清单命令
```
# 解包
unzip -o temp/YouTube_1.79.11.apk -d temp/ytvr_x
# Java 侧（推荐）
jadx-gui temp/YouTube_1.79.11.apk
# Native 侧：把 temp/ytvr_x/lib/arm64-v8a/libyoutubevrjni.so 拖进 IDA (arm64)
# 导出 JNI 符号速览
strings -a libyoutubevrjni.so | grep '^Java_com_google' | sort -u
```
