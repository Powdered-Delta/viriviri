# 抽取内容访问标记原子组件

## Goal

把当前 Bilibili 列表中的充电角标从 app 私有实现提升为可复用的沉浸工作台原子组件。共享层定义访问状态和展示文本，Compose 层通过主题 token 画出可定位的封面角标，应用层只负责 Bilibili 字段映射。

## Requirements

- `:spatial-workbench-core` 定义平台无关的内容访问状态，至少包括普通和充电专属。
- core 提供确定性的角标文案解析；普通状态不产生文案。
- `:spatial-workbench-compose` 提供无需 Bilibili、Activity、Media3、Surface 或 Meta SDK 的紧凑角标 composable。
- Compose 角标接收 `CinemaPalette`，使用 `CHARGING_BADGE` / `CHARGING_BADGE_LABEL` token，不嵌入 RGB/hex 值。
- 应用层 `Recommendation` 使用 core 的访问状态，不再持有 app 私有 enum 或文本映射。
- 维持当前封面右下角布局和 `is_chargeable_season=true` 的严格映射规则。

## Non-goals

- 不增加登录、支付、充电绕过、Cookie、历史同步或播放 fallback。
- 不根据 `rights.elec`、标题、播放失败或 DASH 缺失生成访问标签。
- 不改变 ExoPlayer、MediaSource、Surface、Spatial 场景或 Quest 部署行为。
- 不实现付费/VIP/版权标签；设计只为后续扩展保留位置。

## Acceptance Criteria

- [x] `Recommendation` 使用 shared core 的内容访问 contract。
- [x] 共享 Compose 角标可用任意 palette 的 `CHARGING_*` token 渲染。
- [x] app 不再保留充电状态 enum、文案 resolver 或角标具体绘制。
- [x] core/Compose/app 单元测试覆盖状态、token 映射及 Bilibili `is_chargeable_season` 映射。
- [x] 无 player、Surface、网络或 SDK 依赖进入 core/Compose atom。
- [~] 全部 Gradle 验证：当前环境没有 `JAVA_HOME` 或 `java` 可执行文件，Gradle 在配置前退出；静态 diff、引用、括号结构与 Trellis context 校验通过。
