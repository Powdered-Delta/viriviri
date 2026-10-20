# Spatial 坐标、圆柱原点与视觉定位

本文件以项目锁定的 **Spatial SDK 0.13.2** 为准，核验日期 2026-10-20。
结论分别来自实际 SDK 数学类运行结果、JNI/原生实现和项目调用链；不以“看起来向前”推断坐标符号。
历史版本把 API 空间和 OpenXR 空间混为一个右手系，并把 `-Z forward` 解释为模型法线；该解释已撤回。

## 1. 项目使用的 Kotlin/API 世界空间

直接运行 SDK 中的数学类得到：

```text
Pose().forward()              = (0, 0, +1)
Pose().right()                = (+1, 0, 0)
Vector3.Forward               = (0, 0, +1)
Vector3.Up                    = (0, +1, 0)
Quaternion(0, 90, 0) * forward = (+1, 0, 0)
```

因此，初始朝向、面向项目默认幕布时，**API +X 向右、+Y 向上、+Z 向前**。
对应站立手势：右臂向右平举是 +X，头顶是 +Y，左手前指是 +Z。
这是 API 的方向约定；不能把它直接称为 OpenXR 的右手坐标约定。
世界轴固定，不会随用户转头改变；重新定位参考空间时应重新确认用户与世界的关系。

项目使用 LOCAL_FLOOR，以地面为高度参考；view origin 配置为 `(0,0,0,0)`。
初始工作台锚点的 API 世界 Z 为 +2 米。用户抓取移动后，不能继续把“幕布正前方”当成固定世界 +Z。
布局的当前数值与实体层级见 [UI 结构](ui-structure-and-terminology.md)，不要在本文件复制易过时的 rail 坐标。

## 2. 幕布/舞台局部空间

舞台局部点通过父级姿态变换到 API 世界空间：

```text
p_world = T_workbench * T_stage * p_stage
```

初始舞台没有额外旋转，所以轴向与 API 世界空间相同，仅原点不同。
此时 local -Z 从位于 +Z 侧的幕布指向观看者，local +Z 远离观看者。
这并不意味着世界 +Z 与局部 +Z 符号相反。
抓取旋转舞台后，必须乘父级姿态；不能直接在世界 Z 上加减局部补偿。

`worldPoseOf()` 按 TransformParent 链合成姿态；当前舞台尺寸烘入 shape，层的原生 scale 保持单位值。
若今后引入非单位父级 scale，必须扩展该契约，不能假装 Pose 已包含 scale。

## 3. API 到原生渲染/OpenXR 的转换

SDK 0.13.2 的原生实现存在明确的 Z 反射：

```text
F = diag(1, 1, -1)
p_native = F * p_api
R_native = F * R_api * F
```

SceneObjectSetPosition 在提交原生 transform 时翻转位置 Z；网格上传也翻转顶点 Z 和法线 Z。
二者必须一起转换，不能只给 compositor 或只给开孔额外翻一次。
原生/OpenXR 的 -Z forward 与 API 的 +Z forward 在这条边界上对应。
`setPosition`、`setRotationQuat` 等 Kotlin 接口接受 API 空间值，调用者不要手工再翻符号。

官方 architecture 页写的是 +X right、+Y up、-Z forward；该表述不能直接覆盖锁定版本的 Kotlin
`Pose.forward()` 实测结果，更不能借“screen 指模型法线”强行消除差异。

## 4. 原生圆柱与现成的视觉位置补偿

SDK 的圆柱轴在 carrier 原点。原生 makeCylinderSurface 的参数展开，在 API 网格空间可写成：

```text
theta = arcX / radius
p_api = (radius * sin(theta), y, radius * cos(theta))
p_native = (radius * sin(theta), y, -radius * cos(theta))
```

中央点 theta=0 的 **API 局部 Z=+radius**；原生局部 Z=-radius。
边缘相对中央点的 API Z 差为 `radius * (cos(theta) - 1)`，即向 local -Z 卷曲。
不能用平面中心原点的自建网格去替代轴原点圆柱而不补偿这个差别。

项目现有方法是 `PlaybackStageCurvature.entityLocalZForSurface(baseLocalZ)`：

```text
Flat:     carrierLocalZ = baseLocalZ
Cylinder: carrierLocalZ = baseLocalZ - radius
中央点:   carrierLocalZ + radius = baseLocalZ
```

保持的是 **曲面中央点**，不是每一个边缘点；改变曲率时边缘会按圆弧移动。
`SURFACE_ANCHOR_OFFSET_SIGN=-1` 是上述原点关系的结果，不是调试旋钮。
半径变化时从 authored base 绝对重算；不要累加，也不要根据画面症状试着改成 +1。

## 5. 开孔是否套用了幕布的方法

当前 SDK 开孔 mesh 和 compositor video layer 绑定同一个 `PanelSceneObject` / `videoSurfaceEntity`。
现在由 `PlaybackStagePanelRenderer` 在同一次整组提交中应用现成的半径补偿：
`PlaybackStageLayerGeometry.localPose()` 计算 base-radius，`worldPose(stageWorld)` 再合成父级姿态。
每个子层写入新建的绝对局部姿态，局部旋转归零；全部层共用一次采样的舞台世界姿态。
重构前源码同样有调用补偿，因此不能把现象归结为“开孔漏调方法”；此前分散的创建、重试和调试写入
已收敛为 `PlaybackStageController` 的最新布局和一个提交入口，视觉效果仍须实机验证。

`videoPanelConfig()` 给视频和开孔同一最终 shape；renderer 应用 `Crop.NONE`，完整解码帧映射
到该 shape，footprint 为单位值。不再按固定 1920×1080 配置推算 FIT 黑边并裁切：直接解码 Surface
没有这项契约。4:3 / 16:9 = 0.75 的人为横向裁切会让画面内容放大 1/0.75；该错误路径已删除。
弹幕/压暗层按方案 A 使用完整舞台范围，视频/开孔使用有效画面范围；它们共享 scale 和 curvature。

## 6. 证据与验证边界

- `SpatialCoordinateContractTest` 执行实际 SDK 的 Pose/Quaternion，并检验带舞台旋转的半径补偿。
- 详细原生地址、AAR/SO 哈希和独立探针保存在 Trellis 本次任务的 research 记录。
- 运行日志中的 apertureLocalBounds 是 SDK mesh 数据；submittedWorldZ 是提交值，不等于头显像素测量。
- 2026-10-20 只读发现在线 Quest 2，未取到相关保留日志；本轮未安装、启动应用或修改设备。
- JVM/构建通过不能替代头显上的尺寸、Z 定位与遮挡验收。

官方资料：
[Architecture](https://developers.meta.com/horizon/documentation/spatial-sdk/spatial-sdk-architecture)、
[Editor transforms](https://developers.meta.com/horizon/documentation/spatial-sdk/spatial-editor-transforms-coordinates)、
[SceneLayer](https://developers.meta.com/horizon/reference/spatial-sdk/v0.13.2/com_meta_spatial_runtime_scenelayer)。
