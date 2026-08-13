# 构建原子化沉浸组件与充电内容诊断

## Goal

让沉浸 Browse 列表能够在播放前展示 Bilibili 明确声明的充电/付费内容标记，同时保持现有单播放器、匿名协议和受限内容边界。为后续原子化组件基建保留可测试的中立数据 contract。

## Scope

- 将 Bilibili 列表响应中的明确权益字段映射到推荐项的访问标签。
- 在推荐和搜索结果的列表行显示“充电专属”标记；未知字段不显示标记。
- 为映射和列表标签文本添加 JVM 单元测试。
- 保存给定 BV1n6uM6nEvJ 的匿名 `view` 诊断结论。

## Non-goals

- 不实现登录、Cookie、SESSDATA、历史同步、充电绕过或支付流程。
- 不通过标题、作者、播放失败或 DASH 缺失推断充电专属。
- 不改变 `createMediaSource`、ExoPlayer、Surface 或 Spatial 场景。
- 不实现 live-room、弹幕或持久化用户数据。

## Data rule

只有 Bilibili 明确的结构化字段才可产生列表标签：`is_chargeable_season == true`。`rights.elec` 表示创作者可接受充电，并不单独证明内容访问受限；`rights.ugc_pay`、`rights.arc_pay` 等付费字段同样不自动改写为充电专属。标题、作者、播放失败和 DASH 缺失都不允许推断角标。

对 `https://www.bilibili.com/video/BV1n6uM6nEvJ` 的匿名 `view -> nav/WBI -> playurl` 探测结果：`view` 和 `playurl` HTTP/API 成功；`rights.elec=0`、`rights.ugc_pay=0`、`rights.arc_pay=0`、`is_chargeable_season=false`；匿名 `playurl` 的 `data` 为空、无 DASH/durl。该 BV 证明“匿名播放受限”不等于“可从公开列表字段确认充电专属”，因此当前列表应显示普通视频，不显示“充电”角标。

## Acceptance criteria

- [x] 推荐和搜索映射将明确 `is_chargeable_season=true` 映射为充电专属。
- [x] 充电专属项目在封面右下角显示固定“充电”标签，背景/文字来自 `CHARGING_BADGE` 主题 token。
- [x] 普通、未知和给定 BV 不显示该标签。
- [x] 单元测试覆盖正向、负向、缺失字段和标签文本。
- [x] 现有播放、单 Surface 和匿名请求行为未改动。
- [~] `:app:testDebugUnitTest`、`:spatial-workbench-core:test`、`:spatial-workbench-compose:compileDebugKotlin`、`:app:assembleDebug`：当前环境没有 `JAVA_HOME` 或 `java` 可执行文件，Gradle 在配置前退出；静态 diff、括号结构与 Trellis context 校验通过。
