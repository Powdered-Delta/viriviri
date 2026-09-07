# 02 — 渲染器初始化 / 刷新率 / 面板俯仰角 逆向笔记

样本：`libyoutubevrjni.so`（arm64，IDA 13337）。`VrRenderer` / `VrApiRunner` JNI。

## 1. 关键发现（对 ViriViri 可执行）

### 面板俯仰角（browse UI pitch）
- `VrApiRunner_nativeGetBrowseUiPitchAngleDegrees()` → 内部 `sub_48AFF4`：
  角度是**运行时从 lull 组件/实体读取的弧度值，再 ×57.296 转度**；读不到返回 NaN。
  **不是硬编码常量**——说明 YouTube VR 把浏览面板的俯仰角做成了可配置/随环境布局的数据。
- → 我们的做法正确：面板 pitch 放在 `WorkbenchLayoutConfig` 集中可调；YouTube 用「度」、内部转弧度，我们配置层用什么单位要统一（Spatial 用弧度/四元数）。

### 刷新率
- `nativeGetSupportedRefreshRates()` 返回 **int[]**，从 VR 设备 API 的向量取（vtable+16，按 4 字节/元素计数）。
- → 支持多档（如 72/90/120Hz），由设备枚举；ViriViri 若要锁高刷应先 `xrEnumerateDisplayRefreshRatesFB` 再选，不要写死。

### 渲染器生命周期
- `VrRenderer_nativeCreate(...)`：分配 88 字节渲染器对象（`sub_645C00(88)`），`sub_52AC64(msaaMode=clamp(a6<4), context, a4)`；GL 上下文标志 65542。
- `nativeGlInit` → `sub_52D5E0`；`nativeOnDrawFrame/nativeOnPause/nativeOnResume/nativeOnTrigger/nativeOnTap` 为 GL 线程回调（符号在 PLT 跳转表，真正实现在 .so 内注册）。

### 手势 / 设备
- `nativeIsHandTrackingEnabled()`：设备支持且 `deviceModel ∈ [5,6]`（`sub_4C64BC()-5 < 2`，对应特定 Quest 机型）。
- `nativeGetDeviceModel()`：vtable+104 返回机型字符串。

## 2. 对 Meta Spatial SDK 的映射
| YouTube VR (GVR/lull) | Meta Spatial SDK (ViriViri) |
| --- | --- |
| VrRenderer native + GL 线程 draw frame | SDK AppSystemActivity / VrActivity 渲染循环（无需自管） |
| 设备枚举刷新率 | OpenXR `XR_FB_display_refresh_rate` |
| 浏览面板俯仰角（可配置度值） | WorkbenchLayoutConfig 面板 pitch（集中调参） |
| MSAA 模式 a6 | Panel/相机抗锯齿配置 |
| Hand tracking 机型判断 | OpenXR hand tracking extension 能力查询 |

## 3. 合规：仅记录思路，独立实现。
