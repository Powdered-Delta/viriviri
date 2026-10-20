package com.m0e_n00b.viriviri

import kotlin.math.cos
import kotlin.math.sin

/**
 * Tunable spatial layout for the immersive Workbench.
 *
 * Units are meters. Panels attached to the MediaStage (center / nav / transport /
 * keyboard) use the MediaStage LOCAL frame, where -Z faces the user and -Y is down;
 * their values are local offsets from the video surface. The left/right rails are
 * authored in WORLD coordinates (they are not parented to the stage) and use
 * [STAGE_WORLD_Z] as the depth anchor.
 *
 * Values are centralized here so they can later be bound to a settings screen or
 * presets (same pattern as PlaybackCanvasSize / AppPreferences). The defaults match
 * the current on-device layout. UI panels stay flat; only placement and yaw follow
 * the arc. Depth order from the video outward is: video (0) -> nav/center ->
 * transport -> keyboard, each more negative in local Z (closer to the user).
 */
data class WorkbenchLayoutConfig(
    // ---- Center workspace panel (Search empty / results / video list) ----
    /** Center panel physical width in meters (local X). */
    val centerWidth: Float = 1.5f,
    /** Center panel physical height in meters (local Y); grows upward from [centerBottomLocalY]. */
    val centerHeight: Float = 1.20f,
    /** Center panel forward offset from the video surface. Negative = toward the user. */
    val centerLocalZ: Float = -0.52f,
    /** Fixed bottom edge of the center panel (local Y); the top edge rises as [centerHeight] grows. */
    val centerBottomLocalY: Float = -0.63f,

    // ---- Left / right rails (vertical mirrors around the center panel) ----
    /** Shared physical width of both rails in meters. */
    val railWidth: Float = 0.7f,
    /** Left (Detail) rail physical height in meters; its body scrolls. */
    val leftRailHeight: Float = 0.9f,
    /** Right (Context) rail physical height in meters. */
    val rightRailHeight: Float = 0.58f,
    /** Horizontal gap (meters, along X) between a center panel edge and the inner edge of a rail. */
    val railGap: Float = 0.05f,
    /** Rail yaw in degrees toward the stage center; left uses -value, right uses +value. */
    val railYawDegrees: Float = 45f,
    /** World Y (height) shared by both rail centers; tracks the stage's authored default height. */
    val railCenterWorldY: Float = STAGE_DEFAULT_WORLD_Y,

    // ---- Top global navigation bar (mr_panel, local frame) ----
    /** Nav bar vertical offset (local Y); positive = up. */
    val navLocalY: Float = 0.72f,
    /** Nav bar forward offset (local Z); negative = toward the user. */
    val navLocalZ: Float = -0.52f,
    /**
     * Nav bar pitch in degrees, applied as `Quaternion(pitch, 0f, 0f)` on the panel's LOCAL pose.
     *
     * SIGN CONVENTION — read this before changing the number (it cost one dev cycle to establish):
     * the panel's normal is **-Z** (toward the user) in the stage-local frame, and a rotation of **+θ
     * about +X lifts that normal UP**, i.e. tips the bar BACK over the user's head. A **negative** angle
     * tips it DOWN, so its face looks at someone standing in front of the workbench.
     *
     * `Quaternion(x, y, z)` takes DEGREES — it does `Math.toRadians(x * 0.5)` internally — so 8f really
     * is 8 degrees; the old value was not a radians/degrees mistake, it was the wrong sign.
     *
     * The previous `+8f` ("faces the user" per the old comment) therefore produced the exact opposite:
     * the bar leaned back with its normal pointing at the ceiling, and the Search button on it could not
     * be aimed at (reported on device 2026-10-20).
     *
     * The value is fixed, and the panel is rigidly parented, so the optimal angle depends on where the
     * user raised the stage to. At the default authored stage height this is "aim the normal at the
     * eyes": panel world ≈ (0, 2.86, 1.48), eyes ≈ (0, 1.6, 0) → atan(1.26 / 1.48) ≈ 40° down.
     * Raising the stage makes the ideal steeper (≈ -55° at stage Y 3.0); that is a known limitation, not
     * something a constant can solve.
     */
    val navPitchDegrees: Float = -40f,

    // ---- Transport controls (local frame) ----
    /** Transport vertical offset (local Y); negative = below center. */
    val transportLocalY: Float = -0.78f,
    /** Transport forward offset (local Z); sits slightly in front of the center panel. */
    val transportLocalZ: Float = -0.66f,
    /**
     * Transport pitch in degrees. POSITIVE = normal tips UP, which is correct here: this panel sits at
     * or below eye height, so it must look up at the user. Do not "fix" it to negative by analogy with
     * [navPitchDegrees] — the correct sign depends on whether the panel is above or below the eyes.
     */
    val transportPitchDegrees: Float = 20f,

    // ---- Application keyboard / input-method panel (local frame) ----
    /** Keyboard vertical offset (local Y). */
    val keyboardLocalY: Float = -0.48f,
    /** Keyboard forward offset (local Z); nearest layer for near-field typing. */
    val keyboardLocalZ: Float = -0.78f,
    /** Keyboard downward tilt in degrees (pitch). */
    val keyboardPitchDegrees: Float = 20f,

    // ---- MediaStage dimming while any Workbench surface is present ----
    /** Black scrim alpha over the video (0 = transparent, 1 = opaque). */
    val stageBackdropAlpha: Float = 0.42f,
) {
  /** Center panel anchor Y (local), derived so the bottom edge stays at [centerBottomLocalY]. */
  val centerLocalY: Float
    get() = centerBottomLocalY + centerHeight / 2f

  /** Rail yaw in radians, used by the derived arc geometry. */
  private val railYawRadians: Double
    get() = Math.toRadians(railYawDegrees.toDouble())

  /** Absolute world/local X of each rail center: center half-width + gap + angled inner offset. */
  val railX: Float
    get() =
        (centerWidth / 2.0 + railGap.toDouble() + railWidth / 2.0 * cos(railYawRadians)).toFloat()

  /** Radius of the arc passing through both rail centers and the stage. */
  val railArcRadius: Float
    get() = (railX.toDouble() / sin(railYawRadians)).toFloat()

  /** World Z of the rails (the MediaStage is authored at [STAGE_WORLD_Z]). */
  val railWorldZ: Float
    get() = (STAGE_WORLD_Z.toDouble() - railArcRadius.toDouble() * cos(railYawRadians)).toFloat()

  /**
   * Stage-local Z of the rails when they are parented to the MediaStage: the authored world Z
   * minus the stage's world Z, i.e. the rails sit -arcRadius·cos(yaw) in front of the stage
   * (stage local -Z points toward the user). Y stays 0 in stage-local so rails track the
   * stage's world height automatically when the stage is lifted.
   */
  val railLocalZ: Float
    get() = railWorldZ - STAGE_WORLD_Z

  companion object {
    /** Authored world Z of the MediaStage (spatialized_video_panel). */
    const val STAGE_WORLD_Z: Float = 2.0f

    /** Default layout used by the host; swap for a loaded/preset config in settings. */
    val DEFAULT = WorkbenchLayoutConfig()
  }
}
