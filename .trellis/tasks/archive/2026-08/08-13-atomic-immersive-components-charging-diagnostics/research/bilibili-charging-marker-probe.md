# Bilibili Charging Marker Probe

## Target

- BV: `BV1n6uM6nEvJ`
- URL: `https://www.bilibili.com/video/BV1n6uM6nEvJ`
- Request chain: anonymous `view -> nav/WBI -> player/wbi/playurl`

## Bounded result

| Step | HTTP | API code | Relevant result |
| --- | --- | --- | --- |
| `view` | 200 | 0 | `cid` present; `rights.elec=0`, `rights.ugc_pay=0`, `rights.arc_pay=0`, `is_chargeable_season=false` |
| `nav` | 200 | -101 | Anonymous response still provided WBI signing material |
| `playurl` | 200 | 0 | `data` empty; no DASH object and no durl collection |

No signed media URL, cookie, credential, CSRF value, account identifier, or raw WBI material was recorded.

## Decision

`is_chargeable_season` is the only verified public list/detail field used to
render ViriViri's `CHARGING_EXCLUSIVE` access label. `rights.elec` means a
creator can receive charging and is not enough to assert exclusive access.

An empty anonymous `playurl` response cannot generate a list badge. It may
represent an access limitation, an endpoint contract variation, or another
availability condition. The UI must retain its ordinary item presentation and
let the existing bounded playback-resolution error path report failure after a
user selection.
