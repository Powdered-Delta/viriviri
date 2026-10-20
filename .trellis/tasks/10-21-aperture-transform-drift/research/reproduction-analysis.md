# Installed recovery APK reproduction, 2026-10-21

Read-only device package hash matches the built DEV 802069c7 APK:
048976BB840F4BCD6BF7D2C380DE5D62A1BEB04413C192A710A814414E501D9F.
App update time 08:35:12; reproduction captured 09:01:21..09:01:39 (headset log clock).

Parsed 876 layer records: {'VIDEO': 292, 'BACKDROP': 292, 'DANMAKU': 292}. Curved records 864.
Maximum carrier formula error 1.4e-06 m; native center-boundary error 1e-06 m; arc-edge boundary error 2.325461e-06 m. Width versus 1.6*scale error 2.4e-07 m.

These logs rule out missing radius/scale submission in this reproduction. They measure actual mesh local bounds but only log submitted world Z; they do not measure final native SceneObject pose, GPU pixels or visible-window identity. Native bounds Z is reflected relative to API Z; do not add the two Z columns directly.

Example records:

```text
10-21 09:01:23.002 20248 20248 I ViriViriStage: batch VIDEO content=2.011744x1.131606 scale=1.2573401 radius=null baseZ=0.0 carrierZ=0.0 submittedWorldZ=1.9893367 apertureLocalBounds=((-1.005872, -0.565803, 0.0), (1.005872, 0.565803, 0.0))
10-21 09:01:21.427 20248 20248 I ViriViriStage: batch VIDEO content=2.011744x1.131606 scale=1.2573401 radius=9.897781 baseZ=0.0 carrierZ=-9.897781 submittedWorldZ=-7.8376083 apertureLocalBounds=((-1.0041414, -0.565803, -9.897781), (1.0041414, 0.565803, -9.846714))
```

Latest user wording: “最新启动的这个版本是对的，但是依旧存在z修正值没正确应用的问题”. Asked whether this confirms installed version only, or also correct visual direction; do not erase the prior behind-viewer observation until clarified. APK identity is independently confirmed by device sha256sum, so no need to ask the user to identify the build.

No new runtime patch or sign change made from these submission logs. Public SDK 0.13.2 SceneObject exposes pose setters and mesh-local bounds but no final object-world pose getter. Next evidence must establish the final rendered carrier/window relationship, not repeat another submitted-value test. Existing collision probe remains removed; no new collision/input path introduced.

User subsequently clarified: black-window curve direction is correct; center still moves with curvature. This supersedes the prior direction report for the latest build. The receive-only Inspector snapshot in settled-stage-inspector.json confirms the compensated ECS local poses persist; stage/anchor have no ECS Scale component. Final GPU pose remains unmeasured.
