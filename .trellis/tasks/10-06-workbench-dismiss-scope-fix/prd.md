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
