# 添加 Windows Quest ADB 安装脚本

## Goal

提供一个可审计的 Windows PowerShell 脚本，用于将用户明确指定的 ViriViri
debug APK 覆盖安装到已连接的 Meta Quest，减少 WSL/Windows 路径和版本混淆。

## Scope

- 新增 `scripts/install-quest.ps1`。
- 支持显式 `-Apk` 路径；不指定时使用仓库默认
  `app/build/outputs/apk/debug/app-debug.apk`，并在输出中明确提示。
- 自动定位 `adb.exe`：`-AdbPath`、`$env:ANDROID_HOME`、`$env:ANDROID_SDK_ROOT`
  和常见 Windows SDK 路径。
- 检查 ADB 可用、设备列表、设备状态和多设备歧义；支持可选
  `-DeviceSerial`。
- 安装前打印 Windows APK 路径、文件大小和 SHA-256；执行 `adb install -r`。
- 安装后确认 `com.m0e_n00b.viriviri` 的 `pm path`，并打印包版本/路径。
- 默认不构建、不启动、不清理数据、不自动选择任意 APK；`-Launch` 作为显式
  可选动作。
- 脚本错误使用非零退出码，外部命令参数安全传递，不依赖 WSL `wslpath`。
- 更新 README Windows/Quest 使用说明，给出预期输出与手动验证提醒。

## Non-Goals

- 不修改 Android 代码、Gradle、APK 内容或设备设置。
- 不自动部署到 Quest，不抓取 logcat，不执行 force-stop/clear/uninstall。
- 不实现跨平台 Bash/PowerShell 双脚本。

## Acceptance Criteria

- PowerShell 语法检查通过，脚本在无设备、无 adb、多设备和成功安装路径有清晰
  错误/输出。
- `-WhatIf`/dry-run 能检查路径和设备但不执行 install；实现方式需保持兼容
  Windows PowerShell 5.1 或明确记录 PowerShell 7 要求。
- README 包含明确命令示例，强调安装成功不等于 Quest 视觉验收。
