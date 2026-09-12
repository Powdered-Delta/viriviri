<!-- TRELLIS:START -->
# Trellis Instructions

These instructions are for AI assistants working in this project.

This project is managed by Trellis. The working knowledge you need lives under `.trellis/`:

- `.trellis/workflow.md` — development phases, when to create tasks, skill routing
- `.trellis/spec/` — package- and layer-scoped coding guidelines (read before writing code in a given layer)
- `.trellis/workspace/` — per-developer journals and session traces
- `.trellis/tasks/` — active and archived tasks (PRDs, research, jsonl context)

If a Trellis command is available on your platform (e.g. `/trellis:finish-work`, `/trellis:continue`), prefer it over manual steps. Not every platform exposes every command.

If you're using Codex or another agent-capable tool, additional project-scoped helpers may live in:
- `.agents/skills/` — reusable Trellis skills
- `.codex/agents/` — optional custom subagents

Managed by Trellis. Edits outside this block are preserved; edits inside may be overwritten by a future `trellis update`.

<!-- TRELLIS:END -->

# Execution Efficiency

## Scope Before Tools

Before editing, state one concrete acceptance target for the current patch. Do not combine unrelated follow-up ideas into a half-finished patch.

For a narrowly scoped issue, inspect only the direct call chain first:

```text
state/reducer -> host callback -> UI component -> tests
```

Use direct file reads and targeted searches for a few known files. Reserve context indexing, repository-wide inventory, or broad architecture research for unknown, cross-cutting, or genuinely large areas.

## Complete Vertical Slice

For a behavior change, prepare one coherent patch across every required boundary before compiling:

```text
state contract -> reducer/host -> UI -> tests
```

Do not repeatedly build after each small edit. Compile after the whole vertical slice is present; fix all compilation failures in that pass; run the full Windows verification only after the slice is coherent.

When a new user instruction changes the intended behavior, stop extending the old patch. Re-state the new acceptance target, update the task/design record when required, then make a fresh coherent patch.

## Verification Cadence

Use the cheapest verification that can catch the current risk:

```text
pure reducer / utility change
  -> targeted JVM test task

shared Compose change
  -> :spatial-workbench-compose:testDebugUnitTest

app behavior, resources, Spatial panel registration, scene export, or cross-module change
  -> .\scripts\build-windows-debug.ps1
```

Do not re-run the complete Windows build after every individual edit. Run it once after the related changes and targeted tests are complete, and again only when a subsequent patch changes build-relevant behavior.

All Android verification commands must run in the Windows environment. Do not install, deploy, or launch the APK unless the user explicitly asks.

## Edit Hygiene

- Read the exact target block before an exact edit; merge nearby changes into one edit.
- Check a file's line endings before editing it. Preserve its existing convention unless intentionally normalizing the entire file with user-aware review.
- For CRLF files, use a CLI normalizer instead of repeatedly editing individual lines. On Windows, prefer an explicit PowerShell conversion that preserves UTF-8 without BOM:

```powershell
$content = [System.IO.File]::ReadAllText($path)
$content = $content -replace "`r?`n", "`r`n"
[System.IO.File]::WriteAllText($path, $content, [System.Text.UTF8Encoding]::new($false))
```

- Before normalizing, record the file's current convention. After normalizing, run `git diff --check` and `git diff --numstat`; a narrow patch must not become a whole-file rewrite without an explicit reason.
- Run `git diff --check` before builds and before commits.
- Treat pre-existing modified and untracked files as user-owned unless they are directly in scope. Do not stage unrelated files.

## Spatial UI Boundaries

For spatial UI changes, separate these concerns before implementation:

```text
static spatial placement
  -> Meta Spatial Editor / mse-agent scene data

dynamic panel visibility, input, and lifecycle
  -> Kotlin host / system state

Compose visual state and callbacks
  -> Compose components
```

A single user interaction must have one owner. Do not let stage click listeners, outer-dismiss geometry, panel callbacks, and hover/touch fallback handlers independently perform the same show/hide transition.

When adding or changing a panel, document:

```text
owner
parent / anchor
visibility state
input / hit-test ownership
relative depth
video-surface ownership (normally none)
```

# Meta Spatial SDK

This is a **Meta Quest VR/MR headset** app, not a standard Android phone/tablet app. It renders 3D content in the user's physical space with head tracking, hand tracking, and controller input.

## Architecture

The SDK uses an **Entity-Component-System (ECS)** architecture:

- **Entities** hold **Components** (data). **Systems** process them each frame via queries.
- **Custom components** are declared in XML schemas (`app/src/main/components/*.xml`) and auto-generate Kotlin classes at build time.
- **Scenes** can be authored visually in Meta Spatial Editor (`.glxf` files) or built entirely in code at runtime.
- **Panels** render 2D Android UI (Compose or XML layouts) onto surfaces positioned in 3D space.
- **Units are meters.** e.g. `Transform(Pose(Vector3(0f, 1.5f, -2f)))` = 1.5m up, 2m forward.

## Key Patterns

```kotlin
// Create entity with components
Entity.create(listOf(Transform(pose), Mesh(Uri.parse("mesh://box"))))

// Procedural meshes (no 3D model file needed)
// mesh://sphere, mesh://box, mesh://plane, mesh://quad, mesh://skybox

// Query entities in a System
val q = Query.where { has(Transform.id, MyComponent.id) }
for (entity in q.eval()) { /* per-frame logic */ }

// Register custom components and systems
componentManager.registerComponent<MyComp>(MyComp.Companion)
systemManager.registerSystem(MySystem())
```

## Docs

https://developers.meta.com/horizon/llmstxt/documentation/spatial-sdk/llms.txt/

## Meta Spatial Editor
Meta Spatial Editor is a spatial composition tool for Spatial SDK. Import, organize, and transform your assets into visual compositions and export them into Spatial SDK projects.

### mse-agent
mse-agent is a command-line tool included with Meta Spatial Editor for creating and modifying 3D scenes programmatically. Run `mse-agent readme` for the full command reference.

### Step 1: Install Meta Spatial Editor (if not already installed)

Check if mse-agent exists at one of these paths:
 - Mac: `/Applications/Meta Spatial Editor.app/Contents/MacOS/mse-agent`
 - Windows: `C:\Program Files\Meta Spatial Editor\V*\Resources\mse-agent` (use the latest version folder)
 - Linux: `<package-root>/mse-agent` (`<package-root>` is wherever you extracted the downloaded package)

If not found, download and install Meta Spatial Editor:
 - Mac: https://developers.meta.com/horizon/downloads/package/meta-spatial-editor-for-mac/
 - Windows: https://developers.meta.com/horizon/downloads/package/meta-spatial-editor-for-windows/
 - Linux (headless CLI only): https://developers.meta.com/horizon/downloads/package/meta-spatial-editor-cli-for-linux/
### 2. Launch the editor

You can launch the editor in one of two ways:

#### Option A: GUI mode (Mac and Windows)

Launch Meta Spatial Editor and open your project scene. This is the standard workflow for visual editing.

- Mac: `open -a "/Applications/Meta Spatial Editor.app" "app/scenes/Main.metaspatial"`
- Windows: `cmd /c start /B "" "C:\Program Files\Meta Spatial Editor\V*\MetaSpatialEditor.exe" "app/scenes/Main.metaspatial"`

#### Option B: CLI batch mode (Mac, Windows, and Linux)

You can run the editor in headless batch mode with no UI. This is useful when working in a terminal, running on a remote server, or on Linux where the GUI is not available. CLI batch mode is available starting from v16.

Run the following command from your project root directory to start the editor in batch mode:

| Platform | Command |
|----------|---------|
| Mac      | `/Applications/Meta Spatial Editor.app/Contents/MacOS/CLI serve -p app/scenes/Main.metaspatial &` |
| Windows  | `start /B "C:\Program Files\Meta Spatial Editor\V*\Resources\CLI.exe" serve -p app\scenes\Main.metaspatial` |
| Linux    | `<package-root>/MetaSpatialEditorCLI serve -p app/scenes/Main.metaspatial &>/dev/null &` |

> **Note:** The `serve` command starts the editor in headless mode, listening for commands from `mse-agent`. It is a long-running process — launch it in the background (as shown above) so it does not block your terminal.
>
> - On **Windows**, replace `V*` with the latest version folder (for example, `V16`).
> - On **Linux**, `<package-root>` is the root of the downloaded package.

## Rules

**BEFORE writing any `Entity.create()` code for new scene objects, STOP and answer these questions:**

1. **Is this entity static?** (fixed position, no runtime data, no dynamic count)
   - YES → **Use Meta Spatial Editor (mse-agent)** to add it to the `.metaspatial` scene file. Do NOT write Kotlin code.
   - If mse-agent is not installed, **do NOT fall back to Kotlin code**. Instead, install the latest Meta Spatial Editor first. If installation is not possible, ask the user before proceeding with Kotlin code.
   - NO → Use Kotlin runtime code in `onSceneReady()` or a System.

2. **Does this project have a `.metaspatial` scene?**
   - Check: `ls app/scenes/*.metaspatial`
   - If yes, static entities belong there — not in Kotlin.

3. **Is the task a mix of static and dynamic?**
   - Author static parts in Meta Spatial Editor, dynamic parts in Kotlin.

**Why this matters:** Scenes authored in Meta Spatial Editor are visually inspectable — designers and developers can review layout, adjust positions, and iterate without rebuilding the app. Hardcoding static entities in Kotlin buries spatial layout in code where it's invisible and harder to maintain.
## Meta Quest Agentic Tools (metavr + hz skills)

Meta-official Horizon OS tooling is available in this repo (Apache-2.0, installed from
[meta-quest/agentic-tools](https://github.com/meta-quest/agentic-tools)):

- **Skills** (project-scoped under `.agents/skills/`): `hz-quest-verify-first`, `hz-spatial-sdk`,
  `hz-platform-sdk`, `hz-immersive-designer`, `metavr-cli`. The skill catalog loads them automatically.
- **metavr CLI**: invoke without install via `npx -y metavr ...` (Node 18+; verified working).
- **metavr MCP server**: configured in project-root `mcp.json` (40+ device/docs/perf tools).
  Shareable; it contains no user secrets.

### Docs-first (replaces guessing / web search)

For any Quest/Horizon/Spatial SDK API, manifest entry, or version-specific behavior, prefer the
authoritative path over training-data memory — `web_search` is not reliable in this environment:

1. Load the `hz-quest-verify-first` skill before answering or coding Quest-specific behavior; it
   enforces verification against current Meta docs / metavr.
2. Use `npx -y metavr docs search "<query>"` (or the metavr MCP docs tools) to fetch current
   Spatial SDK / Platform SDK guidance, then load `hz-spatial-sdk` / `hz-platform-sdk` as needed.
3. Use `hz-immersive-designer` when reviewing spatial layout, comfort, or readability.

This complements, and does not replace, mse-agent: mse-agent owns **static scene authoring**
(see the rules above); metavr/hz skills own **device control, docs, and code-level guidance**.

### Device control is gated — do NOT auto-install

The metavr MCP/CLI can install, launch, stop apps and pull device logs. These are **explicit-action
only**:

- Do **not** install, deploy, update, or launch an APK, and do not perform device writes, unless
  the user explicitly asks in the current turn. This restates the existing rule for the APK build;
  it applies equally to `metavr app install/launch` and MCP device-mutation tools.
- Read-only device checks (e.g. `metavr device list/info`, doc search) are fine.

### Quest ADB / platform-tools must be current

Quest device access breaks after a headset OS upgrade when the local **ADB/platform-tools is too
old** (device shows unauthorized / drops off). Before any on-device step, ensure platform-tools is
upgraded to the latest release:

```
npx -y metavr doctor        # reports platform-tools/adb version and device auth state
```

If ADB is stale or the headset is unauthorized after an update, upgrade platform-tools (or re-run
metavr's setup) and re-authorize the headset before continuing — do not work around an old adb.
## metavr Tool Reference (concrete commands)

Invoke via `npx -y metavr <group> <verb> ...` (Node 20+; npx always pulls latest).
Exact flags: `npx -y metavr --markdown-help` or `--help` per command.

### Command groups

| Group | Purpose | Representative commands |
|---|---|---|
| `docs` | Meta Quest / Horizon docs + API search (**preferred over web_search**) | `docs search "<query>"`, `docs fetch <url>`, `docs api-search <query>`, `docs api-details <name>`, `docs api-stats` |
| `device` | Headset discovery / state | `device list`, `device info <id>`, `device connect <ip>` (WiFi), `device wait`, `device battery`, `device controllers`, `device health-check`, `device configure-testing setup/restore` |
| `app` | App lifecycle (mutating → gated, see below) | `app list`, `app info <pkg>`, `app foreground`, `app install <apk>`, `app launch <pkg>`, `app stop <pkg>`, `app uninstall <pkg>`, `app clear <pkg>`, `app path <pkg>` |
| `capture` | Screenshots from headset | `capture screenshot -o <file>.png` |
| `log` | Device logs | `log --tag <TAG>`; raw: `adb logcat` |
| `audio` | Device audio | `audio status`, `audio set <0-15>`, `audio mute`, `audio unmute` |
| `perf` | Perfetto performance analysis | `perf capture`, `perf load <id>`, `perf context`, `perf analyze-trace`, `perf query <id> <sql>`, `perf thread-state`, `perf gpu-counters`, `perf compare <a> <b>`, `perf traces` |
| `files` | Device file ops | `files list/pull/push/remove/mkdir` |
| `adb` / `shell` | Low-level ADB / device shell | `metavr adb ...`, `metavr shell <cmd>` |
| `asset` | Meta 3D asset library search | `asset search <query>` |
| `mcp` | MCP server management | `mcp server`, `mcp install <tool>` |
| `config` | metavr config | `config list/read/write/reset` |
| `doctor` | Environment health (adb/platform-tools/device auth) | `doctor` |

### metavr MCP server (project config: `mcp.json`)

Started via `npx -y metavr mcp server` (~40 tools). Exposes the same domains:
device management, app lifecycle, docs search/fetch, API search, perf trace analysis,
file ops, audio, screenshots, 3D asset search. **Mutating tools obey the same gating
rule as the CLI: no install/launch/stop/clear/uninstall/reboot unless the user asked
explicitly in the current turn.**

### Gating recap (applies to CLI and MCP)

- **Always allowed (read-only):** `docs *`, `device list/info/wait/battery/controllers`, `app list/info/foreground`, `perf *`, `log`, `files list/pull`, `audio status`, `asset search`, `doctor`.
- **Require explicit user request (mutating):** `app install/launch/stop/uninstall/clear`, `device reboot/wake/connect/proximity`, `files push/remove/mkdir`, `audio set/mute/unmute`, `device configure-testing *`, screenshots (`capture screenshot`).
- When in doubt, run the read-only check first and ask.

# UI Structure & Terminology Doc

`docs/ui-structure-and-terminology.md` records UI regions and their code-level names, hosts,
Spatial entities, and slots, as **currently implemented**. It is not a design document:

```text
docs/ui-structure-and-terminology.md         -> what the code does today
docs/immersive-ui-low-code-architecture.md   -> the target architecture (many items unimplemented)
```

The source tree is authoritative. The architectural conventions themselves live in section 8 of
that document; the rules below only cover when it must change and how to keep it honest.

## When It Must Change

A change is not complete until the matching section is updated:

| Change | Sections |
| --- | --- |
| Add, remove, or rename a Spatial panel, entity node, or panel registration | 2, 3, 5 |
| Change `PanelSlot`, `WorkbenchModule`, a route enum, or a state machine | 3, 4, 6 |
| Change stage layout constants or derived geometry | 2 |
| Wire up or retire a `[契约]` / `[未接线]` item | 6, then 7 |
| Resolve or introduce a naming ambiguity | 6, 7 |

## Rules

1. **Source is authoritative.** Never introduce a term the code does not use. To rename, change the
   source first, then sync the doc.
2. **Every term must resolve.** Each backticked identifier must exist in the tree. Before finishing,
   verify by whole-word search over `app/src`, `spatial-workbench-core/src`,
   `spatial-workbench-compose/src`, `app/scenes`, and `app/src/main/components`.
3. **Tag the layer.** Mark each entry `[实现]` (live in runtime), `[契约]` (only in
   `spatial-workbench-core`), or `[未接线]` (named in runtime, with no entity or behavior). Never
   mix a contract-layer claim with a runtime fact in one statement.
4. **Update the diagram, not just the prose.** Hierarchy, ownership, and depth changes belong in the
   ASCII / Mermaid blocks.
5. **Cross-reference sibling docs.** Link `immersive-ui-low-code-architecture.md`,
   `prototypes/workbench/README.md`, and `spatial-coordinates.md` instead of restating them.

Doc-only edits do not need a build; run `git diff --check` and the whole-word identifier check
above. If the change also touched panel registration, scene data, resources, or cross-module code,
follow the full build in "Verification Cadence".
