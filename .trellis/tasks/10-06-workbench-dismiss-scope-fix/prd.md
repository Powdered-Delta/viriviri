# Workbench 收起范围修正：WORKBENCH_EMPTY 空白可收起 + outer-dismiss 命中诊断

## 背景（用户实测反馈）

用户安装在 `DEV d2737818`（设备 Last Update 2026-10-06 21:05）后反馈两点：

1. **收起范围判定不对**：中心面板空白点击应当**在 `WORKBENCH_EMPTY` 时可以收起** ——
   空列表本身等价于一张背景面。
2. **点工作台周围空区域也无法收起** —— 疑似回归。

## 已核实的根因

`08-25-workbench-dismiss-scrim`（提交 `856356a`）依据该卡 PRD 里的一句断言
「`WorkbenchOuterDismiss` 已经是外部区域的唯一 dismiss owner」，把中心面板的
click-to-dismiss **整条移除**。但那次移除**从未在真机上验证过外层命中层是否真的生效**：

- 场景数据（`app/src/main/assets/scenes/Composition.glxf`）中 `WorkbenchOuterDismiss` 是一个
  `scale [9, 6, 0.025]`、`translation [0, 1.25, 2.2]` 的平面，指向 `WorkbenchOuterDismiss.gltf`；
  这应用把舞台放在 z ≈ 2，因此该平面位于面板**后方**，作为背板在设计上是合理的 ——
  但它的**朝向/法线**是否利于射线命中，未在真机确认。
- 代码侧 `Hittable()` 无参调用是**正确**用法（上游样例
  `temp/Meta-Spatial-SDK-Samples/.../SpatialVideoSampleActivity.kt:623` 注释即
  "mark the mesh as explicitly able to catch input"），`Hittable()` 与 `Hittable(MeshCollision.NoCollision)` 语义不同，前者才是可交互。
- 若外层命中层实际不生效，则移除中心面板回调等于**拿掉了唯一可用的收起入口**，
  与用户反馈的现象完全一致。

## 本次改动

1. **恢复中心面板的 dismiss 回调链**，但把判定改为 `route == WORKBENCH_EMPTY`
   （`CenterContentWorkspace.isBlankSpaceDismissable`）。内容路由（列表 / 搜索 / 详情）下空白
   保持惰性，避免误触关闭 Workbench。
2. **加诊断 trace**：`dismissWorkbenchFromCenterContent(source)` 记录来源
   （`outerDismiss` / `centerPanelBlank`），且外层命中层的 `onClick` 在入口处打印
   `workbenchVisible` / `suppressed`。下一次真机测试即可**确证**外层命中层是否被触达。

## 非目标

- 不改场景数据（`.metaspatial` / `Composition.glxf` / `WorkbenchOuterDismiss.gltf`）。若诊断
  证明外层几何不生效，那是**另一次**需要 Meta Spatial Editor 改场景的修复。
- 不改 `selected != null` 这条复位守卫（有意行为）。

## 验收

- `WORKBENCH_EMPTY` 下点击中心面板空白 → Workbench 收起；内容路由下同样点击 → **不**收起。
- 点工作台之外 → 收起，且 logcat 出现 `outerDismiss click`；**若始终不出现**，
  说明外层命中层未被触达，需单独排查场景几何/朝向。
- `dismissWorkbench source=` 能区分两条入口。
- `.\scripts\build-windows-debug.ps1` 通过（172 单测）。

---

## 补充（2026-10-06 真机确诊 + 方案 A 实施）

### 确诊

PID 过滤的全量 logcat dump（工作台可见期间 19 秒）显示 `outerDismiss click` **0 次**，
而同一 tag 的 Debug 级日志正常存在 ⇒ `onClick` 从未被调用，外层命中层未生效。
几何已排除：`doubleSided: true`、9×6 m、无旋转、网格资源确实打进 APK。

### 根因链

```
WorkbenchOuterDismiss.gltf 的 PbrMaterial：alphaMode: Opaque + baseColorFactor [0,0,0,0]
  → Opaque 忽略 alpha → 该 9×6 m 板子会渲染成纯黑墙
  → 唯一让它不出现在画面上的手段是 Visible(false)
     （源文件 Visibility: {} 而 schema 默认值为 false；运行时 attachOuterDismissInput 再设一次）
  → 而 Visible(false) 同时使实体退出命中测试
  ⇒ 该层从设计上不可能被点中
```

### 方案 A 实施（提交 d10d76e）

1. **场景**：用 mse-agent `set-property`（非手改 YAML）把 `WorkbenchOuterDismiss` 节点的
   `Visibility.visible` 设为 true。
2. **材质源**：`app/scenes/WorkbenchOuterDismiss/materials/Material.metaspatialmaterial` 的
   `alphaMode` 由 `Opaque` 改为 `Blend`，让 alpha-0 成为真透明。
3. **Kotlin**：`attachOuterDismissInput` 不再调用 `Visible(false)`。

### 导出制品验证（构建重跑 `:app:export` 后）

- `WorkbenchOuterDismiss.gltf` → `"alphaMode": "BLEND"` + `baseColorFactor [0,0,0,0]`
- `Composition.glxf` → 该节点已无 Visible 组件；全文件 `Visible` 出现 **0 次**

### 仍待真机确认

- 点工作台之外应收起，且出现 `outerDismiss click workbenchVisible=true`。
- **风险点**：该板子现在"引擎可见"，若透明材质仍写深度，可能挡掉工作台**后方**的环境几何
  （环境在 z=2.2 之后）。若见到幕后环境消失，需关闭该材质的深度写入。
