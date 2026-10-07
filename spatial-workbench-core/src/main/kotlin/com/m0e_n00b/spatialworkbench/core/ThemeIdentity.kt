package com.m0e_n00b.spatialworkbench.core

/*
 * Five independent identity spaces (design §4.5, R7.1). They must never be used interchangeably:
 *
 *   PanelId         one spatial panel instance (1 panel = 1 instance)
 *   SlotId          a semantic capability/content position; a slot may be bound by N panels
 *   ComponentId     a UI component inside a panel, from the controlled component registry
 *   EntityRef       a semantic entity reference resolved by an app adapter
 *   OverlaySurfaceId a danmaku/caption render target; NOT a UI slot
 */

/** One spatial panel instance. */
@JvmInline
value class PanelId(val value: String) {
  override fun toString(): String = value
}

/** A semantic capability/content position. [PanelSlot] stays the single enum for this id space. */
typealias SlotId = PanelSlot

/** A UI component id from the controlled component registry. */
@JvmInline
value class ComponentId(val value: String) {
  override fun toString(): String = value
}

/** A semantic entity reference resolved to a Spatial `Entity` by an app adapter. */
@JvmInline
value class EntityRef(val value: String) {
  override fun toString(): String = value
}

/** A danmaku/caption render target. Distinct from [SlotId] and from the single video Surface. */
@JvmInline
value class OverlaySurfaceId(val value: String) {
  override fun toString(): String = value
}

/**
 * Panel taxonomy (design §2.8, R7.4). The class decides which geometry path can actually apply, so
 * "the theme can resize every panel" is refused at the schema layer instead of silently ignored.
 */
enum class PanelClass {
  /** Type A: ordinary registered panel; register-once + runtime reshape. */
  UI_PANEL,
  /** Type B: scene-authorized panel; the adapter takes over reparent + `PanelDimensions` writes. */
  SCENE_PANEL,
  /** Type C: hand-built compositor media panel; dedicated adapter, restricted geometry subset. */
  MEDIA_SURFACE,
  ;

  /** Geometry a theme is allowed to declare for this panel class (design §5.6, §5.3). */
  val allowedGeometry: Set<ThemeableGeometry>
    get() =
        when (this) {
          UI_PANEL -> ThemeableGeometry.entries.toSet()
          SCENE_PANEL ->
              setOf(
                  ThemeableGeometry.SIZE_METERS,
                  ThemeableGeometry.SHAPE,
                  ThemeableGeometry.OFFSET_METERS,
                  ThemeableGeometry.ROTATION_DEGREES,
                  ThemeableGeometry.Z_ORDER,
              )
          // The compositor layer order, stereo mode and video Surface ownership are not themeable.
          MEDIA_SURFACE -> setOf(ThemeableGeometry.SIZE_METERS, ThemeableGeometry.SHAPE)
        }

  /**
   * Hit testing the class already owns. A declared value different from this default must be inside
   * [allowedGeometry]; otherwise the theme advertises geometry the class silently ignores.
   */
  val defaultHitTest: PanelHitTest
    get() = if (this == MEDIA_SURFACE) PanelHitTest.CUSTOM_RECT else PanelHitTest.PANEL

  /** Overflow behaviour the class already owns; see [defaultHitTest]. */
  val defaultOverflow: CanvasOverflowPolicy
    get() = CanvasOverflowPolicy.CLIP
}

/** Theme-static geometry knobs. Values are read-only once the theme is loaded (design §5.5 rule 5). */
enum class ThemeableGeometry(val wireName: String) {
  SIZE_METERS("sizeMeters"),
  SHAPE("shape"),
  OFFSET_METERS("offsetMeters"),
  ROTATION_DEGREES("rotationDegrees"),
  Z_ORDER("zOrder"),
  HIT_TEST("hitTest"),
  OVERFLOW("overflow"),
  ;

  companion object {
    fun fromWireName(name: String): ThemeableGeometry? = entries.firstOrNull { it.wireName == name }
  }
}

/**
 * The only values a theme may hand to *user runtime adjustment* (design §5.5 rule 5). Geometry is not
 * on this list: a theme cannot disguise a static value as a user setting.
 */
enum class UserAdjustableProperty(val wireName: String) {
  STAGE_SCALE("stageScale"),
  STAGE_HEIGHT("stageHeight"),
  ;

  companion object {
    fun fromWireName(name: String): UserAdjustableProperty? = entries.firstOrNull { it.wireName == name }
  }
}

/** Hit-test ownership of a panel surface. */
enum class PanelHitTest {
  NONE,
  PANEL,
  CUSTOM_RECT,
}

/** Where a child panel attaches to its parent panel. */
enum class AttachPoint(val wireName: String) {
  TOP_LEFT("topLeft"),
  TOP_CENTER("topCenter"),
  TOP_RIGHT("topRight"),
  CENTER_LEFT("centerLeft"),
  CENTER("center"),
  CENTER_RIGHT("centerRight"),
  BOTTOM_LEFT("bottomLeft"),
  BOTTOM_CENTER("bottomCenter"),
  BOTTOM_RIGHT("bottomRight"),
  ;

  companion object {
    fun fromWireName(name: String): AttachPoint? =
        entries.firstOrNull { it.wireName.equals(name, ignoreCase = true) }
  }
}

/**
 * One panel instance and its themeable geometry.
 *
 * [placement] is the theme-static, read-only geometry for the theme's default mode; per-mode
 * overrides stay in [ThemeLayout.placements] (they are the existing model and are still consumed by
 * the canvas recipes). [parentPanelId] is the panel-space parent (`panels[x].parent.panel`); the
 * legacy slot-space parent stays in [SlotPlacement.parentSlot].
 */
data class PanelSpec(
    val id: PanelId,
    val panelClass: PanelClass,
    val componentId: ComponentId,
    val placement: SlotPlacement,
    val zOrder: Int = 0,
    val hitTest: PanelHitTest = panelClass.defaultHitTest,
    val overflow: CanvasOverflowPolicy = panelClass.defaultOverflow,
    val themeableGeometry: Set<ThemeableGeometry> = emptySet(),
    val userAdjustable: Set<UserAdjustableProperty> = emptySet(),
    val parentPanelId: PanelId? = null,
    val parentAttach: AttachPoint? = null,
  ) {
  /** True when the theme is allowed to declare [property] for this panel. */
  fun canTheme(property: ThemeableGeometry): Boolean = property in panelClass.allowedGeometry

  /** True when the theme declares (and therefore owns) [property] as a static value. */
  fun isThemeStatic(property: ThemeableGeometry): Boolean = property in themeableGeometry

  /** True when [property] is owned by an existing user setting, not by the theme. */
  fun isUserAdjustable(property: UserAdjustableProperty): Boolean = property in userAdjustable
}

/** How several panels that claim the same slot relate to each other (R7.2, design §4.5). */
enum class SlotSharePolicy {
  /** Exactly one panel may claim the slot (default). Any extra claim is `DuplicateSlotOwner`. */
  EXCLUSIVE,

  /** N panels share one semantic state; declarations must pair up in one non-blank `mirrorGroup`. */
  MIRRORED,

  /** N panels each keep their own state for the same slot. */
  INDEPENDENT,
}

/**
 * A slot -> panel binding. A slot is a semantic capability that may map to more than one panel, so
 * the validator cannot be "one slot, one panel"; each extra claim must be declared here.
 */
data class SlotBinding(
    val slot: SlotId,
    val panelId: PanelId,
    val sharePolicy: SlotSharePolicy = SlotSharePolicy.EXCLUSIVE,
    /** Required for [SlotSharePolicy.MIRRORED]; all mirrored claims of a slot share one group. */
    val mirrorGroup: String? = null,
) {
  val path: String
    get() = "slotBindings[${slot.name}->${panelId.value}]"
}

/** True when the panel declares at least one property that only user settings may own. */
val PanelSpec.hasUserAdjustableGeometry: Boolean
  get() = userAdjustable.isNotEmpty()
