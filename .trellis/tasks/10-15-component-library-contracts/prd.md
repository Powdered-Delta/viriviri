# PRD：组件库封装 —— 状态合同、组件目录与边界校验

- **任务**：`.trellis/tasks/10-15-component-library-contracts/`
- **状态**：planning
- **动因（用户 2026-10-15）**：把组件按**前端 UI 库**的方式封装，为后续迁移到其他 XR 平台
  （用户澄清的目标是**尚未发售、跑 SteamOS、ARM 架构**的头显硬件；先前"SteamVR"为表述有误）做准备。
  用户同时确认：**规格复用比代码复用更现实、更重要**。

## 1. 现状核实（已逐项验证，非推测）

| 模块 | Meta Spatial 依赖 | 规模 | 可移植性判定 |
| --- | --- | --- | --- |
| `:spatial-workbench-core` | **0** import | 纯 `kotlin.jvm`，**零依赖** | ✅ 完全中立 → **KMP 的理想形状** |
| `:spatial-workbench-compose` | **0** import | 8 文件 / 1274 行 | ✅ 已中立（仅 androidx.compose + core） |
| `:app` | **124 处 import**，横跨 10 个 `com.meta.spatial.*` 包 | 宿主（Activity 单文件 3426 行） | ❌ Meta 专属，**它就是"一个宿主实现"** |

**即：库边界已经存在，且已经中立。** 未封装的是 **9 个 composable 文件 / 1533 行**留在 `app`
（比库里的 1274 行还多）。

### 1.1 耦合的真实形态（比预期轻）

| 文件 | 行数 | 耦合形态 |
| --- | --- | --- |
| `ImmersiveCenterContentPanel.kt` | 29 | `appState: ViriViriAppState = ViriViriApplication.appState`（**默认值逃生口**） |
| `ImmersiveInputMethodPanel.kt` | 42 | 同上 |
| `ImmersiveLeftPanel.kt` | 178 | 同上 |
| `SearchPanel.kt` / `SearchInputPanel.kt` | 49 / 91 | 待核 |
| `MRPanel.kt` | 94 | 待核 |
| `PancakeActivity.kt` | 65 | 宿主（2D），非库候选 |
| **`DanmakuOverlay.kt`** | 97 | **硬读全局**（无参数）：`:38` `collectAsState()`、`:42` `playerSession.player`、`:51` `danmakuStreamSource(...)` |
| **`RecommendationUi.kt`** | **888** | 以 `ViriViriAppState` 为必需参数（`:124`、`:168` 带默认值） |

**两层耦合，必须分开处理**：

1. **调用耦合**：`= ViriViriApplication.appState` 默认值。去掉它即消除"组件自己找状态"。
2. **类型耦合（更深）**：这些组件以 **app 模块的 `ViriViriAppState`** 为参数类型，
   因此**无法搬进 `:spatial-workbench-compose`**（那会造成 compose → app 的反向依赖）。
   真正的解药是**库内 props 类型 + app 侧适配器**。

### 1.2 平台可移植性的分层结论（用户已确认"规格复用更好"）

| 层 | ARM SteamOS 可否复用 | 结论 |
| --- | --- | --- |
| theme.json / token 词汇 / 组件目录与规格 | ✅ **规格级复用，最可靠** | **本任务的主要产出方向** |
| `:spatial-workbench-core`（合同 / reducer / schema / validator） | ✅ 可经 **KMP** 共享 | 高杠杆、低成本 |
| `androidx.compose.*`（当前 compose 模块所用） | ❌ Android 专属 | 若将来要 **Compose Multiplatform**，现在就要避开 Android 专属 API |
| `app`（Meta panel/entity/ISDK） | ❌ | 永远只是"一个宿主实现" |

**结论**：本任务不追求"把 UI 搬到别的平台"，而是让**中立的合同与目录成型、并让边界可被验证**。

## 2. 需求

### R1 组件状态合同（props in / actions out）

- R1.1 每个库候选组件接收 **库内定义的 props 数据类 + 动作回调**，**不得**再以
  `ViriViriAppState` 为参数类型，**不得**有 `= ViriViriApplication.appState` 之类的默认逃生口。
- R1.2 app 侧新增**适配器**，把 `ViriViriAppState` 映射为库 props；`app` 仍然是唯一读全局单例的地方。
- R1.3 动作回调使用**语义命名**（`onPlayPause` / `onSeek` / `onSelectVideo` / `onSubmitSearch` …），
  与主题层的 `ThemeAction` 词汇保持一致，避免出现第二套动词表。
- R1.4 `DanmakuOverlay` 必须去掉硬读全局：改为接收
  「当前视频 id / 弹幕源工厂 / 播放位置提供者」三类 props。
- R1.5 **本任务范围只做小文件**：`ImmersiveCenterContentPanel`、`ImmersiveInputMethodPanel`、
  `ImmersiveLeftPanel`、`SearchPanel`、`SearchInputPanel`、`MRPanel`、`DanmakuOverlay`。
  **`RecommendationUi.kt`（888 行）单列后续切片**——它是中心内容主体，值得独立一轮。

### R2 组件目录（`ThemeComponentRegistry` 填实）

- R2.1 产出**真实**的 `ThemeComponentRegistry` 实例：`componentId → ThemeComponentDescriptor`
  （`kind` / `allowedSlots` / `compatiblePanelClasses` / `implementationRef`）。
- R2.2 覆盖现有库组件与已注册面板的内容组件（含 `SpatialPanelShell` / `WorkbenchPanelAtoms` /
  `SearchComponents` / 具体面板内容）。
- R2.3 已核实：合同已由 Phase 1 交付，但 **app 中没有任何实例**（默认 `ThemeComponentRegistry.EMPTY`）
  → 属"合同存在、表不存在"。本任务补上那张表。
- R2.4 这是 **Penpot 组件清单的映射目标**：Penpot 的组件命名应能一对一落到 `componentId`。

### R3 边界校验（把可移植性从"声明"变成"可验证"）

- R3.1 新增脚本 `scripts/verify-component-boundary.ps1`，对**库模块**强制以下不变量：
  - `:spatial-workbench-core` 不得 import `android.*` / `com.meta.spatial.*` / `com.m0e_n00b.viriviri.*`；
  - `:spatial-workbench-compose` 不得 import `com.meta.spatial.*` / `com.m0e_n00b.viriviri.*`；
  - 库模块内不得出现 `ViriViriApplication`；
  - 报告（warning 而非失败）`:spatial-workbench-compose` 中 `androidx.core.ktx` 等 **Android 专属 API**
    的使用点，作为"Compose Multiplatform 可移植性欠债"清单。
- R3.2 脚本退出码非零即失败，可直接用于提交前检查。
- R3.3 之所以做这条：可移植性若不能自动验证，就会随时间腐化。项目自己已有同类教训
  （k82 的"silently always-false gate"）。

## 3. 非目标

- **不新增 Gradle 模块**（AGENTS.md 明确禁止为"组件化"而先加模块）。现有三模块边界已足够承载。
- **不把组件搬进 `:spatial-workbench-compose`** —— 那是后续切片。**先解耦、再搬家**；
  没有解耦就搬，只是把问题换个位置。
- **不改 `app` 的任何运行时行为**（面板注册、可见性、输入、场景一律不动）。
- **不动 Meta Spatial 代码**、不动 `app/scenes`。
- 不做 `core → KMP` 改造（高杠杆但独立，另立任务）。
- 不改 `RecommendationUi.kt`（后续切片）。
- 不引入 Compose Multiplatform 依赖（只**避免新增** Android 专属用法，不现在就迁移）。

## 4. 验收

| # | 条件 | 方式 |
| --- | --- | --- |
| A1 | R1.5 列出的 7 个组件均无全局读取、无 `appState` 参数类型、无默认逃生口 | 人工 + R3 脚本 |
| A2 | app 侧适配器承担全部 `ViriViriAppState` → 库 props 的映射 | 代码审查 |
| A3 | `ThemeComponentRegistry` 有真实实例，且覆盖现有库组件与面板内容 | 单测断言 id 集合非空且互不重复 |
| A4 | `scripts/verify-component-boundary.ps1` 在**当前**代码上通过 | 命令 |
| A5 | 该脚本能**捕获**一次故意注入的违规（临时给 core 加一行 `import com.meta.spatial.core.Entity` → 必须失败） | 命令（临时验证后还原） |
| A6 | `:spatial-workbench-core:test`（128）与 `:app:testDebugUnitTest`（180）全绿 | 命令 |
| A7 | `app` 运行时行为零变化：未改面板注册、未改可见性、未改场景 | `git status` + 代码审查 |
| A8 | `.\scripts\build-windows-debug.ps1` 通过 | 命令 |
| A9 | `git diff --check` 干净、新增/改动文件全 LF | 命令 |

## 5. 后续切片（本任务不做，登记备查）

```text
slice 2  RecommendationUi.kt（888 行）解耦 + 适配器
slice 3  把 9 个组件搬进 :spatial-workbench-compose（此时才动模块内容）
slice 4  host port 接口（core 中立）+ app 实现它
slice 5  core → KMP
slice 6  第二个主题 fixture（Cockpit）证明 API；到此才评估是否提取独立仓库
```

## 6. 关键文件

- `app/src/main/java/com/m0e_n00b/viriviri/{ImmersiveCenterContentPanel,ImmersiveInputMethodPanel,ImmersiveLeftPanel,SearchPanel,SearchInputPanel,MRPanel,DanmakuOverlay}.kt`
- `spatial-workbench-compose/src/main/java/com/m0e_n00b/spatialworkbench/compose/`（props 类型落点）
- `spatial-workbench-core/.../ThemeCatalogs.kt`（`ThemeComponentRegistry` / `ThemeComponentDescriptor`）
- `spatial-workbench-core/.../ThemeIdentity.kt`（`ComponentId` / `SlotId` / `PanelClass`）
- `scripts/`（既有脚本约定：`build-windows-debug.ps1`）
