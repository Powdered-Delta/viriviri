# Workbench 外部收起命中层：从「从未生效」到可用

专项记录：2026-10-06。「点击工作台外部收起 Workbench」这个功能自加入起**从未真正生效**，
本次确诊并修复，同时修掉了修复所引入的一个连带回归。

---

## TL;DR

1. `WorkbenchOuterDismiss` 是一块 9×6 m 的隐形平板，本该提供「点工作台外部收起」的命中几何。
2. 它的材质 `alphaMode` 是 `Opaque` 而 `baseColorFactor` 的 alpha 为 0 —— `Opaque` 忽略 alpha，
   所以它一旦被引擎渲染就是**一整面黑墙**。唯一能让它不出现在画面上的手段是 `Visible(false)`。
3. 而 `Visible(false)` 会让实体**退出命中测试**。于是这一层永远点不到 —— 逻辑自相矛盾。
4. 修复：材质改 `Blend`（让 alpha-0 成为真透明）+ 节点改可见 + 代码不再设 `Visible(false)`。
   **"引擎可见 + 材质全透明"是同时满足"不渲染"和"可命中"的唯一组合。**
5. 该修复引入一个回归：舞台**没有命中几何**，于是"按在幕布上再松手"的那次松手会穿过幕布打到
   这块板子上，把刚呼出的 Workbench 又关掉。已修（见 §5）。

---

## 1. 症状

- 点工作台**之外**的空白区，Workbench 不收起（自功能加入起一直如此）。
- 更早的假象：只有点**中心面板内部**的空白才收起 —— 那是 `CenterContentWorkspace` 上的一个
  Compose `clickable`，它一直是**事实上唯一可用**的收起入口。
- 2026-10-06 修复命中层后又出现新症状：在幕布上扣扳机，**按下时呼出 empty，松开时 empty 消失**；
  若按住扳机把指向移到右面板再松手，empty 则保留。

## 2. 证据（如何确诊，方法可复用）

不要看"没日志"就下结论 —— 先排除抓取方式问题：

```powershell
# ❌ 本项目大多 trace 是 Log.d，而 -s TAG:I 只放行 Info 及以上 → 会全部被滤掉
adb logcat -d -s ViriViriWorkbench:I

# ✅ 按 PID 过滤，绕开 tag 与级别问题（最可靠）
$pid = (npx -y metavr shell "pidof com.m0e_n00b.viriviri").Trim()
npx -y metavr shell "logcat -d -v time --pid=$pid"
```

关键一次 dump：Workbench 可见期间**整整 19 秒**，用户点了列表外侧 / 幕布 / empty / 暂停，而

```
outerDismiss click ...        ← 0 次
```

同一份 dump 里同 tag 的 **Debug 级**日志（`applyPlaybackCanvasSlots`、`setVisible slot=…`）正常存在
⇒ 不是过滤问题，是 `onClick` **从未被调用**。

同时排除的假设：
- **几何**：`WorkbenchOuterDismiss.gltf` 是 `doubleSided: true`、经 GLXF 缩放到 9×6 m、无旋转 ——
  朝向与覆盖都不可能打不到。
- **资源缺失**：`WorkbenchOuterDismiss.gltf`（3125 bytes）确实打进了 APK。
- **接线错误**：无参 `Hittable()` 是**正确**的「可接收输入」用法 —— 上游样例
  `temp/Meta-Spatial-SDK-Samples/**/SpatialVideoSampleActivity.kt:623` 的注释即
  "mark the mesh as explicitly able to catch input"。

## 3. 根因

```
WorkbenchOuterDismiss.gltf 的 PbrMaterial：alphaMode: Opaque + baseColorFactor [0,0,0,0]
  → Opaque 忽略 alpha → 该 9×6 m 板子会渲染成纯黑墙
  → 唯一让它不出现在画面上的手段：Visible(false)
     （源文件里 Visibility: {}，而该组件 schema 的 defaultValue 就是 false；
       运行时 attachOuterDismissInput 里又设了一次）
  → 而 Visible(false) 同时使实体退出命中测试
  ⇒ 这一层从设计上不可能被点中
```

注意这条与 AGENTS.md 的规则**正面冲突**：那里写的是「隐藏必须同时关闭命中：只用 `Visible` +
命中控制」。也就是说本项目原本就不存在"隐形但可命中"的受支持模式 —— 该实体是在**违反**这条规则
的用法上设计的。

## 4. 修复（方案 A：三处协同，缺一不可）

| # | 文件 | 改动 |
|---|---|---|
| 1 | `app/scenes/Composition/Main.scene` | `WorkbenchOuterDismiss` 节点的 `Visibility` 由空（默认 false）改为 `visible: true` |
| 2 | `app/scenes/WorkbenchOuterDismiss/materials/Material.metaspatialmaterial` | `alphaMode` 由 `Opaque` 改为 `Blend` |
| 3 | `app/src/main/java/com/m0e_n00b/viriviri/SpatialVideoSampleActivity.kt` | `attachOuterDismissInput` 中删除 `entity.setComponent(Visible(false))` |

第 2 步是让方案成立的关键：材质**本来就是**全透明（`baseColorFactor` 未给 `data` → 导出为 alpha 0，
作者本意即如此），只是 `Opaque` 把 alpha 丢掉了，才逼得代码必须 `Visible(false)` 从而牺牲命中。

**制品层验证**（构建会重跑 `:app:export`，必须验证导出结果而非只看源文件）：

```jsonc
// app/src/main/assets/scenes/WorkbenchOuterDismiss.gltf
"alphaMode": "BLEND", "baseColorFactor": [0, 0, 0, 0], "doubleSided": true   ✓

// app/src/main/assets/scenes/Composition.glxf
{"entity_id":"WorkbenchOuterDismiss"}}   ← Visible 组件已消失
全文件 "Visible" 出现次数：0                ✓
```

## 5. 修复引入的回归：舞台按压归属

**症状**：在幕布上扣扳机 → 按下呼出 empty，松开即消失。

**机制**：舞台**没有命中几何**，这是既定设计 —— 它的点击是几何判定
（`AnalogMediaStageTuningSystem` + `StageRayTargeting`），且 `videoSurface` 显式设了
`MeshCollision.NoCollision`。因此：

```
按下 → 几何判定命中幕布 → onStagePrimaryAction() → Workbench 变可见（empty）
     → 该几何系统随即自我禁用（日志 "disabled while the workbench is visible"）
松开 → ECS 把 onClick 投给射线下的实体：幕布处无几何 → 穿过幕布打到后方那块
       刚变成"可命中"的板 → 误判为"点了工作台外部" → 收起
```

用户发现的绕法（按住后把指向移到右面板再松手）恰好印证：右面板**有**命中几何，会吸收射线。

**修法**：在 `onStagePrimaryAction()` **开头每次重新布防** `suppressOuterDismissUntilMs`。
该系统在按住扳机期间**每帧**都会调用此函数，因此守卫在整个按压期间保持有效，松手那一击必然落在
窗口内被吞掉。

这个做法规避了两个坑：
- 不需要区分 down / up 边沿（`onClick` 本身不携带按压起点信息）；
- 不依赖"从某个边沿起算的固定窗口" —— 刻意按住瞄准很容易超出任何固定窗口，而每帧重新布防天然
  跟着按压走。

它同时覆盖了"Workbench 已可见"那一支：打开状态下点幕布同样不会把它关掉。

## 6. 不变量（不要破坏这些）

- `WorkbenchOuterDismiss` 必须**引擎可见**（不得设 `Visible(false)`），且其材质必须**全透明**
  （`Blend` + alpha 0）。两者必须成对：少了前者就打不到，少了后者就是黑墙。
- 舞台动作（`onStagePrimaryAction`）**绝不能**经外部点击层收起 Workbench —— 依靠每次重新布防
  `suppressOuterDismissUntilMs` 保证。
- 中心面板只有 `WORKBENCH_EMPTY` 路由下点空白才收起；内容路由下保持惰性。
- 收起时的路由复位受 `selected != null` 守卫约束：**未选中视频时不得复位**（有意行为）。

## 7. 授权源 vs 产物（改哪里）

```
app/scenes/**                     ← 授权源，**受版本控制**，改这里
  Composition/Main.scene            节点层级、变换、Visibility
  <对象>/materials/*.metaspatialmaterial   材质（alphaMode 等）
  Main.metaspatial                  工程入口

app/src/main/assets/scenes/**     ← 构建产物（:app:export 生成），已被 app/.gitignore 忽略
```

**改产物无效**，下次构建会被覆盖。

## 8. mse-agent 操作规程（本次实际用过的）

AGENTS.md 要求静态空间布置走 Meta Spatial Editor，不要用 Kotlin 硬编码。headless 用法：

```powershell
$cli = 'D:\Program Files\Meta Spatial Editor\v16\Resources\CLI.exe'
$mse = 'D:\Program Files\Meta Spatial Editor\v16\Resources\mse-agent.exe'

Start-Process $cli -ArgumentList 'serve','-p','app\scenes\Main.metaspatial'   # 起 headless 编辑器
& $mse ping                                                                   # 期望 pong
& $mse list-objects                                                           # 拿 id / schemaId / name
& $mse get-schema --component com.meta.components.Visibility                  # 拿属性名/类型/默认值
& $mse get-properties --id 36 --component com.meta.components.Visibility
& $mse set-property --id 36 --component com.meta.components.Visibility --property visible --value true
& $mse save                                                                   # 写回 app/scenes/**
```

注意：
- 改完 `save` 后**关掉 CLI 进程**，避免它在后续把材质文件回写覆盖。
- `list-all-components` 只返回 Spatial 运行时组件，**不含** `com.meta.components.PbrMaterial`
  这类模型/材质组件 —— 被引用对象文档里的材质取不到，只能改其 `.metaspatialmaterial` 源文件。
- 材质枚举用 PascalCase（已见取值：`Opaque` / `Mask` / `Blend`），导出为 glTF 的
  `OPAQUE` / `MASK` / `BLEND`。

## 9. 验证清单

- [ ] 设备显示 `DEV <当前构建哈希>`（腕部调试面板 / `mode_panel` 调试标签）
- [ ] 点工作台**之外**的空白 → 收起；logcat 出现 `outerDismiss click workbenchVisible=true`
- [ ] 在幕布上扣扳机 → 松开 → empty **保留**（对应日志应为 `suppressed=true`）
- [ ] `WORKBENCH_EMPTY` 下点中心面板空白 → 收起，且出现 `dismissWorkbench source=centerPanelBlank`
- [ ] 内容路由（列表 / 搜索 / 详情）下点中心面板空白 → **不**收起
- [ ] **未选中视频**时收起 → 路由**不**被复位
- [ ] 幕布后方环境是否正常（见 §10 深度写入风险）

## 10. 未验证 / 已知风险

- **深度写入**：该板子现在是"引擎可见"的。若其透明材质仍写深度，可能挡掉工作台**后方**的几何
  （板子在 z = 2.2，环境在其后）。截至本文档最后更新，真机未观察到异常，但该机制未被针对性验证。
- `suppressOuterDismissUntilMs` 的窗口值（`OUTER_DISMISS_SUPPRESSION_MS = 500L`）本身未针对
  长按压做压力测试；当前设计（每帧重新布防）使其不依赖该值，但若将来有人把它改成"只在边沿布防"，
  该问题会复发。
- 舞台之外的"点空白"区域是否存在命中空洞（各面板之间的缝隙）尚未逐区验证。

## 11. 相关提交与文件

| 提交 | 内容 |
|---|---|
| `d10d76e` | 方案 A：场景可见性 + 材质 Blend + 移除 `Visible(false)` |
| `010d8e2` | 修复舞台按压回归（每帧重新布防 suppress 守卫） |
| `ee141b2` / `39cf1ca` | 文档与任务记录同步 |

- `app/scenes/Composition/Main.scene`
- `app/scenes/WorkbenchOuterDismiss/materials/Material.metaspatialmaterial`
- `app/src/main/java/com/m0e_n00b/viriviri/SpatialVideoSampleActivity.kt`
  （`attachOuterDismissInput`、`onStagePrimaryAction`、`dismissWorkbenchFromCenterContent`）
- [ui-structure-and-terminology.md](../ui-structure-and-terminology.md) —— 当前实现与术语（2.1 / 5.3）
- [quest-runtime-notes.md](../quest-runtime-notes.md) —— 设备侧已验边界
