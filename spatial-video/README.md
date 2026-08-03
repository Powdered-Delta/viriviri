# ViriViri Spatial Video

ViriViri is the active Quest development branch, derived from Meta's Spatial SDK `SpatialVideoSample` at upstream commit `d3cc1b7`. It is used to develop and validate immersive video, Horizon OS 2D-window handoff, and return-to-immersive behavior on Quest hardware.

## Application identity

```text
Application name: ViriViri
Package name: com.m0e_n00b.viriviri
Meta Spatial SDK: 0.13.2
Version: 0.1.0
```

## Current behavior

- The default launcher starts the immersive video activity.
- A spatial `Video Mode` panel opens the separate Horizon OS 2D window.
- The 2D window does not yet take ownership of video playback.
- A 2D return records the panel group relative to the viewer. If the viewer's yaw changed by at least 15 degrees, the video, selector, and mode panels move together to the corresponding new pose over 160 ms.

## Local development notes

- This checkout uses `compileSdk = 36` because the local Android SDK installation lacks a usable Platform 34 package.
- Meta Spatial Editor CLI is not installed locally. The optional `Composition.glxf` environment is therefore allowed to fail to load without preventing video playback.
- Repeated 2D and immersive transitions can intermittently expose Horizon OS/OpenXR compositor behavior such as long black screens. Treat those as runtime investigations rather than application-level video handoff failures.

详细的设备验证记录、已知运行时问题与重定位验收步骤见 [Quest 运行时记录](docs/quest-runtime-notes.md)。
