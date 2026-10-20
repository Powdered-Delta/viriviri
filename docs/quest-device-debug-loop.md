# Quest 设备调试闭环（截图 / 日志 / A-B 验证）

> 适用：在本机对物理 Quest 头显做"改代码 → 装机 → 观察 → 判定"的循环。
> 不是运行时行为文档（那在 [quest-runtime-notes.md](quest-runtime-notes.md)），也不是架构文档。
> 本文所有命令与数值都在 2026-10-20 的 Quest 2 / Horizon OS（Android 14, SDK 34）上实测过。

---

## 1. 一次性准备

| 项 | 值 |
| --- | --- |
| 设备连接 | `adb connect 192.168.0.234:5555`（WiFi adb） |
| 包名 | `com.m0e_n00b.viriviri` |
| 主 Activity | `com.m0e_n00b.viriviri/.SpatialVideoSampleActivity` |
| 帧缓冲 | **3664 × 1920**，左右并排立体（单眼 ≈ 1832 × 1920） |
| Root | Magisk；`su -c id` 需先在 Magisk app 里放行 `Shell` |

**adb 位置**（不在 PATH 时）：

```powershell
$adb = "C:\Users\N00b\AppData\Local\Android\Sdk\platform-tools\adb.exe"
```

### 1.1 WiFi adb 会反复掉线

表现：命令卡在 `- waiting for device -`，或 `device 'x:5555' not found`。
处理：**每条长命令前先重连**，并确认设备在位。

```powershell
& $adb connect 192.168.0.234:5555
& $adb devices      # 必须看到 "<ip>:5555  device"
```

> 教训：一次 `install + force-stop + start + logcat` 串在一个命令里，中途掉线会让后半段静默失败，
> 却仍然打印出上一轮的日志。**串行长命令必须分步验证。**

---

## 2. 构建矩阵

三个构建变体，用途不同，**不要混用**：

| 用途 | 命令 | 关键 BuildConfig |
| --- | --- | --- |
| **人工验证**（交给用户手测） | `.\scripts\build-windows-debug.ps1` | `INPUT_INJECTION_ENABLED=false` |
| **诊断：关掉手写视频网格** | `.\gradlew.bat :app:assembleDebug "-Pviriviri.noVideoMesh=true"` | `NO_VIDEO_MESH=true` |
| **自动化：启用输入注入**（只给自己用） | `.\gradlew.bat :app:assembleDebug "-Pviriviri.inputInjection=true"` | `INPUT_INJECTION_ENABLED=true` |

`build-windows-debug.ps1` 会跑 `:app:testDebugUnitTest` + `:app:assembleDebug`，并打印
**APK 路径 / 体积 / SHA-256 / Build SHA**——把 SHA 记下来，后面用来证明装的是哪一版。

### 2.1 PowerShell 会吃掉 `-P` 参数（必踩）

```powershell
.\gradlew.bat :app:assembleDebug -Pviriviri.inputInjection=true      # ✗ 报 Task '.inputInjection=true' not found
.\gradlew.bat :app:assembleDebug "-Pviriviri.inputInjection=true"    # ✓ 必须加引号
```

### 2.2 验证属性真的生效

不要相信"构建成功"，直接读生成物：

```powershell
Select-String -Path app\build\generated\source\buildConfig\debug\com\m0e_n00b\viriviri\BuildConfig.java `
  -Pattern 'INPUT_INJECTION_ENABLED|NO_VIDEO_MESH'
```

### 2.3 配置缓存

`build-windows-debug.ps1` 已带 `--no-configuration-cache`。手动调 `gradlew` 时若改了 Gradle 属性却没生效，
加 `--no-configuration-cache` 再试。

---

## 3. 安装与"确认装的是哪一版"

优先用现成脚本（含 SHA 校验与可选启动）：

```powershell
.\scripts\install-quest.ps1 -Apk app\build\outputs\apk\debug\app-debug.apk
```

手动安装时**必须看输出**：

```powershell
& $adb -s 192.168.0.234:5555 install -r -t (Resolve-Path 'app\build\outputs\apk\debug\app-debug.apk').Path
# 期望: "Performing Streamed Install" + "Success"
```

然后**用系统时间戳确认**（安装失败时前面的步骤会静默跳过）：

```powershell
& $adb -s 192.168.0.234:5555 shell "dumpsys package com.m0e_n00b.viriviri | grep lastUpdateTime"
& $adb -s 192.168.0.234:5555 shell "pidof com.m0e_n00b.viriviri"
```

> 教训：曾出现 `install` 输出被管道吞掉、`lastUpdateTime` 仍是两小时前，而后续日志看起来"正常"——
> 实际测的是旧包，整轮结论作废。**每次装机都要核对 `lastUpdateTime` 或 APK SHA。**

### 3.1 体积基线

从源码树 clean 构建约 **168 MB**。如果某个 APK 是 **350 MB**，那是历史累积残留，
不要拿它当"真实产物"比较，也不要从体积推断内容（先 `clean` 再构建）。

---

## 4. 截图流程

### 4.1 正确做法：`adb screencap`

```powershell
$full = "$env:TEMP\vv-full.png"
& cmd /c "`"$adb`" -s 192.168.0.234:5555 exec-out screencap -p > `"$full`""
```

得到 3664×1920 的左右并排立体帧（实测 2.9–5 MB PNG）。
**先看整帧缩略图**，需要细节再裁单眼。

### 4.2 单眼裁切（用户提供的经验值）

```text
crop W=1600  H=900  X=2017  Y=510
```

本地裁切（Pillow；`python` 是 Store 占位程序，用下面两个之一）：

```powershell
$py = "C:\ProgramData\miniconda3\python.exe"     # 或: uv run --no-project python
@'
from PIL import Image
im = Image.open(r"C:\Users\N00b\AppData\Local\Temp\vv-full.png").convert("RGB")
im.resize((1200, int(1200*im.size[1]/im.size[0]))).save(r"C:\...\vv-full-small.png")   # 整帧缩略
im.crop((2017, 510, 2017+1600, 510+900)).save(r"C:\...\vv-eye.png")                    # 单眼
print("full:", im.size)
'@ | Set-Content "$env:TEMP\crop.py" -Encoding utf8
& $py "$env:TEMP\crop.py"
```

### 4.3 ⚠️ 裁切会骗人

单眼 crop **本身会切断画面内容**。曾把 crop 边界当成"面板被裁切"的 bug 证据，浪费了一轮排查。

**规则：任何"裁剪/边缘异常"的判断，先用整帧缩略图确认是真现象，再裁切看细节。**

### 4.4 不要依赖的截图方式

- `npx -y metavr capture screenshot -o out.png` → 实测产出 **0 字节**文件，不可用。
- 优先用 `exec-out` 而非 `adb shell screencap`：后者在 Windows 上可能被换行转换破坏 PNG
  （未在本项目实测，但 `exec-out` 是稳妥写法）。

---

## 5. 日志流程

### 5.1 本项目固定的 tag

| tag | 来源 | 用途 |
| --- | --- | --- |
| `ViriViriAspect` | `SpatialVideoSampleActivity` | 曲率应用、视频面板 reshape、形状/尺寸 |
| `ViriViriCurve` | 同上 | 启动时恢复的曲率值、面板形状摘要 |
| `ViriViriBackdrop` | 同上 | 层序候选与**实际写入**的 `renderMode`/`layerZIndex` |
| `ViriViriTarget` | `AnalogMediaStageTuningSystem` | 瞄准是否命中舞台；含 `centre=` / `half=` **真实舞台位姿** |
| `ViriViriWorkbench` | 多个 | 工作台显隐、模块、outer-dismiss |
| `ViriViriInjection` | `DebugInputBridge` | 合成输入（仅注入构建） |

> 读舞台真实位姿就来 `ViriViriTarget` 的 `centre=(x,y,z) half=(w,h)`——分析几何前先取这一行，
> 不要用代码里的默认常量假设舞台位置。

### 5.2 `logcat -d` 只给缓冲尾部

**不要**用"某 tag 0 条"推断"这段代码没跑"。缓冲可能只有几十秒，也可能几个月（取决于刷屏量）。

- 要看某个动作的效果：**先 `logcat -c` 清空，再触发动作，再读**。
- 要看连续过程：起一个持久抓取（写文件），不要反复 `-d`。

```powershell
& $adb -s 192.168.0.234:5555 logcat -c
# ...触发动作...
& $adb -s 192.168.0.234:5555 logcat -d -v time -s ViriViriAspect:I ViriViriBackdrop:I
```

带级别过滤时注意：`-s TAG:I` 会保留 `I/W/E`，但 **丢掉 `Log.d`**。若某段是 `Log.d`，要么在代码里提升级别，
要么不加 `:I` 过滤。

### 5.3 串行命令里的坑

`am force-stop` 有时对沉浸式前台应用不立即生效；一定要用 `pidof` 验证进程是否真的换了。
实测出现过 `am start` 之后 pid 未变（复用了旧进程），导致"重启后的日志"其实是旧会话的。

---

## 6. 标准 A/B 调试闭环

**一次只动一个旋钮**，并且每一步留三件证据：**APK SHA + 关键日志行 + 截图**。

```text
1. 定位假设        写出"如果 X 成立，则 Y 会变化"
2. 一个旋钮        改一个常量 / 加一个 buildConfigFlag，不要顺带修别的
3. 构建            记下 SHA-256
4. 装机            核对 lastUpdateTime / pid
5. 清日志           logcat -c
6. 触发            手动操作，或（注入构建下）广播驱动
7. 取证据          日志 + 截图
8. 判定            假设成立/否证；否证就明确记录"这条已排除"
```

### 6.1 用构建期开关做对照，而不是靠"记得别发那条命令"

本项目的两个开关（见 `app/build.gradle.kts`）：

```kotlin
buildConfigField("boolean", "INPUT_INJECTION_ENABLED", ...)   // 默认 false：人工验证包不含注入路径
buildConfigField("boolean", "NO_VIDEO_MESH", ...)             // 默认 false：诊断用，关掉手写视频网格
```

这样"人手测的包"在**构建产物层面**就没有那条可达路径，而不是靠约定。

### 6.2 否证也要记录

每次否证写进任务/研究文档，附上是怎么测的。否则下一轮（或下一个 session）会把同样的假设再试一遍。
今天的例子：射线遮挡测试 0/117 → **"环境几何遮挡"这条已排除**（但要注意该测试用的是旧舞台位姿，
换位姿后需重算）。

---

## 7. 面板 ↔ Display 对照（输入相关）

`dumpsys display` 可以拿到每个面板的 VirtualDisplay。本项目的固定分布：

| displayId | 尺寸 | touch | 在 `mViewports` | 面板 | 注册方式 |
| --- | --- | --- | --- | --- | --- |
| 300 | 1725×495 | **NONE** | ❌ | `controls_id` | LayoutXML |
| 295–299 | 各异 | **NONE** | ❌ | center / danmaku / backdrop / left rail | ComposeView |
| 301 | 1950×480 | **VIRTUAL** | ✅ | `mr_panel` | IntentPanel |
| 302 | 1805×2320 | **VIRTUAL** | ✅ | `video_selector_panel` | IntentPanel |

结论：**只有 `touch VIRTUAL` 且在 `mViewports` 里的面板有真实输入管道。**
View 类面板（XML/Compose）在系统输入层没有 viewport，`PanelSceneObject.onInput` 会返回 `true`
但 `View.isPressed` 保持 `false`——即"被消费但没有效果"。

按 display 注入（需要 shell 的 `INJECT_EVENTS`）：

```powershell
& $adb -s 192.168.0.234:5555 shell "input touchscreen -d 300 tap 599 82"
# 注意参数顺序：<source> 在 -d 之前
```

完整取值：

```powershell
& $adb -s 192.168.0.234:5555 shell "dumpsys display | grep 'DisplayInfo{'"
```

---

## 8. 陷阱清单（都是实测踩过的）

| 现象 | 真因 / 处理 |
| --- | --- |
| `Task '.inputInjection=true' not found` | PowerShell 拆了 `-P` 参数 → 加引号 |
| 日志 tag 一条都没有 | `logcat -d` 只返回尾部；或该 tag 是 `Log.d` 被 `:I` 过滤掉 |
| 装机后行为没变 | `install` 静默失败；核对 `lastUpdateTime` |
| "重启后"日志其实是旧的 | `am force-stop` 未生效，`pidof` 未变 |
| APK 350 MB | 历史累积残留；`clean` 后约 168 MB |
| 截图里面板"被裁切" | 可能是单眼 crop 的边界；先看整帧 |
| `PanelSceneObject.getRootView()` 编译不过 | Kotlin 侧是属性：`panel.rootView` / `panel.display` |
| `registerReceiver` 在 SDK 34 抛异常 | 必须传 `Context.RECEIVER_EXPORTED` |
| 合成输入把真实手柄弄失灵 | `PanelInputListener` 每面板只有一个 hover owner；必须用真实手柄实体作 source |
| WiFi adb 掉线 | 分步执行，每步前 `adb connect` |

---

## 9. 相关文档

- [quest-runtime-notes.md](quest-runtime-notes.md) — 运行时行为、已知问题、reference space
- [ui-structure-and-terminology.md](ui-structure-and-terminology.md) — 面板/槽位/实体的当前实现
- [spatial-coordinates.md](spatial-coordinates.md) — 坐标系与正负方向约定
- [research/immersive-input-injection-testing.md](research/immersive-input-injection-testing.md) — 输入注入可行性研究
- [research/cozy-vs-passthrough-investigation-prompt.md](research/cozy-vs-passthrough-investigation-prompt.md) — 进行中的排查交接
- `scripts/build-windows-debug.ps1` / `scripts/install-quest.ps1` — 构建与安装
