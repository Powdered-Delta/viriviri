# 视频 reshape 失败记录（2026-10-23）

状态：**候选失败，已撤回；暂停逐帧推进已记为已知 bug，延后到快进／快退阶段处理。**

用户于 2026-10-23 决定暂缓修复；当前跟踪见
[播放已知问题 PLAYBACK-001](playback-known-issues.md)。

本记录针对正式播放入口的视频面板。用户明确纠正了先前的“验收通过”记录：
该候选没有验收通过，实机出现“缩放不同步”和“进曲面直接消失”。
不得把本轮 Windows 构建、JVM 测试或此前误记的结论作为视觉验收证据。

## 1. 为什么引入这次改动

此前正式路径在每次尺寸或曲率变化时，按最终宽、高、半径创建新的视频
PanelSceneObject；旧媒体 Surface 按身份解绑后，旧面板才会销毁。
文字覆盖层保留 View、Display 和 Surface，仅更新形状，并继续应用 B。

用户先确认字体大小可用，随后反馈调节几何时视频逐帧推进，并补充：
**“暂停时也会往前走”。**

Media3 1.4.1 源码显示：切换输出 Surface 会重置首帧状态，允许在暂停时
也输出首帧。因而“反复换 Surface”能够解释暂停画面逐帧前进，
但这个解释不能证明任何替代几何实现正确。

本轮试图保留视频解码 Surface，通过 SDK reshape 更新几何，避免反复触发首帧。

## 2. 被撤回的候选具体改了什么

候选不只改变了动画步长，而是改变了视频显示资源的更新方式：

| 项目 | 候选之前／当前恢复的路径 | 失败候选 |
| --- | --- | --- |
| 视频显示对象 | 最终视频几何改变时新建 | 保留同一 PanelSceneObject |
| 解码 Surface | 随新视频面板更换 | 保留同一 Surface |
| 视频几何更新 | 从最终参数创建面板 | 对现有面板调用 reshape |
| 播放器交接 | 按身份解绑旧输出、绑定新输出 | 几何更新不再绑定／解绑输出 |
| 视频像素预算 | 使用既有固定配置 | 未调用 resize 改像素缓冲 |
| 文字覆盖层 | 保留资源、更新形状并重设 B | 保持相同策略 |

失败候选的实际调用链为：

```text
尺寸／曲率输入
  -> PlaybackStageController 派生完整最终布局
  -> 主线程几何帧（33 ms 合并）
  -> PlaybackStagePanelRenderer
     -> 对 VIDEO 也调用 panel.reshape(videoPanelConfig(layout.geometry))
     -> 检查 Surface / Display 身份保持不变
     -> clearCrop()
     -> setScale(1, 1, 1)
     -> 为各层提交 ECS local Transform
```

Activity 当时使用一个仅负责首次创建／绑定的候选方法，替代几何变化时的视频重建。
这个候选方法和对应的就绪标记已撤回；当前源码恢复为 replaceProductionVideo。

候选继续使用统一派生的最终尺寸、有效半径及 local Z 补偿，
没有新增独立开孔、手工视频网格、UV 缩放或另一套播放器。
也没有用 seek、prepare、reload 或修改播放／暂停状态来补偿画面。

因此，**即使已经传入完整配置、清除 crop、设置原生 scale=1，
并保持 Surface 身份不变，这次视频 reshape 仍然没有通过实机检查。**

## 3. 实机失败表现及证据边界

### 3.1 缩放不同步

- 触发操作：使用该候选调整尺寸／缩放。
- 用户报告：**“缩放不同步”**，并明确表示原来的问题回来了。
- 结论：该候选不能保证尺寸变化时显示结果同步，不能继续作为正式路径。
- 尚未取得能够区分视频纹理、显示几何、裁切边界各自变化量的测量记录；
  不把“某个具体矩阵、UV 或开孔参数没有更新”写成已确认根因。

### 3.2 进入曲面时直接消失

- 触发操作：切换／调节进入曲面。
- 用户报告：**“进曲面直接消失”**。
- 结论：曲面可见性检查失败，不能只因 API 调用成功或参数读回正确而放行。
- 本轮没有记录首次消失的精确半径、缩放倍率、原生层状态或异常堆栈。
  因此不能断言消失一定由 Z 补偿、剔除、层销毁或裁切中的某一项造成。

上述是用户实机反馈，不是代理通过截图或自动化复现测得的结果。
本轮没有为这两个现象采集新的截图或视频。暂停逐帧是否被该候选消除，
也没有形成独立、有效的验收结论，不能写成“暂停修好了，只剩几何问题”。

## 4. 哪些事实已确认，哪些仍是假设

| 事项 | 当前证据与结论 |
| --- | --- |
| Media3 换输出后的首帧机制 | 已核对 1.4.1 源码；暂停时仍可能释放首帧 |
| SDK reshape 的实现意图 | 0.13.2 文档及本地字节码显示重建 PanelShape，并复用 PanelSurface |
| Surface 保持同一身份 | 候选加入了运行时身份检查；这不是几何正确的证明 |
| 缩放同步、曲面可见 | 用户明确报告失败 |
| reshape 为什么造成这两个现象 | 尚未定位到具体 SDK／原生状态，不能直接定性为某个底层缺陷 |
| 参数公式和 JVM 测试 | 能验证应用层派生值与提交逻辑，不能验证头显像素或原生裁切 |
| 恢复旧几何路径后的暂停问题 | 仍然开放，没有被回退本身解决 |

可能继续排查的范围包括：已有 SceneObject 的原生状态是否完整更新、
网格与合成层是否使用一致的最终形状、裁切与变换是否保持一致。
这些只是待证伪方向，不能替代实机证据，也不能作为再次启用相同候选的理由。

## 5. 适用范围：不能泛化成“所有 reshape 都不可用”

本次否定的是：**在当前正式视频链中，保留已有视频 PanelSceneObject，
通过 reshape 更新尺寸／曲率，以替代最终参数创建视频面板。**

弹幕等文字覆盖层保留 UI 资源、更新形状并在之后重新应用 B 的路径，
没有被这次视频失败单独证伪，当前继续保留。
不能为了回退视频候选，顺手恢复重建文字 UI 的闪烁问题。

同样，本记录不能证明所有 SDK 版本、所有面板类型或所有 reshape 参数都会失败。
当前工程的实机失败已足以拒绝这一候选，无须把它扩大成未经验证的普遍结论。

## 6. 已采取的回退和当前状态

1. 撤销“已验收／已解决”的记录，重新打开暂停逐帧问题。
2. 恢复按最终参数新建视频面板，移除视频几何提交中的 reshape。
3. 保留 B、统一 Z 补偿、33 ms 调度、38px 字体／4px 描边和底部 15% 预留区。
4. 取消失败候选的暂存；提交尝试尚未产生新的 Git commit。
5. 画图 skill 的 ignore 规则仍保留，与本次视频回退无关。

| 制品 | 构建／测试 | APK SHA-256 | 状态 |
| --- | --- | --- | --- |
| 失败的保留 Surface／视频 reshape 候选 | Windows 构建成功；240 项 JVM 测试通过 | B91EF302EA20FCA195DF21DFCB8781EDCD1AF80B34B4BFBD8039C9819F7EC0A4 | 用户报告几何失败，已撤回 |
| 恢复视频新建路径的回退包 | Windows 构建成功；239 项测试通过；APK CRC 通过 | 82190190C3E9A466BA9237E0E6CBF2360FCEE0A2C5EA84BBAF019B7969B9F3CE | 恢复基线；暂停问题未解决 |

回退时删除了仅为保留面板候选新增的一项应用层测试，因此测试数从 240 回到 239；
不是通过删除一个失败测试来宣称修复成功。候选当时所有 JVM 测试都通过，
但这些测试没有覆盖 SDK 原生几何和头显显示。

APK 继续使用 app/build/outputs/apk/debug/app-debug.apk；新构建会覆盖同名文件，
识别历史候选应使用上述哈希，不能仅凭文件名或分支 HEAD。
失败候选没有独立提交，其工作区当时基于 POC 提交 3a0a3fa，
该提交本身不包含此候选，不能将其当作失败实现的版本标识。

## 7. 后续方案的必要验收项

- 平面缩放时视频显示结果与边界同步；连续调节及松手后的最终状态均检查。
- 平面进入曲面可见；曲面缩放、调整曲率以及返回平面均检查。
- 暂停时调节大小／曲率保持同一视频帧；正常播放时保持正常速度。
- 文字覆盖层继续跟随几何，无新增闪烁或漂浮轮廓。
- 隐藏／恢复和资源清理不清掉当前输出，不泄漏旧面板或合成层。

必须同时通过几何显示与暂停帧保持，才可以再次标记问题解决。
不要只删除 clearVideoSurface 后认为问题已经消失：换到另一个 Surface
本身仍会进入 Media3 的首帧处理。也不要仅重组同一组视频 reshape 调用，
再将其作为新的方案；需要说明改变了哪个失败机制，并提供对应实机结果。

## 8. 核对来源

- [Media3 1.4.1 VideoFrameReleaseControl](https://github.com/androidx/media/blob/1.4.1/libraries/exoplayer/src/main/java/androidx/media3/exoplayer/video/VideoFrameReleaseControl.java)：输出 Surface 变化及首帧释放状态。
- [Media3 1.4.1 MediaCodecVideoRenderer](https://github.com/androidx/media/blob/1.4.1/libraries/exoplayer/src/main/java/androidx/media3/exoplayer/video/MediaCodecVideoRenderer.java)：切换视频输出的调用链。
- [Meta Spatial SDK 0.13.2 PanelSceneObject](https://developers.meta.com/horizon/reference/spatial-sdk/v0.13.2/com_meta_spatial_runtime_panelsceneobject)：reshape 与 resize 的不同职责。
- 本地核对资料：temp/experiment-panel-bytecode.txt、temp/geometry-retained-panel-api.md。
- 构建日志：temp/geometry-playback-continuity-build.log、temp/geometry-revert-failed-reshape-build.log。
- 用户在本聊天的实机反馈及“刚才没验收”的纠正；当前状态以此纠正为准。

相关实现概览见 [production-stage-rendering.md](production-stage-rendering.md)，
UI 所有权见 [ui-structure-and-terminology.md](ui-structure-and-terminology.md)，
原理验证历史见 [geometry-overlay-poc.md](geometry-overlay-poc.md)。
