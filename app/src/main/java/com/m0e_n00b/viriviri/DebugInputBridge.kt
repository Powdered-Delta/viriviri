package com.m0e_n00b.viriviri

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import android.os.SystemClock
import android.util.Log
import android.view.View
import com.meta.spatial.core.Entity
import com.meta.spatial.core.Query
import com.meta.spatial.core.SystemManager
import com.meta.spatial.core.Vector2
import com.meta.spatial.core.Vector3
import com.meta.spatial.runtime.HitInfo
import com.meta.spatial.runtime.PanelSceneObject
import com.meta.spatial.runtime.Scene
import com.meta.spatial.toolkit.Controller
import com.meta.spatial.toolkit.PanelCreationSystem

/**
 * DEBUG-only seed of the in-process input driver described in
 * `docs/research/immersive-input-injection-testing.md`.
 *
 * It exists to answer one question on a real headset: **does a synthetic `onInput` actually reach the
 * panel's Compose/View tree and change app behaviour, with no controller held?**
 *
 * Why this shape:
 * - It drives `SceneObject.onInput(HitInfo, source, changed, buttonState, downTime)` directly, which is
 *   the very seam the SDK's own `PanelClickSystem` uses (verified against the 0.13.2 bytecode:
 *   `SceneObject.onInput` is public, `HitInfo` has an 8-arg public constructor,
 *   `PanelCreationSystem.forEachPanel` is public).
 * - It clicks a **view id**, not a hardcoded (u,v): resolving the target first is what makes "the click
 *   landed on the control I meant" true by construction instead of by eyeballing a screenshot.
 * - It logs the three-stage oracle from §9.2: hit target -> `isPressed` while DOWN is outstanding ->
 *   the app-side effect.
 *
 * Transport: a broadcast, so a host script can drive it without rebuilding the test APK:
 *
 * ```text
 * adb shell am broadcast -a com.m0e_n00b.viriviri.DEBUG_CLICK \
 *     --ei panel <registrationId> --ei view <viewId>
 * ```
 *
 * Never installed in release: [install] returns immediately unless [BuildConfig.DEBUG].
 */
internal class DebugInputBridge(
    private val context: Context,
    private val scene: Scene,
    private val systemManager: SystemManager,
    private val handler: Handler,
) {
  private var receiver: BroadcastReceiver? = null

  fun install() {
    // Build-time opt-in (see app/build.gradle.kts). The manual-verification APK is built without
    // -Pviriviri.inputInjection=true, so this class registers nothing and cannot touch input at all.
    if (!BuildConfig.DEBUG || !BuildConfig.INPUT_INJECTION_ENABLED) {
      if (BuildConfig.DEBUG) {
        Log.i(TAG, "DRIVER not installed (INPUT_INJECTION_ENABLED=false): manual-verification build")
      }
      return
    }
    if (receiver != null) return
    val created =
        object : BroadcastReceiver() {
          override fun onReceive(ctx: Context?, intent: Intent?) {
            val panelId = intent?.getIntExtra(EXTRA_PANEL, 0) ?: 0
            val viewId = intent?.getIntExtra(EXTRA_VIEW, 0) ?: 0
            // Mask is a transport parameter on purpose: PanelInputListener drops every bit that is not in
            // the panel's own clickButtons, so a wrong mask silently produces "no press at all". Being
            // able to sweep it without a rebuild is what turns that into one experiment.
            val mask = intent?.getIntExtra(EXTRA_MASK, CLICK_MASK) ?: CLICK_MASK
            if (panelId == 0) {
              Log.w(TAG, "DRIVER ignored: missing --ei panel")
              return
            }
            // Panel input and entity mutation belong to the scene/UI thread, never the receiver thread.
            handler.post { clickView(panelId, viewId, mask) }
          }
        }
    // API 33+ requires an explicit exported flag for runtime receivers; omitting it throws on
    // Android 14 (this headset reports SDK 34), which would silently cost the whole driver.
    val filter = IntentFilter(ACTION_DEBUG_CLICK)
    if (android.os.Build.VERSION.SDK_INT >= 33) {
      context.registerReceiver(created, filter, Context.RECEIVER_EXPORTED)
    } else {
      context.registerReceiver(created, filter)
    }
    receiver = created
    Log.i(TAG, "DRIVER installed action=$ACTION_DEBUG_CLICK")
  }

  fun uninstall() {
    receiver?.let { runCatching { context.unregisterReceiver(it) } }
    receiver = null
  }

  /**
   * Clicks [viewId] inside the panel registered as [panelId].
   *
   * [viewId] 0 means "dead centre of the panel", which is the fallback for panels whose content is built
   * by Compose and therefore has no `findViewById` target.
   */
  private fun clickView(panelId: Int, viewId: Int, mask: Int) {
    val entity = Entity(panelId)
    val panel = findPanel(entity)
    if (panel == null) {
      // §4.2: panel scene objects are created a few frames later; report rather than cache the miss.
      Log.w(TAG, "DRIVER miss panel=$panelId (scene object not ready)")
      return
    }
    // `rootView` is a Kotlin property on PanelSceneObject (JVM name getRootView), so it must be read as
    // a property here; calling getRootView() from Kotlin does not resolve.
    val root: View? = panel.rootView
    if (root == null || root.width <= 0 || root.height <= 0) {
      Log.w(TAG, "DRIVER miss panel=$panelId (root view not laid out)")
      return
    }

    val target = if (viewId == 0) null else runCatching { root.findViewById<View>(viewId) }.getOrNull()
    val cx: Float
    val cy: Float
    val hitLabel: String
    if (target == null) {
      if (viewId != 0) {
        Log.w(TAG, "DRIVER miss panel=$panelId view=$viewId (id not found in view tree)")
        return
      }
      cx = root.width / 2f
      cy = root.height / 2f
      hitLabel = "centre"
    } else {
      val visible = target.visibility == View.VISIBLE && target.isShown
      cx = (target.left + target.right) / 2f + target.translationX
      cy = (target.top + target.bottom) / 2f + target.translationY
      hitLabel = "view=$viewId visible=$visible enabled=${target.isEnabled}"
      if (!visible) {
        // §9.2 stage 1: a target that is not shown cannot be clicked; say so instead of reporting success.
        Log.w(TAG, "DRIVER hit-rejected panel=$panelId $hitLabel")
        return
      }
    }

    // Same pixel space PanelInputListener maps into: x = u * display.widthInPx.
    val u = (cx / root.width).coerceIn(0f, 1f)
    val v = (cy / root.height).coerceIn(0f, 1f)
    val px = (u * root.width).toInt()
    val py = (v * root.height).toInt()

    // Stage 1: did the point resolve to the control we meant?
    Log.i(
        TAG,
        "DRIVER hit panel=$panelId $hitLabel px=($px,$py) uv=(${fmt(u)},${fmt(v)})",
    )

    val hit =
        HitInfo(
            /* entity = */ entity,
            /* sceneObjectHandle = */ 0L,
            /* nodeId = */ 0,
            /* meshElementId = */ 0,
            /* distance = */ 0f,
            /* point = */ Vector3(0f, 0f, 0f),
            /* normal = */ Vector3(1f, 0f, 0f),
            /* textureCoordinate = */ Vector2(u, v),
        )
    // HOVER OWNERSHIP: PanelInputListener keeps ONE hover owner per panel. Injecting with a foreign
    // synthetic entity steals that owner from the real controller, and the panel then ignores the real
    // ray until something else claims hover back -- on device this looked like "the injection blocked my
    // controller". So drive with the real right-hand controller's own entity whenever one exists;
    // there is then no ownership change at all.
    val source =
        controllerSource()
            ?: run {
              // Deliberately refuse instead of inventing a source: a foreign entity would take hover
              // ownership away from the real controller and leave the panel deaf to it afterwards.
              Log.w(
                  TAG,
                  "DRIVER skipped panel=$panelId: no live right controller to inherit hover from. " +
                      "Wake/hold the right controller, or drive the app through L1 state instead.",
              )
              return
            }

    // Sizes are logged because PanelInputListener maps (u,v) through the DISPLAY size while the target
    // was resolved in ROOT-VIEW pixels. The mapping is proportional so a mismatch is harmless, but an
    // unexpected ratio is the first thing to rule out when a press does not land.
    val display = panel.display
    Log.i(
        TAG,
        "DRIVER sizes panel=$panelId root=${root.width}x${root.height} " +
            "display=${display?.widthInPx}x${display?.heightInPx} mask=$mask",
    )

    // No standalone hover pre-pass: PanelInputListener synthesizes hover-enter on press itself
    // (`synthesizeHoverEnterOnPress`), and a separate hover call with a different source is exactly what
    // takes hover away from the real controller. Measured: DOWN is still consumed without it.

    // Stage 2: DOWN, then read the press state BEFORE releasing — sending both in one frame clears it.
    // The boolean is the discriminator: false means no listener consumed the event at all (panel not
    // attached, or every bit masked out), true means it was dispatched into the view tree.
    val downConsumed = panel.onInput(hit, source, mask, mask, SystemClock.uptimeMillis())
    val pressed = target?.isPressed
    Log.i(
        TAG,
        "DRIVER down panel=$panelId mask=$mask source=${source.id} " +
            "downConsumed=$downConsumed pressed=${pressed ?: "n/a"} before=${stateSnapshot()}",
    )

    handler.postDelayed(
        {
          val upConsumed = panel.onInput(hit, source, mask, 0, SystemClock.uptimeMillis())
          // Stage 3: the effect is the real oracle. Read app state, not pixels.
          Log.i(TAG, "DRIVER up panel=$panelId upConsumed=$upConsumed after=${stateSnapshot()}")
        },
        RELEASE_DELAY_MS,
    )
  }

  /**
   * The entity the real right-hand controller uses as its input source, or null when no controller is
   * live. Returning the real one is what keeps hover ownership where it belongs.
   */
  private fun controllerSource(): Entity? =
      Query.where { has(Controller.id) }
          .eval()
          .filter { it.isLocal() }
          .firstOrNull { candidate ->
            val controller = candidate.tryGetComponent<Controller>() ?: return@firstOrNull false
            controller.isActive && PointerInfoSystem.isRightControllerOrRightHand(candidate)
          }

  private fun findPanel(entity: Entity): PanelSceneObject? {
    val system = systemManager.findSystem<PanelCreationSystem>() ?: return null
    var found: PanelSceneObject? = null
    system.forEachPanel { candidate, future ->
      if (candidate == entity) {
        // CompletableFuture, not a value: null for a few frames after creation.
        found = future.getNow(null)
      }
    }
    return found
  }

  /** App-side snapshot used as the pass/fail oracle, in place of a screenshot. */
  private fun stateSnapshot(): String {
    val app = ViriViriApplication.appState
    val player = app.playerSession.player
    return "playing=${player.isPlaying} pos=${player.currentPosition} " +
        "scale=${fmt(app.state.value.playbackStageScale)} " +
        "curvature=${app.state.value.playbackCurvature} " +
        "destination=${app.state.value.destination}"
  }

  private fun fmt(value: Float): String = String.format("%.4f", value)

  companion object {
    const val ACTION_DEBUG_CLICK: String = "com.m0e_n00b.viriviri.DEBUG_CLICK"
    const val EXTRA_PANEL: String = "panel"
    const val EXTRA_VIEW: String = "view"
    const val EXTRA_MASK: String = "mask"
    private const val TAG = "ViriViriInjection"

    /**
     * `A | TriggerL | TriggerR`. `PanelInputListener` drops any bit not in the panel's own
     * `clickButtons`; the default mask is exactly these, and masking makes the extra bits harmless on
     * panels that accept a subset.
     */
    private const val CLICK_MASK: Int = 1 or 16 or 32

    /** Arbitrary id for the synthetic input source; distinct from the real controller entities. */
    private const val SYNTHETIC_SOURCE_ID: Long = 2000L

    private const val RELEASE_DELAY_MS: Long = 60L
  }
}
