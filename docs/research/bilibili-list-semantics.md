# B 站「列表」语义：合集 / 系列 / 播放列表 / 收藏夹 / 分P

本文回答一个具体的设计问题：**在 B 站语义下，「播放列表」与「合集」是同一个东西吗？**

结论用于沉浸式工作台右 rail（`PanelSlot.CONTEXT`）的内容设计。

**方法**：读取第三方 B 站客户端 **PiliPlus** 的最新代码作为语义证据源。

| 项 | 值 |
| --- | --- |
| 仓库 | `https://github.com/bggRGjQaUbCoE/PiliPlus` |
| HEAD | `b2d6a5be`（`main`，**2026-10-10**） |
| 本地路径 | `D:\Workspace\_refs\PiliPlus`（**仓库外**，浅克隆，只读） |
| 规模 | 1625 文件 / 23.9 MB |

> 采用 PiliPlus 而非官方文档：它需要**逐个对接**这些接口，字段名与分支条件都是被真实数据逼出来的，
> 因此对"哪个 id 对应哪个对象"比文档更可靠。

---

## 1. 结论

**「播放列表」与「合集」不是同一个东西。** 只在最trivial的意义上相似——**都是可连续播放的有序视频列表**。

它们是**两个不同的 B 站对象**：

| | 合集 | 播放列表 |
| --- | --- | --- |
| 对象 | `ugc_season` | media list |
| identity id | `season_id`（= `ugc_season.id`） | `media_id`（`/pl\d+`、`ml{id}`） |
| 归属 | **一个 UP 主**（`UgcSeason.mid`） | **用户**（或官方频道） |
| 接口 | `/x/v2/space/season/videos`、`/x/polymer/web-space/seasons_archives_list` | `/x/v2/medialist/resource/list`（`type` + `biz_id`） |
| **可否跨 UP** | **否** | **是** |

**决定性判据是归属**：`UgcSeason` 只有一个 `mid`；而播放列表是**用户自建、可跨 UP 收录**的列表。

### 还有一个被普遍混淆的第三项：**系列**

「合集 / 列表」这个说法里的**「列表」并不是播放列表，而是「系列」（`series`）**——
它是 UP 主的另一种自建归组，用 `series_id` 标识，与合集同属 UP、同样**不跨 UP**。

所以那句"播放列表和合集会是一回事"实际上**把三个不同的东西混成了一个**：
**合集（`season_id`）/ 系列（`series_id`）/ 播放列表（`media_id`）**。

---

## 2. 术语表

| 术语 | B 站对象 | identity id | 接口 | 归属 | 跨 UP | UI 出现位置 |
| --- | --- | --- | --- | --- | --- | --- |
| **合集** | `ugc_season`（含 `sections`） | `season_id` = `ugc_season.id` | space `/x/v2/space/season/videos`；web `/x/polymer/web-space/seasons_archives_list` | UP（`UgcSeason.mid`） | **否** | 视频页「播放列表」tab 内的 `合集：{title}` 块；UP 空间「全部合集/列表」；订阅 type=21 |
| **系列** | `series` | `series_id` | space `/x/v2/space/series`；web `/x/series/archives` | UP | **否** | 空间卡片 `seasonId == null ? '列表'` 分支 |
| **播放列表** | media list | `media_id`（`/pl\d+`、`ml{id}`） | `/x/v2/medialist/resource/list` | **用户** | **是** | `MediaListPanel`（`panelTitle` = `favTitle`）；深链 `m.bilibili.com/playlist/pl…` |
| **收藏夹** | fav folder | `media_id` / `fid` | `/x/v3/fav/folder/*` + 同一个 medialist 资源接口 | 用户 | 是 | `/favDetail`；订阅 type=11 |
| **分P** | 稿件内分段 `pages` | `cid`（+ 稿件 `aid`/`bvid`） | 详情内嵌 `pages`（`/x/player/pagelist`） | 唯一投稿 | 否 | `PagesPanel`「视频选集 / 共N集」 |
| **剧集（PGC）** | 番剧 / 课程 season | PGC `season_id`（与合集同名不同域） | `/pgc/view/web/season` | 版权方 | 否 | `EpisodeType.pgc('剧集')` |

---

## 3. 证据

### 3.1 三种类型在代码里是**并列的枚举**，播放列表不在其中

`lib/models/common/episode_panel_type.dart:1-9`（全文 9 行）：

```dart
enum EpisodeType {
  part('分P'),
  season('合集'),
  pgc('剧集'),
}
```

视频详情里的选集面板只有**三种互斥类型**：分P / 合集 / 剧集。**没有"播放列表"这一类** ——
说明在"视频下的选集面板"这个语境里，合集与播放列表**根本不是同一层概念**。

### 3.2 空间卡片的「列表」是**系列**，不是播放列表

`lib/pages/member_season_series/widget/season_series_card.dart:55`：

```dart
'${item.meta!.seasonId != null ? '合集' : '列表'}: ${item.meta!.total}',
```

同一个 model、同一张卡片，仅凭 `seasonId` 是否存在切换标签。**点击后走哪条路**才是关键 ——
`lib/pages/member_season_series/view.dart:79-95`：

```dart
bool isSeason = item.meta!.seasonId != null;
dynamic id = isSeason ? item.meta!.seasonId : item.meta!.seriesId;
// → ContributeType.season / ContributeType.series
// → seasonId: isSeason ? id : null,  seriesId: isSeason ? null : id
```

**「列表」分支用的是 `seriesId`**，即 UP 的**系列**。所以"合集/列表"是
**合集 vs 系列**，**两者都不是播放列表**。

佐证：`member_contribute/controller.dart:34` 里 UP 空间只有**一个** tab
`SpaceTab2Item(param: 'ugcSeason', title: '全部合集/列表')`，其内部是
`seasonsList + seriesList` 两个服务端列表的拼接（`member_season_series/controller.dart:20-23`）。

### 3.3 播放列表是与收藏夹同族的**用户** media list

`lib/models/common/video/source_type.dart:1-40`（全文 40 行）：

```dart
normal    (mediaType: -1,                       playlistSource: PlaylistSource.UP_ARCHIVE),
archive   (mediaType:  1, extraId: 1,           playlistSource: PlaylistSource.MEDIA_LIST),
watchLater(mediaType:  2, extraId: 2,           playlistSource: PlaylistSource.MEDIA_LIST),
fav       (mediaType:  3, extraId: 2,           playlistSource: PlaylistSource.USER_FAVOURITE),
playlist  (mediaType:  3, extraId: 4,           playlistSource: PlaylistSource.MEDIA_LIST),
```

- `archive`（合集路径）与 `playlist` 是**两个不同的 `SourceType`**，`mediaType`/`extraId` 都不同（`1/1` vs `3/4`）。
- 二者**只在 gRPC 传输层共用** `PlaylistSource.MEDIA_LIST` 这个枚举值 —— **传输层归一 ≠ 语义层同一**。
- 收藏夹与播放列表**同为 `mediaType: 3`**（用户 media list 族），只靠 `extraId`/`PlaylistSource` 区分。

`lib/http/user.dart:351-377` —— `getMediaList` 命中 `/x/v2/medialist/resource/list`，
参数 `type` / `biz_id` / `oid` / `otype`（ugc:2 // pgc:24）：
收藏夹 / 播放列表 / 稍后再看**共用同一个通用 media list 接口**，靠 `biz_id` 分派。

`lib/utils/app_scheme.dart:633-659` —— 解析 `m.bilibili.com/playlist/pl(\d+)`：

```dart
'sourceType': SourceType.playlist,
'favTitle': '播放列表',
'mediaId': mediaId,
```

### 3.4 跨 UP 的实证（决定性）

`lib/pages/popular_precious/view.dart:61-67`：

```dart
extraArguments: {
  'sourceType': SourceType.playlist,
  'favTitle': '入站必刷',
  'mediaId': _controller.mediaId,
  ...
```

「入站必刷」是官方**跨 UP** 榜单，却用 `SourceType.playlist` 承载 ——
**播放列表必然可以跨 UP**。

反向证据：`lib/models_new/video/video_detail/ugc_season.dart:3-8`（全文 27 行）：

```dart
class UgcSeason {
  int? id;
  String? title;
  String? cover;
  int? mid;          // ← 单一归属 UP
  List<SectionItem>? sections;
}
```

合集有**且只有一个** `mid`。`lib/pages/video/view.dart:1857` 也以
`seasonId: videoDetail.ugcSeason!.id` + `list: sections` 打开，作用域完全在 UP 内。

### 3.5 命名陷阱（**对本项目直接影响**）

`lib/pages/video/view.dart:1308-1312`：

```dart
final tabs = [
  ...
  if (_shouldShowSeasonPanel) '播放列表',
];
```

**视频页那个 tab 的字面标签就是「播放列表」** —— 但它的显示条件 `_shouldShowSeasonPanel`
实际是 `hasSeasonOrParts`（UGC）/ `hasEpisodes`（PGC）。

**也就是说：这个叫「播放列表」的 tab 里装的其实是「分P + 合集」，不是用户 media list。**

`lib/pages/video/view.dart:1796-1874` 进一步证明两者**并存而非互替**：
`hasParts` → `PagesPanel` / `EpisodePanel(.part)`，与 `ugcSeason != null` →
`SeasonPanel` + `EpisodePanel(.season)` 可以**同时出现**。

---

## 4. 假设对在哪、错在哪

**对的部分**
- 都是可连续播放的有序列表，由同一套播放器管线自动续播；
- 在 PiliPlus 里都能从"列表"类界面进入（`SeasonSeriesCard` 合并合集+系列；`MediaListPanel` 渲染收藏夹/播放列表/稍后再看）；
- 合集 id 会被**重新打包**成 media list 形态供音频播放使用
  （`lib/pages/video/controller.dart:1417-1421`，`PlaylistSource.MEDIA_LIST, extraId = 8`）——
  **这正是最容易让人误以为"它们是同一个东西"的地方：一次内部转换。**

**错的部分**
- 对象不同：`season_id` vs `media_id`；
- 接口不同：`space/season/videos` vs `medialist/resource/list`；
- **归属不同：UP 独占 `mid` vs 用户自建 —— 这是最锋利的一条**；
- "不同的推荐列表"这个描述也不对：合集是 UP 对**自己视频**的**创作式归组**，不是推荐列表；
- 而且卡片里的 `'列表'` 分支是**系列**，所以这句话实际把 **合集 / 系列 / 播放列表** 三者混为一谈。

---

## 5. 对本项目原型的影响

原型的右 rail 我原先标注为 `[播放列表/合集]` —— **这个标签是错的**，它把两个不同对象合并了。

修正为：

| 组件 | 应表示 | 依据 |
| --- | --- | --- |
| 合集/分P 列表（契约里的 `playlist-list` / 选集） | 视频**自身**的选集：合集 `sections` + 稿件 `pages` | `EpisodeType.part('分P')` / `season('合集')` 并列；二者在视频页并存 |
| 播放列表（media list） | **用户**自建、**可跨 UP** 的列表 | `SourceType.playlist`；`/x/v2/medialist/resource/list` |
| 相关视频 | 推荐 | 与本问题无关 |

**建议**：右 rail 的第一个 tab 命名为 **`合集`**（内容 = 分P + 合集），
**不要**沿用 B 站视频页那个有歧义的「播放列表」字样；
真正的**播放列表**是**独立 feature**，是否出现在右 rail 需单独决策。

---

## 6. 未核验项

- 收藏夹与播放列表的 `media_id` 是否共享同一命名空间（代码把 `pl…` 与 `ml{mediaId}` 当作同一 id 空间用，但无证据表明二者不冲突）；
- `save_panel/view.dart:266` 把 `DYNAMIC_TYPE_MEDIALIST` 标为 `'收藏夹'`，而 `app_scheme.dart:654` 把 `pl…` 标为 `'播放列表'` —— 未找到可区分二者的代码；
- 空间接口的 `season`（`SpaceSeason`）与 `ugc_season` 是否同一对象（未读 `models_new/space/space/season.dart`）；
- 以上均为**端点与分支层面**的事实，未做**真机请求**验证（无构建、无抓包）。
