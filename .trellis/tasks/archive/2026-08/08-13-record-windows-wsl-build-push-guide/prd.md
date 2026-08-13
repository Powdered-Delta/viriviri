# 记录 Windows/WSL 构建推送指南

## Goal

把当前项目在 WSL + Windows 主机环境下的构建、Quest 推送和诊断流程记录到 `temp/`，避免后续把 WSL 的 Java 环境缺失误判为 Gradle 项目故障。

## Requirements

- 记录 WSL 与 Windows 的职责边界。
- 记录 Windows JDK 17、`gradlew.bat`、Android SDK 和 Gradle wrapper 命令。
- 记录当前真实构建失败：Google Maven 下载 `activity-ktx:1.7.0` 超时。
- 记录构建产物检查、SHA-256、`-WhatIf` 预检和显式安装/启动流程。
- 不修改项目构建配置，不自动安装 APK，不提交 `temp/`。

## Acceptance Criteria

- [x] 指南写入 `temp/`。
- [x] 包含可直接复制的 Windows 命令。
- [x] 明确构建、安装、启动和 Quest 人工验证是不同阶段。
- [x] 明确网络依赖下载失败时的诊断边界。
