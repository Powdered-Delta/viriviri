package com.m0e_n00b.spatialworkbench.core

enum class PanelSlot {
  MEDIA_STAGE,
  TRANSPORT,
  SYSTEM_TOOLBAR,
  BROWSE,
  CONTEXT,
  FOCUS,
  ACTION_SHEET,
}

enum class ImmersiveLayoutMode {
  WATCH,
  FOCUS,
  EDIT,
}

enum class PanelVisibility {
  VISIBLE,
  HIDDEN,
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

data class SlotPlacement(
    val visibility: PanelVisibility,
    val anchor: SpatialAnchor,
    val offsetMeters: Meters3 = Meters3(),
    val rotationDegrees: EulerDegrees = EulerDegrees(),
    val sizeMeters: PanelSizeMeters,
    val shape: PanelShape = PanelShape.Quad,
    val parentSlot: PanelSlot? = null,
)

data class ContentExclusion(val id: String, val anchor: SpatialAnchor)

data class PipDock(
    val id: String,
    val anchor: SpatialAnchor,
    val sizeMeters: PanelSizeMeters,
    val exclusion: ContentExclusion,
)

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
) {
  fun placement(mode: ImmersiveLayoutMode, slot: PanelSlot): SlotPlacement? =
      layouts[mode]?.placements?.get(slot)
}
