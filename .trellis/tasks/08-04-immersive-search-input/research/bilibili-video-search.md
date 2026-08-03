# Bilibili Video Search Research

## Provider Boundary

The local PiliPlus reference uses the WBI-signed endpoint:

```text
GET /x/web-interface/wbi/search/type
```

For this slice, the provider requests only video results with:

```text
search_type=video
keyword=<submitted text>
page=1
page_size=20
platform=pc
web_location=1430654
```

The existing public nav response supplies WBI key material even when it reports
anonymous status with `code=-101`. Reuse the app's provider-local WBI signer;
the UI must not construct or sign this request.

## Mapping

Map `data.result[]` entries with a usable `bvid` into the existing
`Recommendation` model:

- `bvid` -> `videoId`
- `title` -> sanitized display title
- `author` -> `authorName`
- `pic` -> `coverUrl`
- `duration` -> `durationSeconds` when parseable
- `play` -> `viewCount` when parseable
- `pubdate` -> display label when available
- `bvid` -> canonical Bilibili video page URL

Entries lacking a `bvid` are skipped. A successful search with no usable entries
is an empty result, not a parser crash.

## Input Contract

The immersive panel is an embedded Android `ComponentActivity`; a focusable
Compose text field uses the normal Android input method. Horizon OS displays the
system virtual keyboard because the manifest already declares optional virtual
keyboard features. Do not create a Spatial entity, custom keyboard, player, or
Surface for text input.
