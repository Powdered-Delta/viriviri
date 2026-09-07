# 坐标系速查（任务内引用）

任务：09-02-workbench-lift-and-grab
主文档：docs/spatial-coordinates.md（改坐标前必读）

## 用户视角（站立面朝幕布，LOCAL_FLOOR，Y=0=地面）

| 方向 | 轴 |
| --- | --- |
| 平举右手 | +X |
| 平举左手 | -X |
| 上举 | +Y |
| 地板以下 | -Y |
| 面朝幕布（正前） | +Z |
| 背后 | -Z |

## 世界 vs 幕布本地

- 世界：+Z = 用户前方；幕布在 (0, stageY, 2)。
- 幕布本地（子面板）：向用户凸出 = 负 local Z（keyboard -0.78 最近）；
  上 = +Y，下 = -Y。
- 幕布本地与世界轴向同向，仅原点不同（幕布中心 vs 地面原点）。

## 本任务相关要点

1. 抬升 = 增大 stageY（世界）。默认 1.25 → 偏低时提到 ~1.5。
2. rails 世界 Y 必须与 stageY 联动，否则垂直错位。
3. 不要把世界系 rails 直接 reparent 到幕布（TransformParent 折算 local 会漂移）——
   教训见 01-grab-diagnosis.md / PRD 实施记录。
4. yaw 绕 Y（水平转向）；pitch 绕 X（俯仰）。
5. LOCAL_FLOOR 下 y<0 = 钻地。
