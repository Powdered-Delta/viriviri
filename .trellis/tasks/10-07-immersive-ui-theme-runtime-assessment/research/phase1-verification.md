# Phase 1（R2–R8）实现与验证记录

日期：2026-10-07。范围：**Phase 0 + Phase 1**（Phase 2 未开工，见 PRD §3.2）。
`app/`、`app/scenes`、Gradle 构建文件**零改动**。

实现由 `trellis-implement` 子代理完成（其在**最后汇报阶段崩溃**，代码已落盘）；
验证由主会话独立复核 + `trellis-check` 子代理对抗性审查两轮。

---

## 1. 交付内容

### 新增（core main，7 个文件）

| 文件 | 职责 |
| --- | --- |
| `ThemeSchema.kt` | `CURRENT_SCHEMA_VERSION` / `SUPPORTED_SCHEMA_VERSIONS` / `ThemeSchemaStep` / `ThemeSchemaMigrationChain`（迁移在校验**之前**；未知与未来版本拒绝，不降级） |
| `ThemeCatalogs.kt` | 可注入目录：`SceneAnchorCatalog` / `EntityCatalog` / `ThemeComponentRegistry` / `OverlaySurfaceCatalog` / `GrabMechanismVerificationCatalog`，统一入口 `ThemeCatalogs`（**可空 = 该项不检查**） |
| `ThemeIdentity.kt` | 五个独立 id 空间：`PanelId` / `SlotId` / `ComponentId` / `EntityRef` / `OverlaySurfaceId`；`PanelClass`；`SlotBinding` + `SlotSharePolicy` |
| `ThemeGeometry.kt` | 旋转顺序 `ROTATION_ORDER`（Yaw→Pitch→Roll）、纯 local-pose 合成（reparent 保持世界姿态）、仅依赖 `kotlin.math` |
| `ThemeGroups.kt` | `ComponentGroupSpec` / `GrabGroupSpec` + `GrabMemberSpec` / `GrabMechanism` / `GrabFollowMode` / `GrabLostTrackingPolicy` / `GrabGroupLifecycle` |
| `ThemeJson.kt` | 手写最小 JSON reader（208 行，仅 stdlib，无第三方依赖） |
| `ThemeJsonParser.kt` | JSON → 模型，产出**带字段路径**的 issue |

### 新增（测试）

10 个测试套件 + `ThemeTestKit.kt` 测试夹具（伪造 catalog、`headsetVerifiedGrabMechanism()` 等）。

### 修改（core）

`ThemeValidator.kt`（935 行，+1006）、`CinemaTheme.kt`（+280）、`MediaOverlayValidator.kt`（+156）、
`ThemeContracts.kt`（+49）、`MediaOverlayContracts.kt`（+41，`DanmakuDepthLayer.surfaceId` → `surfaceIds: Set<String>`）、
`MediaOverlayContractsTest.kt`（4 行，适配字段变更）。

### 新增（fixture）

`themes/cinema/theme.json`（618 行）—— 真实 fixture，由测试从磁盘读取（从测试 CWD 向上查找），
并与 `CinemaTheme.create()` 做**逐字段 + 整对象**比较（非自证式测试）。

---

## 2. 验证证据

### 2.1 主会话独立复核（不采信子代理自报）

| 检查 | 命令 | 结果 |
| --- | --- | --- |
| 测试（强制重跑，不吃缓存） | `.\gradlew.bat :spatial-workbench-core:cleanTest :spatial-workbench-core:test` | **BUILD SUCCESSFUL** |
| 测试计数（读 XML 报告） | `build/test-results/test/*.xml` | **17 套件 / 126 测试 / 0 失败 / 0 错误 / 0 跳过** |
| A10 | `git status --porcelain` 过滤 `app/\|settings.gradle\|build.gradle\|libs.versions` | **无命中** |
| 新依赖 | `git diff --name-only` 是否含构建文件 | **无**；全模块无 `kotlinx.serialization`/`gson`/`org.json`/`jackson` import |
| core 平台中立 | grep `^import (android\|com\.meta\.spatial)` | **无命中** |
| 行尾 | 全部改动/新增文本文件扫 `\r\n` | **全部 LF** |
| 空白 | `git diff --check` | 干净（exit 0） |

### 2.2 既有测试回归

40 个既有测试全部仍通过：`ThemeValidatorTest` 5、`MediaOverlayContractsTest` 7、`WorkbenchContractsTest` 8、
`MediaStageContractsTest` 9、`PlaybackCanvasContractsTest` 7、`TransientMessageContractsTest` 3、`ContentAccessContractsTest` 1。

**关键点**：`ThemeValidatorTest:12` 断言 `ThemeValidator.validate(CinemaTheme.create()).isEmpty()`。
该断言得以保住，是因为
1. 新增的 scene/entity 检查全部经**可空 catalog** opt-in（`catalogs.sceneAnchors ?: return`）；
2. `validate(theme)` 只返回 **error**，warning 需走 `inspect()`。

### 2.3 check 子代理的对抗性审查

**变异验证（证明测试有判别力）**：备份两个 validator，删掉 3 条规则
（`DepthRelationWithoutParent`、`UnknownEntityRef`、`AmbiguousLaneSharing`），重跑得到
**恰好**对应的 3 个测试失败（125 completed / 3 failed），其余不动，然后 hash 校验还原并复跑全绿。

**A5 映射**：15 个 PRD-R5 issue code 全部存在、确实被产出、且都有"删规则即失败"的测试。

**交叉编译**：`:spatial-workbench-compose:compileDebugKotlin` 与 `:app:compileDebugKotlin` 均成功
（仅编译验证；下游唯一引用点是 `ImmersivePlaybackCanvasHost` 使用 `CinemaTheme.create()`，返回类型未变）。

---

## 3. 审查中发现并修掉的真实缺陷（3 个）

### 3.1 A9 静默通过（主会话首先发现）

`ThemeValidator.kt` 原逻辑：

```kotlin
val verification = catalogs.grabVerification
if (verification != null && !verification.isVerified(group.id, group.mechanism)) { add(...) }
```

`grabVerification` 默认 `null` → **默认路径完全不产生 warning**，`NATIVE_PARENT` 静默通过。
这同时违反 PRD A9（"不是静默通过"）与实现自身的 KDoc（`ThemeGroups.kt:31` "never as a silent pass"）。

**更严重的是 `GrabGroupValidatorTest.kt:121` 把这个 bug 当作预期行为锁定了**：

```kotlin
assertTrue(ThemeValidator.validate(theme).none { it is ThemeValidationIssue.UnverifiedGrabMechanism })
```

即"测试迁就实现"——所以 123 全绿并不能说明 A9 满足。

**修法**（保持 WARNING 而非 error，因此既有 `isEmpty()` 断言不受影响）：

```kotlin
val verified = catalogs.grabVerification?.isVerified(group.id, group.mechanism) == true
if (group.mechanism == GrabMechanism.NATIVE_PARENT && !verified) { add(UnverifiedGrabMechanism(...)) }
```

语义：记录缺失（catalog 未注入 **或** 返回 false）→ warning；有正向记录 → 无；`DELTA_MIRROR` → 无（不依赖未验证的 SDK 传播）。
先写失败测试（`NoSuchElementException at GrabGroupValidatorTest.kt:124`）再修，并重写了锁定 bug 的那条断言。

### 3.2 A8 洞：同一 panel 重复声明同一 slot 可伪造 `MIRRORED` 配对

`DuplicateSlotOwner` 原本按 **binding 条目**计数，所以一个冗余自声明就能满足 `MIRRORED` 的"成对"条件。
探针确认 4 种情形都能零 issue 通过（同 panel 两次 MIRRORED 同 mirrorGroup、三次相同声明、
同 panel 两次 INDEPENDENT、真 MIRRORED 对 + 一条重复）。

**修法**：同一 slot 下任何 `panelId` 重复即报 `DuplicateSlotOwner`，且策略检查在
`bindings.distinctBy(SlotBinding::panelId)` 上进行。先写失败测试 `aSlotMayNotBeClaimedTwiceByTheSamePanel`。

### 3.3 三处无判别力的空断言测试

| 位置 | 原断言（恒真） | 改为 |
| --- | --- | --- |
| `ThemeIdentityModelTest:38` | `panels.size == panels.keys.toSet().size` | R7.1 真实不变量："没有 panel 在 slotBindings 中被声明两次" |
| `ThemeIdentityModelTest:73` | `assertEquals(6, whitelist.toSet().size)`（用自己那 6 项构造） | 比较**有序的 wire-name 列表** |
| `ThemePoseCompositionTest:88` | `resolve(a,t) == compose(a,t)`（这就是它的定义） | 有判别力的性质："主题 offset 在 anchor 的**旋转后**坐标系中生效，而不是在世界空间相加" |

另**新增** `ThemeJsonParserTest.unknownActionNamesAreRefusedAtTheParseBoundary`（R8.2 的 action 白名单闭合已实现但原本无测试）。

---

## 4. 已知残留（**不得**当作已保证）

| 项 | 状态 | 归属 |
| --- | --- | --- |
| 设计 §4.3 规则 3（`NATIVE_PARENT` ⇒ 成员父级必须是 handle） | **未强制**。core 拿不到场景层级；原设计把它写成 validator 规则是错的 | Meta adapter（Phase 2/3） |
| 设计 §4.3 规则 5（同一 entityRef 归属多个非父子组） | **未强制**。原设计引用的 `sharedFollow` 字段**在任何合同里都不存在**（设计文档自身缺陷，已更正） | **D8**，建议随 Phase 3 |
| 设计 §4.3 规则 6（`independentlyGrabbable` ⇒ 自带 handle） | **未实现**。需要一个超出 PRD R5 封闭清单的 issue code | **D8**，建议随 Phase 3 |
| 设计 §5.5 item 6（用户调整持久化范围） | **未决定**。原标"Phase 1 必须决定"是错误标签 | **D7** 产品决策 |
| PRD R5 对 `UnknownOverlaySurface` 的第二子句 | 实际由既有的 `InvalidLayer(reason="surface-does-not-support-danmaku")` 产出 | **D9**，已改 PRD 措辞（**不改既有行为**） |
| `WorkbenchLayoutConfig` 降级 | 未做。仍是唯一布局来源 | Phase 2.6 |
| 若干面向 Phase 2–5 的声明字段暂无读取者（`ThemeComponentDescriptor.allowedSlots`/`.kind`/`.implementationRef`、`GrabGroupSpec.restPose`、`GrabMemberSpec.independentlyGrabbable`、`DanmakuOcclusionDomain.requireCrossGroupChecks`、`PanelSpec.isUserAdjustable()`、`ComponentGroup.effectiveLabel`） | 声明但未被消费 | 属**故意**：这些是 Phase 2–5 的合同面，PRD R5/R6 未要求消费者 |
| `ThemeValidator.kt` 935 行 | 偏大，拆分可行但 PRD 未要求 | report-only，未重构 |

---

## 5. 结论

Phase 1 的 R2–R8 **实质完成**，且经两轮独立验证（主会话复核 + 对抗性审查）。
关键结论：**126 个测试全绿本身不足以证明正确性**——第一轮的 3 个真实缺陷（其中 2 个被错误测试锁定）
正是靠"主会话独立复核 + 删规则看红"的变异验证才暴露出来的。
