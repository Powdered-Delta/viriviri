# 05 — 包体构成与 ViriViri 瘦身清单

样本：`YouTube_1.79.11.apk`。解压 190.5MB，APK 内压缩后 98.7MB。**单 arm64-v8a ABI、release、已裁剪**。

## 1. 压缩后大小（决定 APK 体积）
### native .so（top，压缩后）
| .so | 压缩MB | 角色 |
| --- | --- | --- |
| libyoutube_vr_impress_jni | 6.29 | imp 沉浸式 UI 框架（核心） |
| libcrashlytics / -common | 5.08+5.06=10.1 | 崩溃上报（**第三方，可换/可裁**） |
| libgoogle3 | 5.05 | Google 基础库 |
| libmozc | 4.99 | Google 日文输入法（**中文场景可剔除**） |
| libcronet | 3.15 | 网络栈（可换系统/OkHttp） |
| libyoutubevrjni | 3.08 | VR 业务主库（核心：曲面/渲染/面板） |
| libimpress_api_jni | 2.29 | imp API（核心） |
| libopenxr_loader | 2.11 | OpenXR 加载器（必需） |
| libhmm | 1.78 | hand mesh 手势 |
| libgvr_keyboard | 1.11 | VR 射线键盘 UI |
| libgvr / gvr_audio | 0.93+~ | GVR 渲染/音频（旧路径） |
| liboculuscowatchjni | 0.86 | 一起看（可裁） |
| libconscrypt_jni | 0.50 | 加密（平台已自带可考虑） |

### dex：classes2/3/4 = 3.65+3.33+1.71 ≈ 8.8MB（Java/Kotlin 业务，R8 后）

### 观察
- **核心 VR/UI .so 压缩后其实只 ~14MB**（impress 6.3 + youtubevrjni 3.1 + impress_api 2.3 + openxr 2.1）。
- 大头是可替换的第三方：crashlytics(10MB)、mozc(5MB)、cronet(3.2MB)、google3(5MB)。
- 只打 arm64、不打 x86/armeabi-v7a 省掉 ~2/3 native 体积；.so 压缩率约 0.4–0.6。

## 2. ViriViri 瘦身清单（对照我们 debug 350MB）
我们 350MB 是 **debug + 多 ABI + 未裁剪**，与 99MB 不可直接比。release 应做：
- [ ] **只保留 arm64-v8a**（`abiFilters 'arm64-v8a'`）——最大一刀，去掉 x86/x86_64/armeabi-v7a。
- [ ] **release buildType**：`isMinifyEnabled=true`（R8）、`isShrinkResources=true`。
- [ ] **so 压缩/对齐**：android:extractNativeLibs 与压缩策略；确认 .so 在 APK 内压缩（或用 page-align 未压缩权衡安装体积）。
- [ ] **剔除调试/诊断 so** 与未用 SDK：检查 Meta Spatial 示例带入的可选库、测试 mesh/fplmesh 资源。
- [ ] **资源裁剪**：只留需要的 density/语言；去掉参考图、未用材质/贴图。
- [ ] **按需下发/动态特性**：超大资源（如有）走 Play Asset Delivery / 运行时下载。
- [ ] **崩溃上报选型**：crashlytics 类若引入注意其 native 体积；可按需启用。
- [ ] 不要打包日文输入法（我们自研拼音）；不引入 mozc。
- [ ] 出一个 release APK 后用本脚本的压缩体积分组对比，定位真实大头。

### 体积分析命令（随时复现）
```powershell
Add-Type -AssemblyName System.IO.Compression.FileSystem
$z=[System.IO.Compression.ZipFile]::OpenRead('app\build\outputs\apk\release\app-release.apk')
$z.Entries | Sort-Object CompressedLength -Descending | Select-Object -First 25 |
  ForEach-Object { '{0,7:N2}MB  {1}' -f ($_.CompressedLength/1MB), $_.FullName }
$z.Dispose()
```

## 3. 合规：仅体积/架构数据，独立实现。
