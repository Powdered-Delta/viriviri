# Spatial 坐标系与布局约定

> 本文件回答“坐标轴的正负方向到底是什么”，防止空间布局代码改错方向。
> 结论来源：Meta Spatial SDK 官方文档（architecture / scene）+ 本仓库实测
> 正确的布局反推。修改任何 Pose / Vector3 / Quaternion 之前先读本文。

## 世界坐标系（用户视角速查）

坐标系为**右手系**，单位**米**（SDK 官方：architecture doc “Coordinate
system and units”）。

站在原点（LOCAL_FLOOR，Y=0 = 地板）、**面朝幕布**：

| 轴 | 正方向 | 手势比喻（站立、面朝幕布） |
| --- | --- | --- |
| **+X** | 用户**右手边** | 平举**右**手 → +X |
| **-X** | 用户**左手边** | 平举**左**手 → -X |
| **+Y** | **正上方**（天花板） | 手臂**上举** → +Y |
| **-Y** | **正下方**（地板，Y=0 为地面） | 地板向下为负 |
| **+Z** | **正前方**（面朝幕布的方向） | 面朝幕布 → +Z |
| **-Z** | **正后方**（背对幕布） | 背后 → -Z |

一句话记忆：**+X 右、+Y 上、+Z 是你看的方向**。

## 官方依据

- Spatial SDK **right-handed**；+X 右、+Y 上、**-Z forward (into the screen)**
  （“screen” 指内容/模型正面法线方向；内容放在用户前方时，其正面法线即指向
  用户，因此两种表述不矛盾）。
- `scene.setReferenceSpace(ReferenceSpace.LOCAL_FLOOR)`：地板级参考空间，
  **Y=0 是用户设定/边界的真实地面**，Y 向上。这是 OpenXR LOCAL_FLOOR 语义。
- 官方示例 `scene.setViewOrigin(0f, 0f, 2f, 180f)` 注释为 “Position the
  viewer 2m forward” —— 印证 **+Z 是用户前进/注视方向**，幕布等摆在 +Z 侧。
- 本项目 `setViewOrigin(0f, 0f, 0f, 0f)`：用户位于原点、默认朝向（无额外旋转）。

## 本项目实测布局（方向已由设备验证正确）

当前工作台世界位置（WorkbenchLayoutConfig / SpatialVideoSampleActivity
onVRReady）：

| 实体 | 世界位置 | 方向含义 |
| --- | --- | --- |
| `spatialized_video_panel`（幕布 MEDIA_STAGE） | `(0, stageY, 2)` | 正前方 2m；stageY 默认 1.25 |
| 左 rail `video_selector_panel`（BROWSE） | `(-railX, 1.25, railWorldZ)`，yaw -45° | **-X 侧 = 左手边**，绕 Y 向内转 45° 面向观看者 |
| 右 rail `mode_panel`（CONTEXT） | `(+railX, 1.25, railWorldZ)`，yaw +45° | **+X 侧 = 右手边** |
| transport / nav / 中心 / 键盘 | 幕布子级（TransformParent） | 用**幕布本地系**偏移（见下） |

- `railX ≈ 1.047`，`railWorldZ ≈ 0.953`（由 centerWidth/gap/railWidth/yaw 推导）。
- 实测确认：**+X = 用户右侧**（右 rail 在 +railX 显示正确）。

## 世界系 vs 幕布本地系（重要）

`WorkbenchLayoutConfig` 注释“MediaStage 本地系 -Z faces user”指**子面板相对
幕布的局部偏移**，与世界系不冲突：

- 世界系：+Z = 用户前方（幕布在 +Z 侧）。
- 幕布本地系：面板向观看者凸出用**负 local Z**（离幕布更近 = 向 -Z）。
  例：`centerLocalZ=-0.52`、`transportLocalZ=-0.58`、`keyboardLocalZ=-0.78`
  —— 数字越大负得越多，越靠近用户。
- 本地 Y：正为幕布上方、负为幕布下方；`transportLocalY=-0.72`（幕布下方）、
  `navLocalY=0.72`（幕布上方）。
- 幕布本地系与 SDK 世界系轴向同向（+X 右、+Y 上、+Z 远），只是**原点在幕布
  中心**而不是地板/用户原点。

## 常见坑（实测踩过）

1. **不要把世界系 rails 直接 reparent 成幕布子级而不改 local 值。**
   `TransformParent` 会把已设的世界位姿折算成 local；若 rails 的 Transform
   仍是世界系数值（如 railWorldZ≈0.95、y=1.25），reparent 后会被当 local
   使用 → 世界位置漂移（rail 不在中心两侧）。要么 reparent 后**重设正确的
   local Transform**，要么 rails 保持独立世界坐标并**联动高度**。
2. **rails 世界 Y 必须与幕布高度同步。** 幕布 stageY 可被持久化/抬升，若
   rails 钉死 y=1.25 而幕布抬高，rails 与中心面板垂直错位。
3. **yaw 绕 Y 轴**：rail yaw 是绕世界 Y（竖直轴）的水平转向，不是俯仰。
   俯仰（pitch）用于 transport/nav 倾斜面板（绕 X）。
4. **抬升 = 增大 Y。** 幕布偏低/沉地要往高放时，增大 stage 的 y（世界）或
   本地 localY；LOCAL_FLOOR 下 y=0 就是地面，任何内容 y<0 会“钻地”。
5. **避免低头摆放**（Meta 设计指引：头倾 >±15° 疲劳）：幕布中心建议在
   视线高度附近（站立约 1.4–1.6m），不要把内容压到 1.0m 以下。

## 默认高度参考

LOCAL_FLOOR 下，若幕布中心 stageY=1.25、幕布高约 0.9m：
底边 ≈ 0.8m、顶边 ≈ 1.7m。用户反馈“偏低/沉地”时，把 stageY 增大约
0.2–0.35m（如提到 1.5m，底边到 1.05m）即可避免沉地观感。
