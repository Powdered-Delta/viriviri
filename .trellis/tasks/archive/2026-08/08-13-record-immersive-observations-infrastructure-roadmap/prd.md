# 记录沉浸验证与基础设施路线

## Goal

将最新 Quest 验证结果和下一阶段基础设施决策固化，避免在新版沉浸式 UX 的
Browse/Library 信息架构尚未稳定前，过早实现观看历史 UI、Bilibili 登录凭据或
历史同步。

## Scope

- 记录当前空间/手部追踪漂移为未定归因的设备观察项；定义后续复现时必须采集的
  build hash、操作路径、`ViriViriSpatial` 日志及影响范围。
- 记录“充电专属”推荐项在匿名普通 UGC DASH provider 中可能没有 `data.dash`；
  不在本任务发送身份凭据、实现授权绕过、猜测 MP4 fallback 或改变播放器。
- 定义后续内容可播放性分类与受限内容 UX 的基础边界。
- 定义观看历史基础设施路线：本地历史优先、Bilibili 同步单独 opt-in、同步依赖
  显式登录/安全凭据保管/用户可撤回；本任务不实现它们。
- 明确新版沉浸 UX 稳定前不落地 History/Library panel 或持久化 schema。

## Non-Goals

- 不修改 Spatial tracking、reference space、video mesh、Player、Surface 或 Quest
  deployment.
- 不实现历史列表、断点续播、DataStore/Room、登录、Cookie/SESSDATA/CSRF 存储、
  Bilibili history heartbeat/report/list/delete API。
- 不修复充电视频播放或加入 stream fallback。

## Acceptance Criteria

- 技术与产品决策在 runbook、播放规范和基础设施路线文档中可追溯。
- 文档明确本地/远程历史的隐私默认值与实现前置条件。
- 文档明确充电内容失败不能被误判为普通 DASH codec 故障。
- 不产生运行时代码或 APK 行为改变。
