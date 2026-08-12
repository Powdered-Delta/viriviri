# Media Overlay Design Research

## PiliPlus danmaku merge reference

Reference worktree: `temp/PiliPlus-latest`, commit `e5dfc6394`.

`lib/pages/danmaku/controller.dart` shows the current loading and transform
boundary:

- Danmaku is requested by six-minute segments through `DmGrpc.dmSegMobile`.
- Requested segments are tracked and retried after failed requests.
- Events are stored in approximately 100ms progress buckets.
- When `mergeDanmaku` is enabled, equal `content` in the loaded segment is
  merged and a count is incremented on the retained event.
- Filtering runs after self-message detection and merge handling.
- File sources can load a local encoded segment payload independently from the
  network source.

ViriViri should expose this as an ordered, optional `DanmakuTransformPlugin`, not
hard-code it into the source or renderer. The initial plugin can match this
behavior while leaving normalization/window/distinction rules configurable later.

## Bilibili source options

`lib/grpc/url.dart` and `lib/grpc/dm.dart` identify the segmented mobile endpoint:

```text
/bilibili.community.service.dm.v1.DM/DmSegMobile
```

The HTTP XML endpoint is useful as an initial public adapter but may be compressed
and subject to endpoint changes. XML/deflate parsing and segmented protobuf
parsing must remain separate adapters that both emit the same `DanmakuEvent`
contract.

## Caption and translation boundary

PiliPlus has subtitle-related video API handling, but it does not define the
ViriViri spatial caption contract. Caption cues therefore remain independent from
danmakus, share only the MediaStage overlay substrate, and use one selected
stage/gaze target. A translation adapter must be optional, bounded, cancellable,
and original-text preserving.

## Local decisions

- Surface groups share normalized projection coordinates and lane occupancy, not
  physical meter coordinates.
- Group-level load balancing and group-internal depth-layer allocation are
  separate decisions.
- Viewer projection occlusion belongs to an `OcclusionDomain`, which may contain
  multiple groups if theme/viewer geometry overlaps.
- Text direction/writing mode and emission direction are independent fields.
- Surface basic style participates in glyph bounds prediction before scheduling.
