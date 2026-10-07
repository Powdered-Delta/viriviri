# Review Round 1 — 处置记录

Review 来源：另一 session 对 `docs/immersive-theme-runtime-design.md` + 本任务 `prd.md` 的评审
（2026-10-07）。处置原则（用户指示）：**先把文档统一到"可开工"状态，再谈代码**。

本轮**只改文档**，未改动任何源码、场景或面板注册。

---

## 总体结论

| 项 | 处置 |
| --- | --- |
| Review 的 P0 ×4、P1 ×6、P2 ×4 共 14 条 | **全部接受**，逐条落到文档 |
| Review 的批准建议：Phase 0+1 可批准，Phase 2 runtime 不批准 | **接受**。PRD §3 已把已批准范围收紧为 Phase 0 + Phase 1；Phase 2 转为"方向已确认，执行需单独批准" |
| Review 的 6 条 Phase 2 进入条件（G1–G6） | 文档侧**已完成**；登记在 PRD §10 |
| D3 | 原问题不再成立，拆为 D3.1/D3.2/D3.3 三问（PRD §8.2、设计文档 §10.2） |

---

## 逐条处置

### P0

| # | Review 意见 | 处置 | 落点 |
| --- | --- | --- | --- |
| P0.1 | "只读 presentationPolicies"与"PlaybackCanvas 是唯一可见性来源"冲突；实际输出经有损 bridge 与第二套 reducer | **接受并改写**为完整事实链（`presentationPolicies → visibleSlots → applyCanvasSlots（有损塌缩）→ modules() → applyWorkbenchModules`），并明确"主题策略参与 slot 计算，但最终面板集合由第二套 reducer 重新决定" | 设计文档 §0.1；另修正 §2.5 行 18 为"**仅 `PERSISTENT` 被消费**" |
| P0.2 | PRD 阶段边界不自洽（范围含 Phase 2，但非目标说"不重写 scene"，验收说"不做头显"） | **接受**。已批准范围收紧为 Phase 0 + Phase 1；Phase 2 单列并说明其触碰的边界；非目标重写；验收拆为 §5.1（无头显）/§5.2（需头显）；显式说明 `in_progress` 只是流程记账 | 设计文档 §7.1；PRD §3.1/§3.2/§3.4/§5 |
| P0.3 | "面板迁到场景授权"未处理媒体面板的特殊性 | **接受**。新增面板类型学（`UI_PANEL` / `SCENE_PANEL` / `MEDIA_SURFACE`），并把"主题可改全部面板尺寸"限定为按类型的受限子集 | 设计文档 §2.8、§5.6；PRD R7.4、R9.4 |
| P0.4 | `NATIVE_PARENT` 被写得像已验证能力 | **接受**。拆为"代码结构事实（可证）"与"SDK 行为（不可证，需头显）"；默认组改为"结构等价 + 待验证"；Phase 3.0 设为头显 gate，未通过则改用 `DELTA_MIRROR` 并撤回"行为不变" | 设计文档 §0.5、§4.3、§7 Phase 3.0；PRD R7.5（`UnverifiedGrabMechanism` warning）、A9 |

### P1

| # | Review 意见 | 处置 | 落点 |
| --- | --- | --- | --- |
| P1.5 | panel/slot/component/entity 身份层次混在一起；`DuplicateSlotOwner` 过于绝对 | **接受**。定义五个独立 id 空间（`PanelId`/`SlotId`/`ComponentId`/`EntityRef`/`OverlaySurfaceId`）；`DuplicateSlotOwner` 改为条件化（`sharePolicy` = `EXCLUSIVE`/`MIRRORED`/`INDEPENDENT`），把"既有的一槽两 rail"变成显式声明能力而非删规则 | 设计文档 §4.5、§5.2、§5.3；PRD R7.1/R7.2、A8 |
| P1.6 | "offset 相对 anchor"未定义运行时语义 | **接受**。列出必须先固定的 8 项：anchor pose 所有权、坐标系/单位、旋转顺序、reparent 局部换算、主题静态值 vs 用户调整、持久化范围（建议 per-theme）、切主题/导出/改名兼容、默认姿态单一来源 | 设计文档 §5.5；PRD R7.3 |
| P1.7 | 尺寸/reshape 受控路径未覆盖所有类型 | **接受**。按三类面板给出初始尺寸来源、运行时改尺寸可行性、失败/延迟处理；要求 reshape pending **单一 owner**；用 `themeableGeometry` 白名单避免"JSON 可配但实际不生效" | 设计文档 §5.6；PRD R7.4、R9.5 |
| P1.8 | 弹幕 layer→surface schema 不一致（PRD 定了 `surfaceIds`，设计稿仍写单值/待决策） | **接受**。统一为 `surfaceIds: Set<String>`，并补齐连锁语义：分配域≠复制域、唯一 surface 选择步需确定性、`SHARED`/`ISOLATED` 与多 surface 的合法性规则；"能分配"与"遮挡已实现"分开计分 | 设计文档 §4.4、§9.3；PRD R7.5、§5.2 |
| P1.9 | 两处标注修正方向对但需精确 | **接受**。`DanmakuLaneScheduler` 理由改为"**入口不可达**"（保留"存在 `loadDanmaku() → prepareDanmaku()` 调用"这一事实）；`PanelPresentationPolicy` 改为 **`[实现]（部分）`**，明确只有 `PERSISTENT` 有 reader | 已实际修改 `docs/ui-structure-and-terminology.md` §6.3 / §6.9；PRD R1.2 |
| P1.10 | D3 是内容路由所有权决策，设计稿不能替用户决定 | **接受**。D3 拆为 D3.1（中心面板内容路由）、D3.2（布局/呈现模式）、D3.3（`WorkbenchContent` 是否还有独立语义）；不使用"合并两个 enum"作为题目 | 设计文档 §10.2；PRD §8.2 |

### P2

| # | Review 意见 | 处置 | 落点 |
| --- | --- | --- | --- |
| P2.11 | 范围与头显验收约束冲突 | **接受**。Phase 0+1 可在无头显下完成验收；Phase 2 起无头显时只能标"已接通、未验收" | 设计文档 §9.3；PRD §3.4、§5.2 |
| P2.12 | mse-agent"已可用"证据偏弱 | **接受**。改为"配置指向 MSE v16 且 `-h` 命令表已核实；**场景读写能力未核实**，Phase 2 开工前需实测" | 设计文档 §0.8、§7 Phase 0.3；已实际修改 `docs/immersive-ui-low-code-architecture.md:10`；PRD R1.1 |
| P2.13 | Phase 2 塞太多风险、回滚开关不足 | **接受**。拆为 2.1–2.8 八个可独立回滚的子步；回滚粒度改为**按 panel 的集合**（`PLACEMENT_ADAPTER_PANELS`）；几何（2.1–2.6）与可见性 owner 切换（2.7）必须分开提交 | 设计文档 §7 Phase 2、§8 R22；PRD R9、§7 |
| P2.14 | "自由重组"与"注册一次"边界不清 | **接受**。新增 §13：明确允许/不允许清单，给出"自由重组"的准确表述，并要求**两个主题 fixture（Cinema + Cockpit）**才能证明组件组合不是纸面 schema | 设计文档 §13；PRD R8 |

---

## Review 未提出但本轮一并修正的

| 项 | 说明 |
| --- | --- |
| 设计文档 §6.2 的 owner 表原本混列现状与目标 | 拆为「现状（已核实）」与「目标（尚未实现）」两张表 |
| `docs/ui-structure-and-terminology.md` §2.3 未记录 anchor 悬空 | 已补：声明 12 个 anchor 名 vs 场景 3 个节点、交集为空；并记录 14 处运行时 `Entity.create*` 偏差 |
| `docs/ui-structure-and-terminology.md` §6.3 未记录主题契约零消费 | 已补：零匹配证据 + 真实布局在 `WorkbenchLayoutConfig` |
| 设计文档 §0 摘要曾有"由 SDK 原生移动"作为已证事实 | 已改写为待验证（见 P0.4） |

---

## 仍未解决 / 待确认

| 项 | 性质 | 阻塞什么 |
| --- | --- | --- |
| D3.1 / D3.2 / D3.3 | **产品决策** | 仅 Phase 2.7（可见性 owner 收敛） |
| `mse-agent` 在本项目场景上的读写能力 | **需实测**（编辑器运行 + IPC） | Phase 2.1（场景 anchor） |
| 头显验证 `TransformParent` 抓取传播 | **需实测** | Phase 3.0 gate |
| 本次新增/修改文档的版本控制可见性 | **流程**（review 提示：未跟踪 ≠ 已纳入版本控制） | 正式评审/交接前需 `git add` |

以上三项实测/决策**均不阻塞 Phase 0 + Phase 1** 的开工。
