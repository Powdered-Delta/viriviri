# PRD：顶部导航面板 pitch 符号反了，Search 点不到

- **任务**：`.trellis/tasks/10-20-nav-panel-pitch-sign/`
- **状态**：in_progress
- **报告来源**：用户真机（2026-10-20）「workbench 顶部面板的 search 探不到了，现在是后仰（法线向上）」

## 1. 现象

Workbench 顶部导航条（`mr_panel`，`GlobalNavigation` / `MRPanel`，含 Search 按钮）**法线朝上**
（面板后仰），用户自下而上看不到它的正面，Search 难以瞄准点选。

## 2. 根因（已由源码 + SDK 字节码确证）

**不是单位错**——先排除了这个最可疑的假设：

```
javap -c com.meta.spatial.core.Quaternion
  Quaternion(float x, float y, float z):
    -> Math.toRadians(x * 0.5) ... cos/sin
```

→ `Quaternion(x,y,z)` **接收角度**并自行做半角欧拉→四元数转换。所以 `Quaternion(8f, 0f, 0f)` 就是 8°。

**是符号/方向错了。** 单一面板 Transform 在
`SpatialVideoSampleActivity.kt:1344`：

```kotlin
Transform(Pose(Vector3(0f, layout.navLocalY, layout.navLocalZ),
               Quaternion(layout.navPitchDegrees, 0f, 0f)))
```

- 该面板法线为 **−Z**（朝向用户方向）；
- 绕 **+X** 旋转 +θ 时，−Z 会获得 **+Y** 分量 → **法线抬高（后仰）**；
- 因此 `navPitchDegrees = +8f` 得到的就是「法线向上」，与用户报告**完全一致**。

**几何核算**（默认舞台高度）：

| 量 | 值 |
| --- | --- |
| 舞台根世界坐标 | `(0, 2.14, 2.0)`（`STAGE_DEFAULT_WORLD_Y` / `STAGE_WORLD_Z`） |
| 面板 stage-local | `(0, +0.72, −0.52)` → **世界 `(0, 2.86, 1.48)`** |
| 眼睛（估） | `(0, 1.6, 0)` |
| 面板→眼睛向量 | `(0, −1.26, −1.48)` |
| **指向眼睛所需的向下倾角** | `atan(1.26 / 1.48) ≈ **40°**` |

即 `navPitchDegrees` 应约为 **−40f**，而不是 `+8f`。

## 3. 需求

- R1 `WorkbenchLayoutConfig.navPitchDegrees` 由 `+8f` 改为 **`-40f`**（向下倾斜，朝向眼睛）。
- R2 修正该字段的 KDoc：原文写 "downward tilt ... so it faces the user"，但**符号约定没写清**，
  导致后人无法判断该给正还是负。必须写明：**绕 +X 正角 = 法线抬高（后仰）；负角 = 向下倾斜**。
- R3 同步 `docs/ui-structure-and-terminology.md` §2.2 表格里 `mr_panel` 的 `pitch 8°`。

## 4. 非目标

- **不改** `transportPitchDegrees`（+20）与 `keyboardPitchDegrees`（+20）：这两个面板位于眼睛
  高度或更低，法线朝上是**正确**的，改了反而变坏。
- 不改 `app/scenes`；不改面板注册；不改可见性；不碰播放器 / 单一视频 Surface 不变量。
- **不做**"始终朝向用户"的动态 billboard 行为（那需要一个 system，属后续独立任务）。

## 5. 已知限制（必须记录，不当作已解决）

面板是**刚性**挂在舞台根下的常量姿态，而舞台高度用户可调（`workbench_stage_y`，0.6–3.0 m）。
最优 pitch 随舞台高度变化：

| 舞台世界 Y | 面板世界 Y | 指向眼睛所需倾角 |
| --- | --- | --- |
| 2.14（默认） | 2.86 | **−40°** |
| 3.00 | 3.72 | ≈ −55° |
| 1.50 | 2.22 | ≈ −22° |

所以 **−40° 是针对默认高度 2.14 m 的最优值**。把舞台抬高后仍会重新出现"需要更向下"的感觉。
若这点在实际使用中不能接受，应另立任务做动态朝向，而不是继续在这里调常量。

## 6. 验收

| # | 条件 | 方式 |
| --- | --- | --- |
| A1 | 顶部导航条法线朝**下**，站在工作台前能直接看到 Search 的正面 | **头显** |
| A2 | Search 可被射线瞄准并点选（能打开搜索画布） | **头显** |
| A3 | 导航条内其他元素（ViriViri / 账户 / 设置 / MR 开关）同样可点 | **头显** |
| A4 | transport 与键盘面板的倾角**未被改变**（仍为 +20），观感无回归 | **头显** |
| A5 | `:spatial-workbench-core:test`(128) 与 `:app:testDebugUnitTest`(186) 全绿 | 命令 |
| A6 | `.\scripts\build-windows-debug.ps1` 通过 | 命令 |
| A7 | `app/scenes` 零改动；`git diff --check` 干净；LF | 命令 |

## 7. 关键文件

- `app/src/main/java/com/m0e_n00b/viriviri/WorkbenchLayoutConfig.kt`（`:52` `navPitchDegrees`）
- `app/src/main/java/com/m0e_n00b/viriviri/SpatialVideoSampleActivity.kt:1344`（唯一使用处）
- `docs/ui-structure-and-terminology.md` §2.2
