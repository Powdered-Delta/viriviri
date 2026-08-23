# 搜索 QWERTY 输入体验重构

## 目标

优化 ViriViri 的 Search workspace 和应用内输入法：以 `docs/prototypes/workbench/index.html` 的空间布局、层级和候选展开行为为视觉基线，以 Gboard 的 QWERTY 拼音输入逻辑、composition、候选和删除/回车语义为交互基线。

本任务明确移除当前 `ChineseT9InputMethod`，不保留 T9 兼容层，不再通过 Registry 同时提供九宫格输入法。目标输入法为纯 Kotlin、离线、可测试的中文拼音 QWERTY 输入法。

## 非目标与边界

- 不创建第二个播放器、MediaStage、视频 Surface、Spatial Entity 或独立 Spatial Panel。
- 不修改 PlayerSession、Media3 播放生命周期和 Surface handoff 契约。
- 输入法核心不得依赖 Activity、Context、Meta Spatial SDK、Media3、网络或语音服务。
- 共享 Compose 组件只接收 state、style、modifier 和 callbacks。
- system IME 和 voice 只由 Search host / Spatial Activity 触发；当前没有真实 Quest speech contract 时保持 `Unavailable`。
- 不自动安装 APK、部署 Quest 或启动应用。
- 不回滚当前主线以及其他功能已有的修改。
- 输入法、Search workspace、左右 rail 和 Spatial host 必须保持模块化，不能让任一 UI 模块直接持有另一个模块的实现对象或生命周期。

## Web Demo 基准布局

Search 仍然是当前 canvas 内的 center workspace route，左右 Detail / Context rail 不变。输入台只占用现有 MediaStage 的下半部，并位于 Transport 之前。

```text
+----------------------+--------------------------------------+----------------------+
|      Detail rail     |              MediaStage              |     Context rail     |
|                      |                                      |                      |
|  current video       |  Search workspace                    |  parts / related     |
|  metadata/comments   |  +-------------------------------+   |  videos / danmaku    |
|                      |  | Search header                 |   |                      |
|                      |  | [search] [clear] [IME] [mic] |   |                      |
|                      |  +-------------------------------+   |                      |
|                      |  | history / hot keywords        |   |                      |
|                      |  | result list when query exists  |   |                      |
|                      |                                      |                      |
|                      |  +----------------------------------+ |                      |
|                      |  |         application keyboard     | |                      |
|                      |  |          above Transport         | |                      |
|                      |  +----------------------------------+ |                      |
|                      |  | compact Transport overlay        | |                      |
+----------------------+--------------------------------------+----------------------+
```

输入台采用 Web demo 的三栏结构。数字/运算区比普通键宽，主键区使用 QWERTY，右侧为固定操作列。

```text
+------------------------------------------------------------------------------+
| Search input console                                                        |
+----------------------+--------------------------------------+------------------+
| Number / operators  | Composition + candidates + QWERTY   | Actions          |
|                      |                                      |                  |
| [ 1 ] [ 2 ] [ 3 ]   | preedit: ni'hao                     | [ Backspace ]    |
| [ 4 ] [ 5 ] [ 6 ]   | 词组 | 单字   你好  你 好 ... [ + ]  | [ Voice ]         |
| [ 7 ] [ 8 ] [ 9 ]   |                                      | [ Enter ]        |
| [ 0 ]               | Q W E R T Y U I O P                | [ Hide ]         |
| [ + ] [ - ] [ * ]   | A S D F G H J K L                  |                  |
| [ / ] [ = ]         | Shift  Z X C V B N M  Symbol        |                  |
|                      | 中/英  ,  .  [       Space       ] ! ? '|                  |
+----------------------+--------------------------------------+------------------+
```

数字区的运算键使用稳定的 action id：`plus`、`minus`、`multiply`、`divide`、`equals`。显示字符和提交字符必须由同一个键定义生成，避免 UI label 与实际输入不一致。

## Input Console Skin Contract

输入台采用类似传统输入法皮肤文件的三层拆分，但第一阶段只使用 Kotlin descriptor，不加载图片、动画、音效或外部二进制皮肤包：

```text
Input method    -> key id / label / editing action / candidate semantics
Input skin      -> zone ratio / spacing / fixed heights / overlay policy
Input style     -> palette-derived colors / key visual roles / disabled treatment
Compose         -> renders method + skin + style, emits callbacks only
```

`InputConsoleSkin` 是资源无关的布局文件模型，当前默认 `gboard-qwerty-v1` 必须集中定义：

- 数字区、主键区、操作区的列权重。
- 输入台区块间距与按键行距。
- composition 与候选条的固定高度。
- 候选展开是否覆盖主键区。

它不能包含 Activity、Spatial 对象、播放器、Surface、网络、词库或业务 callback。`InputConsoleStyle` 继续从 `CinemaPalette` 解析颜色；将来导入 JSON/XML 皮肤时，只新增 parser/adaptor，不能把布局常量重新散落回 Compose。

候选折叠态固定占用组合栏高度：

```text
+---------------------------------------------------------------------+
| composition: ni'hao                                                |
| 词组 | 单字     你好   你 好   拟好   ...                 [展开]       |
+---------------------------------------------------------------------+
| QWERTY rows remain at the same position and height                   |
+---------------------------------------------------------------------+
```

候选展开态固定覆盖主键区，不改变输入台外框高度，不推动历史、推荐、Transport 或 Search header：

```text
+---------------------------------------------------------------------+
| composition: ni'hao                                      [收起]      |
|                                                                     |
| 你好    你 好    拟好    逆好    你好吗    ...                       |
| 自然文本宽度排列，可换行，可垂直滚动                               |
|                                                                     |
|                                                                     |
+---------------------------------------------------------------------+
| QWERTY area is covered by the candidate composition layer            |
+---------------------------------------------------------------------+
```

## 状态边界

### Input method state

`SearchInputSession` 只保存输入法自身状态：

- `inputMethodId`：固定为 `zh-Hans-qwerty`。
- `committedText`。
- `composition`。
- 候选列表及候选模式。
- 中文/英文模式。
- Shift 状态和 symbol layer 状态。
- 候选消费范围所需的 engine data。

优先使用明确的类型/枚举表达模式和键盘层；只有多击时间等短暂引擎信息才放入 `engineData`。不把 keyboard visibility、候选展开、Search history、结果列表或 Spatial 信息放进输入法 session。

### Search workspace state

Search workspace 单独管理：

- keyboard visible。
- candidate expanded。
- search history 和 hot keywords。
- result snapshot。
- search scroll position。
- 返回 Search 前的 canvas / route snapshot。
- system IME 状态。
- voice 状态。

输入法 reducer 只处理按键、删除、候选选择、候选模式和 composition commit。Search workspace reducer 处理键盘显示、候选展开、清空、提交搜索、历史、Back 和系统回调。

### 模块化与解耦边界

本次改造按四层拆分，层之间只通过稳定的不可变数据和 callback contract 连接：

```text
+-----------------------+       state / callbacks       +----------------------+
| Input Method Core     | <----------------------------> | Search Workspace     |
| QWERTY reducer        |                                | route/query/history  |
| Pinyin lexicon        |                                | keyboard/candidates   |
+-----------------------+                                +----------+-----------+
                                                                      |
                                                        render props  | callbacks
                                                                      v
+-----------------------+       host callbacks          +----------------------+
| Shared Compose UI     | <----------------------------> | Spatial/App Host      |
| Search console        |                                | rail slots / MediaStage|
| keyboard/candidates   |                                | IME / voice / routing |
+-----------------------+                                +----------------------+
```

具体约束：

- `ChinesePinyinQwertyInputMethod` 只依赖输入法 contract 和词库接口，不依赖 Compose、Search workspace、推荐数据、左右 rail、Activity、Context、Spatial SDK、Media3 或网络。
- Pinyin lexicon 是独立纯 Kotlin 组件；输入法只依赖 `OfflinePinyinLexicon`，词库可以替换、注入和单独测试。
- `SearchInputPanel` / `CinemaInputConsole` 只负责呈现输入台，不直接访问 `ViriViriAppState`、播放器、Spatial host、Detail 或 Context 数据。
- Search workspace 只向输入台提供 `SearchInputSession`、keyboard visibility、candidate expanded、style 和 callbacks；输入台不能反向读取 Search history、recommendations 或 result list。
- 左右 rail 使用 slot/contract 接入：Detail、Context、推荐/结果列表分别接收自己的 immutable state 和 callbacks。它们不能 import 输入法实现，也不能通过输入法 action 修改彼此状态。
- MediaStage、Transport、Detail rail、Context rail 和 Search workspace 由 host 组合；Search keyboard 只是 MediaStage 内部 UI layer，不拥有 rail、Surface 或 Transport 生命周期。
- 左右 rail 的显隐、折叠和内容切换由 Workbench/App host 决定，输入法只通知 `onDismiss`、`onSearch`、`onSystemIme`、`onVoice` 等中立 callback。
- 共享 Compose 层不得通过全局 singleton、隐式 composition local 或强制类型转换访问业务状态；所有跨模块行为必须显式出现在参数或 host contract 中。
- 任何跨层数据流都必须能沿 `input core -> workspace -> Compose -> host` 或反向 callback 路径追踪，不能在组件内部复制搜索提交、候选生成或 rail 路由逻辑。

## 实施阶段

### Phase 1：清理输入法 contract 和 UI 基线

1. 删除 `ChineseT9InputMethod` 及其专属实现、测试和默认 Registry 引用。
2. 保留通用 `SearchInputMethod`、`SearchInputSession`、候选和 action contract，并为 QWERTY 所需的模式字段建立明确模型。
3. 将 `SearchInputMethodRegistry` 简化为单一默认 QWERTY 方法，或移除仅为 T9 服务的冗余层；由实现决定最终保留形式。
4. 为 `CinemaInputConsole` 接通真实的 `candidateExpanded` 和 `onToggleCandidates`。
5. 移除候选 chip/card/button 外观，改为自然文本候选项。
6. 用固定高度容器和相对定位实现候选覆盖层：展开时覆盖主键区，折叠时不改变键盘位置。
7. 将搜索 header、键盘、候选、Transport 的宽度和上下关系对齐 Web demo 的 MediaStage 中轴。
8. 为左右 rail 定义独立的 state/callback slot contract：输入台只暴露中立事件，不直接引用 Detail、Context、推荐或播放器组件。
9. 将 Search workspace 与 Detail/Context rail 的组合保留在 host 层；输入法组件不得为了布局方便向左右 rail 下发业务 action。

交付检查：候选展开/收起不改变外框高度，不移动 Search header、历史/推荐区、Transport 和左右 rail；替换任一 rail 内容不会要求修改输入法核心或共享键盘组件。

### Phase 2：实现 Gboard 风格 QWERTY 拼音核心

新增纯 Kotlin 的 `ChinesePinyinQwertyInputMethod`，不依赖 Android UI 或平台服务。

1. 实现四行英文字母布局和数字/运算区。
2. 实现中文/英文切换：
   - 中文模式字母追加 composition。
   - 英文模式字母直接追加 committed text。
   - 切回中文清空未提交 composition，但保留 committed text。
   - 第一阶段不做英文预测。
3. 实现 Shift：单次临时大写，英文模式直接输入大写字母；中文模式只影响英文/符号键显示，不改变 Pinyin 规范化结果。
4. 实现 symbol layer 和数字/运算 action。
5. 实现分词符 `'`：
   - 只允许用于 composition 内部切分。
   - 开头、结尾和连续分隔符按确定性规则清理。
   - `xi'an`、`chang'an` 保留合法分词。
6. 实现 Gboard 风格 Backspace：
   - 先删除 composition 最后一个 code point。
   - composition 为空时删除 committed text 的最后一个 code point。
   - 处理 Unicode code point，不能简单按 UTF-16 单元误删。
7. 实现 Enter 两阶段语义：
   - composition 非空：提交当前首选候选或原始 composition，不触发搜索。
   - composition 为空：由 Search workspace 提交 committed query。
8. 候选选择支持部分消费：候选需要表达消费的 Pinyin 范围，已消费部分转为 committed text，未消费 composition 保留并重新生成候选。

### Phase 3：离线词库与候选排序

1. 复用并整理现有 `DefaultOfflinePinyinLexicon` 的本地能力，不引入远程翻译或搜索服务参与 composition。
2. 统一 Pinyin 规范化：小写、合法分隔符、无效字符过滤和分词归一化。
3. 候选生成顺序：
   - 完整短语优先。
   - 常用词组优先于单字。
   - 单字候选排在词组候选之后。
   - 候选结果去重并限制数量。
   - 不完整 Pinyin 前缀也必须有稳定候选。
4. 明确多音字、分词和未消费 composition 的行为，并用固定 lexicon 测试验证。
5. 词库接口保持纯 Kotlin，允许测试注入静态 lexicon。

### Phase 4：Search workspace 状态和路由接入

1. 将 keyboard visibility 和 candidate expanded 从组件局部状态提升到 Search workspace 状态。
2. 将 Search 输入状态、历史、推荐关键词、结果、滚动位置和返回快照集中在 Search workspace 领域模型中。
3. `SearchInputPanel` 只负责把 workspace state 转为 Compose props，并转发 callbacks。
4. 为 Search workspace、Detail rail、Context rail、推荐/结果列表定义相互独立的 host-facing state/callback contract；各模块只接收自己需要的数据。
5. 由 host 负责组合 Search、左右 rail、MediaStage 和 Transport；Search workspace 不直接创建或管理 rail、播放器和 Surface。
6. system IME / voice 只通过 callbacks 更新状态：
   - `Unavailable`
   - `Requesting`
   - `Partial`
   - `Final`
   - `Cancelled`
   - `Failed`
7. system/voice final text 合并规则：有 composition 时先追加原始 composition，再追加 final text，随后清空 composition 和候选；不得静默丢字。
8. Search Back 恢复进入 Search 前的 canvas、route、query、结果和 scroll snapshot。
9. Search 结果点击继续复用现有唯一播放器和 Surface 生命周期。

### Phase 5：测试和质量验证

新增或更新 JVM 测试：

- QWERTY rows、键 id、显示字符和 action 映射。
- 中文/英文切换。
- Shift 和 symbol layer。
- composition 分隔符：合法、开头、结尾、重复分隔符。
- phrase-before-character 候选排序。
- 不完整 Pinyin 候选。
- partial candidate commit。
- Backspace composition 和 Unicode code point。
- Enter 两阶段语义。
- candidate expanded/collapsed 状态。
- 固定高度和展开覆盖 contract。
- InputConsoleSkin 默认几何、正权重和候选覆盖策略。
- system IME / voice final text merge。
- Search history preservation。
- Search Back snapshot restoration。

每个阶段验证：

```text
:app:testDebugUnitTest
:spatial-workbench-compose:testDebugUnitTest
:app:assembleDebug
```

最终运行：

```powershell
.\scripts\build-windows-debug.ps1
```

不运行安装、部署和启动命令。

## 验收标准

### 输入法逻辑

- 代码中不再存在 T9 默认实现、T9 Registry 注册和 T9 测试。
- 26 键 QWERTY 是唯一默认输入法。
- `nihao`、`xi'an`、`chang'an` 能稳定生成候选。
- 候选选择不会丢失未消费 composition。
- 中文/英文、Shift、symbol、Backspace 和 Enter 语义可由 JVM 测试覆盖。
- 词库转换完全离线，不依赖网络。

### 搜索界面

- Search 仍是当前 canvas 的 workspace route。
- 输入台位于 MediaStage 下部、Transport 之前。
- 数字/运算区、主键区、操作区三栏稳定布局。
- 布局几何由 `InputConsoleSkin` 集中配置；视觉颜色仍由 `InputConsoleStyle` / `CinemaPalette` 提供，不依赖图片或音效资源。
- 折叠候选单行显示并有固定展开按钮。
- 展开候选覆盖主键区并可滚动，不增加外框高度。
- 不使用固定深色键盘背景或组件内硬编码颜色。
- 不影响左右 rail、唯一播放器、唯一 Surface 和 Spatial 生命周期。
- Detail / Context / 推荐结果模块可以独立替换和测试；替换 rail 不需要修改输入法核心和共享键盘组件。
- Search workspace 只通过 host contract 与左右 rail 协作，不直接读取或写入 rail 内部状态。

### 未实现能力

- 没有真实 Quest speech contract 前，voice 保持 `Unavailable`。
- 不把“能够编译”描述为“Quest 输入法已完成”。
- 不执行 APK 安装和 Quest 启动验证。
