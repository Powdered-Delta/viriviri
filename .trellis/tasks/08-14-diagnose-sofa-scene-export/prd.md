# 诊断沙发空间场景导出与打包

## Goal

确认 Meta Spatial SDK SpatialVideoSample 的 MediaRoom（沙发）环境为何未进入当前 APK，并将修复方案限定在可验证的场景导出/资产打包边界。

## Facts

- `app/scenes/Composition/Main.scene` 引用 `MediaRoom/Main.metaspatialobj`。
- `SpatialVideoSampleActivity` 加载 `apk:///scenes/Composition.glxf` 并按 `MediaRoom` 节点控制环境可见性。
- Gradle `spatial.exportItems` 配置从 `app/scenes/Main.metaspatial` 导出到 `app/src/main/assets/scenes`。
- 旧构建命令长期使用 `-x :app:export`，且旧 `app-debug.apk` 未包含 `scenes/Composition.glxf` 或 MediaRoom 资产。
- Windows Meta Spatial Editor CLI `v16.1.0.18.145` is available at `D:\Program Files\Meta Spatial Editor\v16\Resources\CLI.exe`.

## Scope

- 验证 Windows 环境下 `:app:export` 是否可运行，以及所需 Meta Spatial Editor CLI 路径。
- 检查导出产物和 APK 资产列表。
- 若 CLI 已可用，执行导出并重建，确认场景进入 APK。
- 若 CLI 未安装，记录可执行安装/配置步骤与最小构建命令。

## Non-goals

- 不重写场景、环境、播放器、Surface 或 runtime 场景加载代码。
- 不把 MediaRoom 固定实体硬编码为 Kotlin `Entity.create()`。
- 不安装或启动 Quest APK，除非用户明确请求。

## Acceptance Criteria

- [x] 根因明确：旧构建命令通过 `-x :app:export` 跳过唯一的 GLXF export task，且 Gradle 未配置已安装的 Editor CLI。
- [x] `:app:export` 使用 configured Windows CLI 成功，生成 `Composition.glxf`、`MediaRoom.gltf` 和三张纹理。
- [x] `:app:preBuild` depends on `:app:export`; normal Windows build command no longer skips export.
- [x] Windows `:app:testDebugUnitTest :app:assembleDebug` passed in 37s with export in the task graph.
- [x] PowerShell ZIP inspection confirms `assets/scenes/Composition.glxf`, `assets/scenes/MediaRoom.gltf`, and the three texture PNG files are packaged in `app-debug.apk`.
