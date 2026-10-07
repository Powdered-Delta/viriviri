package com.m0e_n00b.spatialworkbench.core

enum class PanelSlot {
  MEDIA_STAGE,
  TRANSPORT,
  SYSTEM_TOOLBAR,
  BROWSE,
  CONTEXT,
  FOCUS,
  ACTION_SHEET,
  SHORTS_DETAILS,
  SHORTS_COMMENTS,
}

enum class ImmersiveLayoutMode {
  WATCH,
  FOCUS,
  EDIT,
  SHORTS,
}

enum class SpatialDepthRelation {
  ROOT,
  FRONT_OF_PARENT,
  BEHIND_PARENT,
}

enum class PanelVisibility {
  VISIBLE,
  HIDDEN,
}

enum class PanelPresentationPolicy {
  PERSISTENT,
  AUTO_FADE,
  ON_DEMAND,
  TRANSIENT,
  THEME_CONTROLLED,
}

enum class CanvasOverflowPolicy {
  CLIP,
  VISIBLE,
}

data class Meters3(val x: Float = 0f, val y: Float = 0f, val z: Float = 0f)

data class EulerDegrees(val pitch: Float = 0f, val yaw: Float = 0f, val roll: Float = 0f)

data class PanelSizeMeters(val width: Float, val height: Float)

sealed interface PanelShape {
  data object Quad : PanelShape

  data class Cylinder(val radiusMeters: Float, val arcDegrees: Float) : PanelShape
}

sealed interface SpatialAnchor {
  data class SceneAnchor(val name: String) : SpatialAnchor

  data class SlotAnchor(val slot: PanelSlot) : SpatialAnchor
}

/**
 * Local placement of a panel relative to a reference frame.
 *
 * The reference frame is either a scene anchor ([anchor] non-null, owned by the scene and read-only
 * at runtime, see design §5.5 rule 1) or the parent panel/slot ([parentSlot] non-null). Both may be
 * present: rule 4 of design §5.5 composes `inverse(parentWorldPose) x anchorWorldPose x themeLocal`,
 * which is how the current `TRANSPORT` placement is declared. A placement with neither is invalid.
 */
data class SlotPlacement(
    val visibility: PanelVisibility,
    val anchor: SpatialAnchor?,
    val offsetMeters: Meters3 = Meters3(),
    val rotationDegrees: EulerDegrees = EulerDegrees(),
    val sizeMeters: PanelSizeMeters,
    val shape: PanelShape = PanelShape.Quad,
    val parentSlot: PanelSlot? = null,
    val depthRelation: SpatialDepthRelation = SpatialDepthRelation.ROOT,
)

data class ContentExclusion(val id: String, val anchor: SpatialAnchor)

data class PipDock(
    val id: String,
    val anchor: SpatialAnchor,
    val sizeMeters: PanelSizeMeters,
    val exclusion: ContentExclusion,
)

data class WorkbenchCanvas(
    val id: String,
    val mode: ImmersiveLayoutMode,
    val visibleSlots: Set<PanelSlot>,
    val visibleComponentIds: Set<String> = emptySet(),
    val overflowPolicy: CanvasOverflowPolicy = CanvasOverflowPolicy.CLIP,
    val preservesHitTesting: Boolean = true,
)

/**
 * UI composition group (design §4.2). It only owns UI composition: members, their render order and
 * the slots they may be attributed to. It never carries entity, grab or render-target semantics.
 */
data class ComponentGroup(
    val id: String,
    val members: List<ComponentNode>,
    /** Human-facing label; blank means "same as [id]". */
    val label: String = "",
    /** Slots this group may be attributed to; empty = unrestricted. */
    val allowedSlots: Set<PanelSlot> = emptySet(),
    /** True when the group may be referenced as a reusable recipe by another theme. */
    val reusable: Boolean = true,
) {
  val effectiveLabel: String
    get() = label.ifBlank { id }
}

data class ThemeLayout(
    val placements: Map<PanelSlot, SlotPlacement>,
    val pipDocks: Map<String, PipDock> = emptyMap(),
)

enum class SystemToolbarModule {
  CLOCK,
  BATTERY,
  ENTER_2D,
  PASSTHROUGH,
  MORE_ACTIONS,
}

data class ToolbarModule(val id: String, val module: SystemToolbarModule)

enum class ComponentKind {
  PANEL,
  TAB_BAR,
  DRAWER,
  POPUP,
  ACTION_SHEET,
  FOCUS_PANEL,
  COLUMN,
  ROW,
  GRID,
  STACK,
  SPLIT,
  SCROLL_AREA,
  SLOT,
  OVERLAY,
  SEMANTIC_MODULE,
}

data class ComponentNode(
    val id: String,
    val kind: ComponentKind,
    val properties: Map<String, String> = emptyMap(),
    val dataBindings: Map<String, String> = emptyMap(),
    val actions: List<ThemeAction> = emptyList(),
    val children: List<ComponentNode> = emptyList(),
    val namedSlots: Map<String, List<ComponentNode>> = emptyMap(),
)

enum class ThemeRoute {
  CREATOR,
  COLLECTION,
  SERIES,
  CHANNEL,
}

enum class FocusTarget {
  CREATOR,
  COLLECTION,
  SERIES,
}

enum class DanmakuMode {
  OFF,
  FLAT,
  SPATIAL,
}

sealed interface ThemeAction {
  data class OpenRoute(val route: ThemeRoute) : ThemeAction

  data class OpenFocus(val target: FocusTarget) : ThemeAction

  data class SetPlaybackSpeed(val speed: Float) : ThemeAction

  data class SetDanmakuMode(val mode: DanmakuMode) : ThemeAction

  data object SwitchTo2D : ThemeAction

  data object TogglePassthrough : ThemeAction
}

data class SpatialTheme(
    val id: String,
    val displayName: String,
    val sceneReference: String,
    val layouts: Map<ImmersiveLayoutMode, ThemeLayout>,
    val toolbarModules: List<ToolbarModule>,
    val rootComponent: ComponentNode,
    val componentGroups: List<ComponentGroup> = emptyList(),
    val canvases: List<WorkbenchCanvas> = emptyList(),
    val presentationPolicies: Map<PanelSlot, PanelPresentationPolicy> = emptyMap(),
  val palette: CinemaPalette = CinemaPalette.DARK,
    /** Declarative theme schema revision; migrated before validation (R2). */
    val schemaVersion: Int = ThemeSchema.CURRENT_SCHEMA_VERSION,
    /**
     * Scene-provided anchor node names this theme depends on. Anchors are authored in
     * Meta Spatial Editor and are the only source of static spatial placement (design §5.1).
     */
    val requiredAnchors: Set<String> = emptySet(),
    /**
     * Panel instance registry (1 panel = 1 instance). Per-mode geometry stays in
     * [ThemeLayout.placements]; [PanelSpec.placement] carries the theme's default-mode geometry.
     */
    val panels: Map<PanelId, PanelSpec> = emptyMap(),
    /** Slot -> panel bindings. A slot may be bound by N panels only with an explicit share policy. */
    val slotBindings: List<SlotBinding> = emptyList(),
    /** Spatial manipulation groups (R4.2). Independent id space from UI composition. */
    val grabGroups: List<GrabGroupSpec> = emptyList(),
    /** Danmaku render-target groups (R4.3). Independent id space from slots. */
    val danmakuGroups: List<DanmakuSurfaceGroup> = emptyList(),
    val danmakuOcclusionDomains: List<DanmakuOcclusionDomain> = emptyList(),
) {
  fun placement(mode: ImmersiveLayoutMode, slot: PanelSlot): SlotPlacement? =
      layouts[mode]?.placements?.get(slot)

  fun presentationPolicy(slot: PanelSlot): PanelPresentationPolicy =
      presentationPolicies[slot] ?: PanelPresentationPolicy.THEME_CONTROLLED

  fun allowsDefaultCanvasHide(slot: PanelSlot): Boolean =
      presentationPolicy(slot) != PanelPresentationPolicy.PERSISTENT

  fun panel(id: PanelId): PanelSpec? = panels[id]

  /** All bindings that claim [slot], in declaration order. */
  fun bindingsFor(slot: SlotId): List<SlotBinding> = slotBindings.filter { it.slot == slot }
}
