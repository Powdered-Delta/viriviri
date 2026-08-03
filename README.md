# ViriViri

ViriViri 是一个面向 VR 视频平台的 Quest 客户端原型。当前以 Bilibili 为首个
平台适配示例，验证推荐、播放、Horizon OS 2D 窗口和沉浸式模式之间的同一播放会话。

> 本文统一使用“沉浸式模式”描述 VR 空间界面，不使用“3D 模式”这一简称。

## 名称

日语中 `B` 与 `V` 的发音通常不作严格区分，因此 `ViriViri` 可以自然地联想到
`Bilibili`；名称本身也保留了 `VR` 的含义。

## 设计思路

目标不是为某一个网站做一次性播放器，而是构建可逐步扩展的通用 VR 视频平台客户端：

```text
平台协议与配置
        ↓
通用推荐 / 播放会话状态
        ↓
2D 窗口与沉浸式模式的不同渲染宿主
```

- **平台 adapter**：平台特有的推荐接口、视频详情、播放地址解析、签名、DTO 和错误
  映射全部隔离在 provider 中。当前示例为 `BilibiliPlaybackProvider`。
- **通用会话状态**：应用级状态保存推荐列表、所选视频、浏览/观看目的地和唯一的
  Media3 `ExoPlayer`。切换 2D 与沉浸式模式时不创建第二个播放器。
- **共享 UI，独立输出**：推荐列表和播放信息可复用 Compose UI；视频输出仍由各宿主
  负责，2D 使用 `TextureView`，沉浸式模式使用 Meta Spatial SDK panel Surface。
- **平台无关的下一步**：新增平台应增加 provider、配置和映射，通用推荐模型、播放
  会话和 UI 不应依赖 Bilibili endpoint、DTO 或凭证。

### 参考与边界

- [PiliPlus](https://github.com/bggRGjQaUbCoE/PiliPlus)：参考 Bilibili 协议边界、
  错误处理和播放地址解析的分层方式。不会复制其代码、资源、名称、图标、文案或凭证。
- [NewPipe](https://github.com/TeamNewPipe/NewPipe)：参考 service / extractor /
  configuration 的隔离原则；本项目保持最小实现，不建立复杂插件系统。
- YouTube VR：参考沉浸式视频浏览与大屏观看的交互方向，不复制其实现或资源。
- Meta Spatial SDK `SpatialVideoSample`：本项目的 Quest / Spatial SDK 上游基础，
  上游提交为 `d3cc1b7`。

## 当前状态

### 已完成

- [x] Quest 2 的沉浸式模式启动入口。
- [x] Horizon OS `PancakeActivity` 2D 窗口路由。
- [x] 沉浸式模式与 2D 窗口共享同一个 Media3 播放会话与输出切换。
- [x] Bilibili 无登录推荐列表。
- [x] Bilibili 无登录视频详情、WBI 签名与 DASH 播放地址解析。
- [x] 沉浸式推荐 panel 的内置可扩展九宫格拼音输入、离线候选和 Bilibili WBI 视频搜索结果；系统 IME 保留为备用输入。
- [x] 推荐项选择、进入观看页、返回推荐页。
- [x] 沉浸式模式和 2D 窗口共享推荐列表、所选视频和观看目的地。
- [x] 2D 页面 header 提供返回沉浸式模式入口。
- [x] Surface 切换不再主动 seek，避免 DASH 分段切换时重复当前片段。
- [x] MR / 非 MR 切换不再改写 panel pose、scale 或 view origin。

### 功能缺失

以下按 PiliPlus 的常见能力对照列出，尚未进入当前 MVP：

- [ ] 倍速播放。
- [ ] 弹幕系统。
- [ ] 登录、Cookie、SESSDATA、账号状态与多账号管理。
- [ ] 直播浏览与直播播放。
- [ ] 离线下载、下载队列、清晰度选择、存储管理和已下载内容播放。
- [ ] 分区、排行榜、收藏、稍后再看与历史记录。
- [ ] 收藏夹浏览、创建、编辑和播放队列。
- [ ] 投币、点赞、评论等互动能力；需在登录与凭证边界明确后实现。
- [ ] 多 P / 选集、分 P 连续播放和播放队列。
- [ ] 合集、系列、收藏夹等内容集合的浏览与连续播放。
- [ ] 图文、动态和混合内容卡片的浏览；需要独立于视频播放的内容渲染宿主。
- [ ] 番剧和课程内容。
- [ ] 会员、付费和专属内容的授权态识别、可观看性提示与合规播放；依赖登录、平台
  授权、地区和版权规则，不能通过绕过限制实现。
- [ ] 清晰度、音轨、字幕、编解码格式和播放偏好选择。
- [ ] 观看进度持久化与跨进程恢复。
- [ ] Bilibili 以外的平台 provider。
- [ ] 平台配置编辑界面与多平台选择。

### 已知问题与待改进

#### 通用

- [ ] Horizon OS / OpenXR 在重复沉浸式模式与 2D 窗口交接时可能间歇性长黑屏或丢失
  runtime input focus；当前没有可靠的应用层根治方案。
- [ ] Bilibili 公共接口不是稳定 SDK 契约，可能因接口、签名、限流、地区、版权或编解码
  能力变化而不可用。
- [ ] 返回沉浸式模式不做自动 yaw 重定位。Horizon OS 的 2D 窗口与系统长按重置会改写
  `LOCAL_FLOOR` reference space，自动补偿会叠加偏移；当前保持 panel 原 Transform，
  由用户手动抓取调整。

#### 2D 窗口

- [ ] 竖屏视频仍可能被拉伸；需要验证并修正 `TextureView` 的视频缓冲尺寸与 contain
  transform。
- [ ] 2D 窗口视频仍可能被拉伸；需要在不同窗口尺寸与源比例下验证黑边、居中和不裁切。
- [ ] 尚无播放/暂停、时间戳、进度拖动、字幕、弹幕、音量和倍速控制。
- [ ] “返回推荐”和视频标题应归入同一观看页 header。
- [ ] 右上角应改为图标形式的搜索与返回沉浸式模式入口。
- [ ] 2D 浏览与观看页的整体信息层级、间距和交互应参考 PiliPlus 重新设计。

#### 沉浸式模式

- [ ] 搜索面板的内置中文词典当前只提供热门短语排序和 ICU 单字回退；后续可通过独立
  `OfflinePinyinLexicon` 扩展为更完整的离线词组词典。
- [ ] 推荐/搜索列表缺少视频封面预览图。
- [ ] 视频详情页尚未正确展示标题、作者、封面、时长、播放量和视频说明等元数据。
- [ ] 推荐列表及后续操作 panel 应在无操作时自动淡出，并可通过交互唤回。
- [ ] 播放控制缺少时间戳、进度、字幕、弹幕和音量控制；后续增加摇杆控制进度和音量。
- [ ] 研究巨幕与曲面屏幕模式。
- [ ] 增加播放器位置、大小和显示形态的直接调整能力。

## 开发与验证

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug --no-build-cache --no-daemon -x :app:export
adb install -r .\app\build\outputs\apk\debug\app-debug.apk
```

环境与设备运行时记录见 [Quest 运行时记录](docs/quest-runtime-notes.md)。

当前基线：

```text
Application: ViriViri
Package: com.m0e_n00b.viriviri
Meta Spatial SDK: 0.13.2
Target device: Quest 2 / Horizon OS Android 14
```

## 致谢

- Meta Platforms 及 Meta Spatial SDK 团队，提供 `SpatialVideoSample` 与 Quest
  Spatial SDK 基础能力。
- PiliPlus 社区，提供了可研究的 Bilibili 客户端分层和错误处理实践。
- NewPipe 社区，提供了服务隔离与可扩展视频平台客户端的设计参考。
- YouTube VR 团队，为沉浸式视频浏览与观影体验提供了产品方向上的参考。

## 许可证

本项目包含并修改了受 Meta Platform Technologies SDK License Agreement 约束的
Spatial SDK 样例材料，相关通知与上游许可证必须保留。项目整体开源许可证尚未最终
确定；在完成上游样例、SDK 与第三方依赖的兼容性审查前，请勿假定全部内容可按单一
开源许可证再授权。
