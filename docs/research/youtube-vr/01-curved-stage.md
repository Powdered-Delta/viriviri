# 01 — YouTube VR 曲面视频幕布（Curved Video Stage）逆向笔记

样本：`libyoutubevrjni.so`（arm64-v8a，IDA 13337）。引擎：**Google lullaby**（`third_party/lullaby/...`）。
分析方法：ida-pro-mcp（decompile / py_eval / xrefs）+ 字符串扫描。所有地址为该 .so 内偏移。

## 1. 结论速览（对 ViriViri 的可执行建议）

- YouTube VR 的曲面视频屏是一张 **lullaby quad mesh**，曲率变化时**重建网格顶点**，
  弯曲几何**不是**在视频 quad 自己的着色器里纯 shader 算的，而是：曲率值 → 生成/更新一张弯曲 mesh（柱面投影），视频纹理贴上去。
- 另有一组 `uWarpFactorVertex/uWarpFactorFragment`（**单个 float warp 因子**）+ `uOffsetPosition/uOffsetVelocity` 材质 uniform，
  用于**控制器/隧道视野等程序化弯曲网格**（`HandMeshController`、`tunnel-vision-quad`），走 shader 顶点位移。
- 面板尺寸用 **dp** 传（`nativeUpdateContainerSizeDp(float wDp, float hDp, id)`），native 再换算成米；
  位姿/视图属性用 8 个 float（4×4 变换的紧凑形式）传（`nativeUpdateViewProperties`）。

**→ 对 Meta Spatial SDK（ViriViri）的落地建议：**
  Spatial SDK 的 `Mesh` 组件可挂**自定义网格**（或用 `mesh://plane/quad` 细分后在顶点阶段位移）。
  实现曲面幕布最贴 YouTube VR 思路的方式：
  1. 生成一张 N×M 细分的平面网格，对每个顶点做**柱面投影**（公式见 §3），曲率半径做成可调参数（放进 WorkbenchLayoutConfig 一类的集中配置）；
  2. 视频 SurfaceTexture 照常作为这张 mesh 的纹理；曲率变化=重建/更新顶点缓冲（YouTube VR 正是按值变化才 rebuild，见 §2）；
  3. 面板尺寸沿用 dp→米换算，位姿走 Transform，和现有 Spatial 面板一致。

## 2. 关键函数（已在 IDB 重命名+注释）

| 地址 | 导出名 / 命名 | 作用 |
| --- | --- | --- |
| 0x61f568 | `Java_..._VrViewFacade_nativeUpdateCurvature(.., uint a4, u8 a5)` | JNI 曲率入口。`a4`=曲率值，`a5`=是否启用弯曲；启用走 `sub_495118` 弯曲路径，否则走 vtable+56 平面路径。 |
| 0x495118 | `CurvedQuad_ApplyOrRebuild` | 在容器表（对象偏移+88）中找/建条目，置 dirty 标志(+128)，曲率经 `sub_419C94` 转成 mesh 参数；值变化才调 0x49522C 重建。 |
| 0x49522C | `CurvedQuad_RebuildMesh` | 弯曲网格重建：查 mesh 数据(`sub_2FD18C`)、复制顶点格式/submesh 区间、分配 96 字节可渲染对象(`sub_645C00`)。 |
| 0x516008 | `Shader_SetWarpAndOffsetUniforms` | 设置材质 uniform：`uOffsetPosition`(vec3,+20)、`uOffsetVelocity`(vec3,+32)、`uWarpFactorVertex/Fragment`(float,+48)。 |
| 0x61f844 | `nativeUpdateContainerSizeDp(float w,float h,id)` | 面板尺寸按 **dp** 传入，native 内部换算。 |
| 0x61f67c | `nativeUpdateViewProperties(8 floats,id)` | 位姿/视图属性（紧凑 4×4）→ `sub_531D34`。 |
| 0x61f6f8 / 0x4eb628 | `nativeUpdateTextureMatrix` / `StreamingTextureContainer_nativeUpdateTextureMatrix` | 视频纹理矩阵（SurfaceTexture texture transform），`StreamingTextureContainer`=视频流纹理容器。 |

其它相关 JNI：`nativeCreateContainer/DestroyContainer`、`nativeUpdateAnchor`、`nativeUpdateCollidability`（命中体）、
`nativeUpdateDismissedOnTouchOutside`（点外部关闭）、`nativeUpdateGradientHeightRatio`（渐变高度比）、`nativeUpdateName/Visibility`。

## 3. 柱面投影数学（取自 GVR 合成器 CYLINDRICAL_LAYER 顶点着色器，逻辑可直接复用）

字符串 @0x4c046 / 相关 GLSL 内嵌源码：
```glsl
// 输入 aViewportCoordsGreen.xy ∈ [-1,1] 的 quad 局部坐标
float cir   = uCylindricalRadius * uCylindricalInnerAngle; // 弧长
float mapping = uCylindricalInnerAngle / 2.0;               // 半角映射
float height  = cir / 2.0;
vec4 cylinderVertex = vec4(
    sin(aViewportCoordsGreen.x * mapping) * uCylindricalRadius,  // x: 弧面横向
    aViewportCoordsGreen.y * height,                            // y: 高度线性
    -(cos(aViewportCoordsGreen.x * mapping) * uCylindricalRadius - uCylindricalRadius), // z: 向内凹
    1.0);
```
即：**以 `uCylindricalRadius` 为半径、`uCylindricalInnerAngle` 为总张角，把平面 x 映射到圆弧，z 用 `1-cos` 让屏幕向观众凹**；
y 方向保持线性（柱面，不是球面）。曲率越大 = 半径越小 / 张角越大。

相关常量字符串：`#define SPHERE_RADIUS`、`#define OUTER_TO_INNER_CYLINDER_DISTANCE`、`QUAD_WIDTH`、
`_cylindrical_texture_distortion(_depth/_external_depth_normalized)`（后一组属 GVR 合成器重投影，非应用层）。

## 4. 合规
仅记录算法思路与参数维度，未复制专有代码；ViriViri 独立实现（GPL-3.0，不与 YouTube 专有代码混用）。
