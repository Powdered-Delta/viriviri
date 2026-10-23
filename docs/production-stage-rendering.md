# 正式播放显示实现

2026-10-22：按用户要求以实验验收的 B 方案完全替换正式显示实现。
正式入口只有一套路径，不保留旧独立开孔、旧显示后端或构建切换器。
2026-10-23：保留 Surface 的视频 reshape 候选实机失败，已撤回，暂停逐帧问题未解决。
独立实验只保存在 codex/geometry-overlay-poc 分支，不打入当前包；原理验证提交为 `3a0a3fa`，
方案区别见 [geometry-overlay-poc.md](geometry-overlay-poc.md)。

## 运行链

```text
既有状态 / 视频尺寸
  -> PlaybackStageController（完整最新几何）
  -> PlaybackStageGeometrySystem 请求主线程帧，33 ms 合并
  -> PlaybackStagePanelRenderer
     ├─ 视频最终形状变化：按身份解绑 Surface -> 销毁旧面板 -> 新建 -> 登记 -> 绑定
     ├─ 真实弹幕：保留 Compose / View / Display / Surface，仅 reshape
     ├─ B：弹幕场景材质 RGBA write=0、depth write=DISABLE
     └─ 所有层 ECS local Z = scaled base - effective radius（平面仅 base）
```

视频使用 SDK 默认网格与合成层，不再生成第二个开孔，也不安装自定义视频 mesh creator。
同一最终视频几何不会重复创建；UI、深度或其他策略改变可以单独提交。
播放器、媒体数据、播放位置、真实弹幕数据订阅和既有 UI 回调保留，不因几何更新 prepare、
reload 或 seek。Surface 属于 SDK，应用不会手动 release。

## 所有权和可见性

| 资源 | owner / parent | 可见性与输入 | 深度与输出 |
| --- | --- | --- | --- |
| 视频面板 | Activity 主线程资源更换 / 现有舞台根 | 沿用画布 Visible；NoCollision、零点击按钮 | scaled base-radius；唯一媒体 Surface |
| 弹幕面板 | 原 Compose 注册及几何提交 / 同一舞台根 | 首次成功应用 B 后显示；NoCollision；内容由真实弹幕逻辑控制 | zIndex=2；scaled -0.01-radius，UI Surface 无媒体消费者 |
| 压暗层 | 现有画布可见性 owner / 舞台根 | 保持当前禁用/显示策略 | 原深度；不应用 B |
| 其他工作台 UI | 既有宿主及注册 | 保持现有输入/可见性 owner | 原位置与合成顺序 |

真实字幕功能尚未实现，实验中的伪字幕不会出现在正式入口。
弹幕纹理预算固定 1280×720 dp @160，使用透明 ALPHA_BLEND、无玻璃。
每次 reshape 后重设 B 并断言 UI Surface/Display 身份不变。
ECS 父子链负责世界变换，不重复设置原生 world position/rotation。

## 失败和生命周期

候选面板在登记或绑定前即由 Activity 持有；失败不确认几何，重试先安全清理持有对象。
旧 Surface 解绑失败时不销毁它。完整提交失败时隐藏弹幕，保持最新请求供后续帧重试。
几何输入命中只使用成功的 `appliedLayout`；失败、缺失资源或关闭时没有旧布局兜底。
关闭先设置终止状态并关闭控制器；`disposeProductionVideo` 按身份解绑输出、注销已登记的
场景对象并销毁持有的视频面板，未登记的失败候选也会清理。成功后销毁动态视频 entity，
取消回调；其他注册 UI 由既有 SDK 生命周期清理。
源尺寸/播放/新媒体回调继续通过既有数据链提交，不另建媒体时钟。

## 验证边界

B 在独立 Cozy 实验中获得用户验收。正式入口仍需检查真实选片、横竖屏与比例、
缩放/曲率、抓取、暂停/跳转、前景遮挡、显示隐藏及 2D/VR 切换。
弹幕保持现有完整 viewport，超出视频实际显示矩形的文字在 B 下可能被场景遮住，
需要实机确认；不把实验验收直接等同于正式入口验收。
Windows 构建使用 `scripts/build-windows-debug.ps1`，没有后端选择参数。


打包核验必须检查 APK 只有正式 VR launcher（SpatialVideoSampleActivity），
不含 GeometryExperimentActivity、伪弹幕/字幕代码、实验视频或独立实验场景资源。
避免将“APK 已包含正式代码”误认为“用户实际打开的是正式入口”。

缩放滑条松手或摇杆调节结束时，立即消费最新完整几何。33 ms 是调度目标间隔，
实际速率受视频面板/Surface 重建耗时影响；日志 replaceMs 只计同步替换耗时，
不代表 GPU 完成或解码器首帧就绪。正式 APK 沿用构建脚本的 app-debug.apk 名称。

弹幕基础字号为 38px、描边 4px，测量和绘制共用参数。绘制只使用容器上部 85%，
滚动、顶部固定、底部固定弹幕都遵守同一边界，底部 15% 保持透明，预留字幕。
这是像素排布约束，不改变空间容器形状、B 材质策略或视频几何更新。

## 暂停逐帧推进：已知 bug，延后处理（2026-10-23）

Media3 1.4.1 更换 Surface 会重置首帧状态，允许暂停时输出下一帧。
曾尝试保留 Surface，仅用 SDK reshape 重建视频形状；用户实机反馈：
1. 缩放不同步；2. 进入曲面直接消失。因此该候选失败并撤回。
“当前版本已验收／问题已解决”的记录被用户明确纠正，不作为验收证据。
当前恢复按最终参数创建视频面板，保留 B、Z 补偿、33ms 节奏及字体38/4、底部15%。
暂停逐帧问题仍开放。SDK 文档、Surface 身份和 JVM 构建不能证明视频形状路径正确。
后续方案必须同时验证缩放同步、曲面可见和暂停帧保持；不能重新引入已失败的 reshape。

详细失败过程、实机表现、证据边界及后续验收条件见
[视频 reshape 失败记录](video-reshape-failure-2026-10-23.md)。

用户决定将此问题放到快进／快退阶段一起处理，本轮停止继续修复。
后续检查项统一记录为 [PLAYBACK-001](playback-known-issues.md)。
