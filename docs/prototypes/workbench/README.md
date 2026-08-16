# ViriViri Workbench Web Prototype Handoff

## Purpose

This document defines the interactive browser prototype at
[`index.html`](./index.html). It is the version-controlled UX
validation surface for the immersive Workbench. It validates information
hierarchy, canvas transitions, hit-target ownership, and visual composition
before Quest/Spatial implementation.

It is not a runtime implementation, a Quest compositor validation artifact, or
a replacement for in-headset input, depth, grabbing, and performance testing.
Its external icon and photo URLs are prototype-only dependencies; they are not
Android or Quest runtime assets.

## Reference Sources

Use the following sources in this order when a later implementation conflicts
with the prototype:

1. [`AGENTS.md`](../../../AGENTS.md): project-wide Quest, ECS, scene authoring,
   and one-surface constraints.
2. [`08-14-08-14-rebuild-immersive-workbench/prd.md`](../../../.trellis/tasks/08-14-08-14-rebuild-immersive-workbench/prd.md): active product model,
   Workbench module ownership, Quiet Watch behavior, and deferred input-method
   work.
3. [`Youtube-VR-UX.md`](../../Youtube-VR-UX.md): navigation composition, canvas
   behavior, MediaStage/transport relationship, and dismissal model.
4. [`index.html`](./index.html): current approved browser-level composition and
   interaction behavior.

The prototype is intentionally more concrete than the UX document about the
current left-detail rail. It may be changed only through explicit product UX
feedback; do not infer new content or controls from consumer video apps.

## Non-Negotiable Runtime Boundaries

- `PlayerSession` owns the one process-wide `ExoPlayer` and the one active SDK
  video output `Surface`.
- Workbench visibility, navigation, comments, lists, or Focus must never create
  another player, video entity, panel video output, or `Surface`.
- `MediaStage` is the only video host. It owns video, subtitle, danmaku, and
  short-lived playback feedback.
- Workbench is one movable composition group. Only its grab handle may move the
  group; individual rails must not become independent movable panels.
- A dim/scrim affects `MediaStage` only. It must not dim the scene,
  passthrough, hands, controls, rails, or global navigation.
- Do not use a transparent full-screen Compose panel for dimming. It produces
  dither/screen-door artifacts on Quest and can interfere with stage input.

## Current Prototype Composition

### Top Stack

The prototype treats these as separate modules, in visual order:

1. `SystemBar`: time, 2D handoff, environment, danmaku, battery.
2. `GlobalNavigation`: `ViriViri` Home, search, profile, settings.
3. `ContentNavigationSlot`: one route-specific module, never a pile of
   simultaneous navigation controls.

`ViriViri` is Home. Do not add a duplicate current-video context icon to global
navigation. Transport has no title; the current video title belongs in context
or detail content.

`ContentNavigationSlot` owns exactly one route-specific module. On Home it is a
centered, content-width tab strip with `直播` (visible but unavailable), `推荐`,
`热门`, and `追番`. Its width adapts to the four tabs instead of reserving an
empty percentage of the top stack. Search uses its own constrained field width;
video-list actions use their content width and render as the selected tab.
Subordinate routes use the same tab surface for a single Back tab; list-workspace
headers never repeat that return control.

Home restores the last selected Home category and list layout without changing
the active canvas or its left/right rail composition. The centered list workspace
becomes visible for Home and every category selection, so the navigation never
appears without corresponding content.

Search uses the slot's constrained field mode: search action, clear action within
the query field, system-IME request, and voice request. The browser prototype
shows an application-owned 26-key Chinese/English console in front of Transport
when the field is selected. It validates console placement and hit ownership
only; it does not model Pinyin conversion, text entry, system-IME focus, voice
capture, requests, or player changes.

### MediaStage and Workbench Canvases

The prototype models these canvas states:

| Canvas | Intent | Visible composition |
| --- | --- | --- |
| `watch` | Quiet Watch | MediaStage only; Workbench modules are hidden. |
| `controls` | Normal playback controls | Top stack, left detail rail, MediaStage, right source rail, front transport. |
| `browse` | Discovery/Search | Browse rail, route-specific content navigation, and visible center list workspace. |
| `context` | Current video context | Context-oriented navigation/content state. |
| `workspace` | Explicit video list | Transparent center list workspace over the existing MediaStage. |
| `focus` | Creator-focused listing | Center list workspace with MediaStage as PiP. |

The center list workspace is transparent. Its cards and controls can have local
surfaces for legibility, but the workspace itself is not a global opaque
backdrop.

### Normal Playback Layout

Normal playback uses a three-column operation layer:

- Left: current-video details and comments.
- Center: the existing MediaStage. The list workspace appears here only after an
  explicit open action.
- Right: source-aware content: parts, collection/playlist, related videos, or
  danmaku.
- Bottom/front: transport controls without a title.

The transport remains visually in front of the stage rather than becoming a
separate lower spatial panel.

### Transport Controls

The transport exposes subtitle, quality, and playback-speed controls directly
in its command row. Each control shows its current value or selected state and
opens one compact option menu, following PiliPlus's player-bottom-control
model:

- **Subtitle**: Off, Chinese automatic subtitles, or English.
- **Quality**: Auto, 1080P, 720P, or 480P prototype availability.
- **Speed**: 0.5x, 0.75x, 1.0x, 1.25x, 1.5x, 1.75x, 2.0x, or 3.0x.

The playback settings popover and all three option menus are children of
`TransportOverlay`. They open above transport and must not align to or become
part of the right source rail. Subtitle, quality, and speed exist only as direct
transport menus; Settings does not duplicate them and contains only autoplay,
display ratio, and danmaku. Only one settings/option surface is visible at a
time. At compact widths, secondary skip/seek buttons hide before subtitle,
quality, speed, or settings access is removed.

These browser controls preserve menu and selection semantics only. Runtime
quality selection still needs source-aware availability and a player update;
subtitle selection needs real track data; playback speed needs to update the
existing player and synchronize danmaku timing.

## Left Detail Rail: Current Contract

The left rail is deliberately **not a tab layout** and has no title, return, or
close header. There must be no blank header spacer.

Its normal state is, from top to bottom:

```text
body
  detail section
    title
    data
    like | coin | favorite
  author
  description
footer
  comment entry
```

The title, data, and video actions share one `detail-section`. The rail does not
render `detail-label` captions; hierarchy comes from typography, spacing, and
the controls' own accessible names.

### Detail Body

- **Title and data**: current video title followed by play count and duration in
  one section.
- **Video actions**: Like, Coin, and Favorite sit directly below the data. Each
  control shows only its familiar icon and public count; accessible names and
  tooltips preserve the action meaning. Like and Favorite toggle their selected
  state and displayed count. Coin is cumulative, cannot be removed, and caps
  the local prototype action at two.
- **Author**: an explicit creator action. Selecting it opens the creator Focus
  workspace.
- **Description**: current video description.

The prototype keeps video-action state per sample video across renders. It does
not model authentication, requests, failure states, favorite-folder selection,
or the PiliPlus long-press triple gesture.

Do not add a pseudo-tab strip, a left-rail header, an X/Back control, a share
button, or additional speculative current-video actions to this rail without
approval. The current approved body order is the list above.

### Comments Collapse

The footer contains the sole comment-entry action. Selecting it opens a
full-height collapse over the **entire left rail**:

- The collapse is bottom-anchored and animates `height: 0 -> 100%`, so it grows
  from bottom to top.
- It covers the detail body and footer while open.
- It uses the pure opaque color `#1a1b17`; it must not reveal the underlying
  detail rail through alpha.
- It has a fixed header with `评论` on the left and a collapse icon on the
  right. The entire header is the collapse hit target.
- Each comment has `Reply` on the left. Dislike and like icon controls are on
  the right.
- Per-comment like/dislike is mutually exclusive.
- Selecting Reply shows a reply input for that comment. Closing comments clears
  the active reply target.

The comment drawer is an information mode, not a separate video stage or
separate spatial product panel.

## Input and Dismissal Rules

- In Quiet Watch, clicking `MediaStage` opens Workbench controls. Do **not**
  make all scene/passthrough clicks open Workbench; this is too easy to trigger
  accidentally in MR/VR.
- Every control uses a `data-action` and is handled through the single delegated
  `dispatch(action, value)` path.
- Rails, a visible center list workspace, transport, settings, profile, and
  text inputs are preserved interaction regions. Clicking their non-button
  content must not dismiss the Workbench. A hidden list workspace has no hit
  target, so center clicks continue to reach MediaStage.
- A non-action click in the actual canvas/scene region may dismiss a temporary
  visible canvas back to Quiet Watch.
- The stage and danmaku display need their own Quest input validation. The
  browser prototype cannot validate Spatial collision, raycast, or panel
  ordering behavior.

## Current Visual Rules

- UI surfaces are restrained and operational, with 7px-or-less card radius.
- Use Lucide icon font icons for familiar icon-only controls. Keep tooltips via
  `title` on icon actions.
- Video cards use a 16:9 cover, optional charging marker at top-left, and
  duration marker at bottom-right. The single-column list layout keeps that
  cover on the left and places title, author, and metrics in a right-side text
  column.
- The current icon and photo assets load from external URLs. Treat them as
  prototype-only dependencies.
- Danmaku appears only on MediaStage and is never an input target.
- Left/right rails are visually above MediaStage. Explicit rail actions are
  kept above the stage hit layer with `pointer-events: auto`.

## Commenting Requirement for Further Prototype Changes

Every UX behavior change in the prototype must have a short, adjacent comment.
The comment must state the design constraint or interaction reason, not narrate
syntax.

Use one of these forms:

```css
/* UX: comments cover the complete left rail, rising from its bottom edge with an opaque surface. */
.comments-drawer { ... }
```

```js
// UX: one comment cannot be both liked and disliked at the same time.
function toggleCommentReaction(...) { ... }
```

Required locations for annotations:

- Beside new or changed CSS rules that alter spatial/layout behavior, opacity,
  stacking, hit targets, or responsive sizing.
- Beside state fields that preserve a user-visible transient state across a
  render.
- Beside reducer/dispatch branches that encode a UX rule.
- Beside compositional markup that establishes a non-obvious panel ownership or
  information hierarchy.

Avoid boilerplate such as `// set value`. If a change is self-evident and does
not alter UX policy, do not add noise. The goal is to preserve the decision that
would otherwise be lost between iterations.

## Current Annotations in the Prototype

The following comments describe the current feature constraints and should be
replaced or removed when their associated behavior changes:

- Left detail has no header; its body starts at the panel top edge.
- Title, metrics, and video reactions form one reading section before creator
  and description content.
- Equal-width video reaction controls show only familiar icons and counts below
  the metrics.
- Per-video Like, Coin, and Favorite state persists across renders.
- Playback settings and option menus belong to transport and open above it;
  Settings does not duplicate subtitle, quality, or speed.
- Home navigation is a centered adaptive four-tab strip; Live remains visibly
  unavailable, Playback marks video list as its selected tab, and subroutes use
  the same surface for the only Back tab.
- Home and category navigation preserve their last tab, list layout, active
  canvas, and side-panel composition while keeping the center list visible in
  three desktop columns.
- Search header controls stay in one stable row, while its 26-key preview is the
  topmost application-owned browser input layer in front of Transport.
- Compact transport preserves subtitle, quality, speed, and settings before
  secondary skip controls.
- Transport selection state and its one open menu persist across rerenders.
- Open comments are a full-height opaque reading mode whose complete fixed
  header is the collapse hit target.
- Comment action rows reserve the left for Reply and the right for reactions.
- Drawer/reaction/reply state persists while Workbench is open.
- One comment cannot be both liked and disliked.
- Closing comments clears the inline reply composer.
- Visible panel-body clicks are preserved interaction, not canvas-dismiss
  clicks; a hidden list workspace never blocks MediaStage dismissal.

## Design Rationale

| Decision | Reason |
| --- | --- |
| One MediaStage and one output Surface | Prevents ExoPlayer/Surface handoff regressions and preserves the player ownership contract. |
| Separate system/global/content navigation | Each layer has one responsibility; navigation remains composable across playback, browse, search, and Focus. |
| Stage-only Workbench invocation from Quiet Watch | MediaStage is the stable, discoverable interaction target. Global scene clicks would create MR/VR accidental activation. |
| No left-rail tabs | Video details, creator, and comments form a reading hierarchy, not peer routes. |
| Full opaque comment collapse | Comments are a focused reading/reply mode. An opaque overlay prevents detail content from competing visually with comments. |
| Reply left; reactions right | Separates conversation continuation from compact moderation/reaction actions and makes repeated rows easy to scan. |
| Preserve rail-body input | A panel body is not canvas whitespace. Treating it as dismiss input made detail/comment controls unreliable. |
| Transparent center workspace | Keeps media context visible while the user chooses content; only local cards need surfaces. |
| MediaStage-only scrim | Keeps the physical scene, passthrough, hands, and Workbench readable and avoids broad compositing artifacts. |

## Known Gaps and Pending Decisions

- The prototype does not prove Quest panel input, stage collision, panel alpha,
  3D placement, hand/controller raycasts, or frame performance.
- The application input-method defect remains deferred. The browser console is
  positioning-only and must not be mistaken for an implementation of that input
  engine or change Workbench layout, stage input, visibility, or performance
  work.
- The comments are local sample data. No account, reply API, like/dislike API,
  authentication, or error state is modeled.
- The remaining runtime Workbench layout migration is intentionally paused until
  this prototype direction is accepted.

## Before Runtime Migration

1. Confirm the prototype navigation slot and the final home category set.
2. Define authenticated API, unavailable, error, and favorite-folder behavior
   for the approved Like/Coin/Favorite placement.
3. Translate approved prototype states into explicit Workbench reducer/module
   requirements.
4. Use the smallest number of Spatial/Android panels possible.
5. Preserve `MeshCollision.NoCollision` on display-only overlays so they cannot
   block MediaStage raycasts.
6. Build via `scripts/build-windows-debug.ps1`; do not install, deploy, or
   launch an APK unless explicitly requested.
7. Validate the implemented composition on Quest separately from this browser
   prototype.
