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
- If a CRLF file produces `git diff --check` noise, fix the changed lines or perform a deliberate, isolated newline normalization. Do not hide a whole-file newline rewrite inside a feature patch.
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
