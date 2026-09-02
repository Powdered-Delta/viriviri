# Bilibili Playback Provider Research

## Scope

This note records the protocol boundary for the no-login ViriViri playback
prototype. It is based on inspection of the local reference project
`temp/PiliPlus`; no source, identifiers, request emulation, or credentials are
copied into the application.

## Required Request Sequence

1. Fetch `/x/web-interface/view?bvid=<bvid>` to resolve the selected video's
   primary `cid`.
2. Fetch `/x/web-interface/nav` and obtain `data.wbi_img.img_url` and
   `data.wbi_img.sub_url`.
3. Derive the 32-character WBI mixin key by reordering the two filenames with
   Bilibili's published permutation.
4. Sign the sorted playurl query with `wts` and `w_rid`, then fetch
   `/x/player/wbi/playurl` using the selected `bvid`, `cid`, and capability
   parameters.
5. Select compatible DASH AVC video and audio tracks and configure one Media3
   player to render them.

## Error Boundaries

- Network timeout and I/O failures are provider failures, never UI crashes.
- A non-zero Bilibili `code`, missing `cid`, malformed WBI metadata, missing
  DASH tracks, and unsupported codecs are explicit playback errors.
- No cookies, SESSDATA, access keys, CSRF values, device spoofing identifiers,
  or Bilibili playback-history heartbeat calls are used in this phase.
- The endpoint is not a stable public SDK contract. A provider failure must leave
  the recommendation list available for retry or another selection.

## Media3 Output Contract

- The player is process-scoped and is the sole owner of a selected media source.
- 2D and immersive hosts may attach different output Surfaces, but must not
  create independent players.
- A host captures `video id`, playback position, and `playWhenReady` before a
  route change; the target resumes only after its Surface is attached.
- The Spatial SDK owns its supplied Surface. Application code may detach it from
  the player but must not release it.

## References

- `temp/PiliPlus/lib/http/video.dart`: WBI-signed `videoUrl` request shape.
- `temp/PiliPlus/lib/pages/video/controller.dart`: DASH / fallback URL selection
  flow.
- `temp/PiliPlus/lib/utils/wbi_sign.dart`: WBI key derivation and signing
  algorithm.
