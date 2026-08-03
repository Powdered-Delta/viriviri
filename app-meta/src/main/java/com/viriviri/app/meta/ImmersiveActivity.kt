package com.viriviri.app.meta

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.Surface
import androidx.media3.common.Player
import com.viriviri.app.meta.player.PlayerManager
import com.viriviri.core.state.HandoffTarget
import com.meta.spatial.core.Entity
import com.meta.spatial.core.Pose
import com.meta.spatial.core.Quaternion
import com.meta.spatial.core.Query
import com.meta.spatial.core.SpatialFeature
import com.meta.spatial.core.Vector3
import com.meta.spatial.runtime.ReferenceSpace
import com.meta.spatial.toolkit.AppSystemActivity
import com.meta.spatial.toolkit.AvatarSystem
import com.meta.spatial.toolkit.AvatarBody
import com.meta.spatial.toolkit.Controller
import com.meta.spatial.toolkit.DpDisplayOptions
import com.meta.spatial.toolkit.MediaPanelSettings
import com.meta.spatial.toolkit.PixelDisplayOptions
import com.meta.spatial.toolkit.QuadShapeOptions
import com.meta.spatial.toolkit.Transform
import com.meta.spatial.toolkit.UIPanelSettings
import com.meta.spatial.toolkit.VideoSurfacePanelRegistration
import com.meta.spatial.toolkit.ViewPanelRegistration
import com.meta.spatial.toolkit.Visible
import com.meta.spatial.toolkit.createPanelEntity
import com.meta.spatial.vr.VRFeature
import com.meta.spatial.runtime.SessionState

class ImmersiveActivity : AppSystemActivity() {
    private val playerManager by lazy { PlayerManager.getInstance(this) }
    private var incomingTransitionId = NO_TRANSITION
    private var panelSurface: Surface? = null
    private var awaitingFirstFrameSurface: Surface? = null
    private var panelsCreated = false
    private val avatarSystem by lazy { systemManager.findSystem<AvatarSystem>() }
    private val controllerRestoreHandler = Handler(Looper.getMainLooper())
    private var controllerRestoreRunnable: Runnable? = null

    override fun registerFeatures(): List<SpatialFeature> = listOf(VRFeature(this))

    private val playerListener = object : Player.Listener {
        override fun onRenderedFirstFrame() {
            val surface = awaitingFirstFrameSurface ?: return
            if (!playerManager.isCurrentSurface(surface)) return

            awaitingFirstFrameSurface = null
            Log.i(
                TAG,
                "Spatial panel first rendered frame; transition=${incomingTransitionId.takeIf { it >= 0 }} " +
                    "surface=${identity(surface)}",
            )
            HybridTransitionController.onDestinationFirstFrame(
                activity = this@ImmersiveActivity,
                transitionId = incomingTransitionId.takeIf { it >= 0 },
                destination = HandoffTarget.IMMERSIVE,
            )
        }
    }

    override fun registerPanels() = buildList {
        add(
            VideoSurfacePanelRegistration(
                VIDEO_PANEL_ID,
                { _, surface -> onPanelSurfaceAvailable(surface) },
                {
                    MediaPanelSettings(
                        shape = QuadShapeOptions(PANEL_WIDTH_METERS, PANEL_HEIGHT_METERS),
                        display = PixelDisplayOptions(PANEL_WIDTH_PIXELS, PANEL_HEIGHT_PIXELS),
                    )
                },
            ),
        )
        add(
            ViewPanelRegistration(
                CONTROL_PANEL_ID,
                { _, context ->
                    Log.i(TAG, "Spatial control panel view created; registration=$CONTROL_PANEL_ID ownsMedia=false")
                    createImmersiveControlPanel(
                        context = context,
                        onEnterPanel = {
                            Log.i(TAG, "Spatial control panel Enter 2D Panel clicked")
                            HybridTransitionController.returnToPanelInHome(this@ImmersiveActivity)
                        },
                        onModeSelected = { mode ->
                            HybridTransitionController.setExperimentMode(this@ImmersiveActivity, mode)
                        },
                        initialMode = HybridTransitionController.selectedExperimentMode(),
                    )
                },
                {
                    UIPanelSettings(
                        shape = QuadShapeOptions(CONTROL_PANEL_WIDTH_METERS, CONTROL_PANEL_HEIGHT_METERS),
                        display = DpDisplayOptions(
                            CONTROL_PANEL_WIDTH_DP,
                            CONTROL_PANEL_HEIGHT_DP,
                            CONTROL_PANEL_DPI,
                        ),
                    )
                },
            ),
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        incomingTransitionId = intent.transitionId()
        HybridTransitionController.registerAppActivity(this, isVrHost = true)
        playerManager.loadTestMedia()
        HybridTransitionController.registerImmersiveActivity(
            this,
            incomingTransitionId.takeIf { it >= 0 },
        )
        playerManager.player.addListener(playerListener)
    }

    override fun onSceneReady() {
        super.onSceneReady()
        scene.setReferenceSpace(ReferenceSpace.LOCAL_FLOOR)
        scene.setViewOrigin(VIEW_ORIGIN_X, VIEW_ORIGIN_Y, VIEW_ORIGIN_Z, VIEW_ORIGIN_YAW_DEGREES)
        Log.i(
            TAG,
            "Spatial scene ready; referenceSpace=LOCAL_FLOOR " +
                "viewOrigin=($VIEW_ORIGIN_X, $VIEW_ORIGIN_Y, $VIEW_ORIGIN_Z, $VIEW_ORIGIN_YAW_DEGREES) " +
                "panelsCreated=$panelsCreated",
        )
    }

    override fun onVRReady() {
        super.onVRReady()
        scheduleControllerVisibilityRestore("vr_ready")
        if (panelsCreated) return

        val videoPanelPose = Pose(
            Vector3(0f, PANEL_HEIGHT_OFFSET_METERS, PANEL_DISTANCE_METERS),
            Quaternion(ROTATION_W, 0f, ROTATION_Y, 0f),
        )
        val controlPanelPose = Pose(
            Vector3(0f, CONTROL_PANEL_HEIGHT_OFFSET_METERS, PANEL_DISTANCE_METERS),
            Quaternion(ROTATION_W, 0f, ROTATION_Y, 0f),
        )
        try {
            Log.i(
                TAG,
                "Spatial video panel final render mode=StereoMode.None (MediaPanelSettings default full-frame mono) " +
                    "position=${videoPanelPose.t} quaternion=${videoPanelPose.q}",
            )
            val visible = Visible(true)
            val videoPanelEntity = Entity.createPanelEntity(
                VIDEO_PANEL_ID,
                Transform(videoPanelPose),
                visible,
            )
            Log.i(
                TAG,
                "Spatial video panel entity created; registration=$VIDEO_PANEL_ID entity=${videoPanelEntity.id} " +
                    "pose.translation=${videoPanelPose.t} pose.quaternion=${videoPanelPose.q} " +
                    "pose.forward=${videoPanelPose.forward()} pose.up=${videoPanelPose.up()} " +
                    "visible=${visible.isVisible}",
            )
            val controlPanelEntity = Entity.createPanelEntity(
                CONTROL_PANEL_ID,
                Transform(controlPanelPose),
                Visible(true),
            )
            Log.i(
                TAG,
                "Spatial control panel entity created; registration=$CONTROL_PANEL_ID entity=${controlPanelEntity.id} " +
                    "pose.translation=${controlPanelPose.t} pose.quaternion=${controlPanelPose.q} " +
                    "visible=true ownsMedia=false",
            )
            panelsCreated = true
        } catch (error: RuntimeException) {
            Log.e(TAG, "Spatial entity creation failed", error)
        }
    }

    override fun onSessionStateChanged(state: SessionState) {
        super.onSessionStateChanged(state)
        Log.i(TAG, "Spatial session state=$state")
        if (state == SessionState.FOCUSED) {
            // Horizon OS can retain the immersive session while losing controller visuals
            // during a 2D -> immersive return. Reassert the SDK avatar visibility on focus.
            scheduleControllerVisibilityRestore("session_focused")
        }
    }

    private fun scheduleControllerVisibilityRestore(reason: String) {
        controllerRestoreRunnable?.let(controllerRestoreHandler::removeCallbacks)
        val attempts = longArrayOf(0L, 150L, 400L, 800L)
        val runnable = object : Runnable {
            private var attempt = 0

            override fun run() {
                avatarSystem.setShowControllers(true)
                val controllers = localControllers()
                controllers.forEach {
                    it.isActive = true
                    it.laserEnabled = true
                }
                Log.i(
                    TAG,
                    "Spatial controllers visibility restored reason=$reason attempt=${attempt + 1} " +
                        "controllerEntities=${controllers.size}",
                )
                attempt++
                if (attempt < attempts.size) {
                    controllerRestoreHandler.postDelayed(this, attempts[attempt] - attempts[attempt - 1])
                }
            }
        }
        controllerRestoreRunnable = runnable
        controllerRestoreHandler.postDelayed(runnable, attempts[0])
    }

    private fun localControllers(): List<Controller> {
        val avatarBody = Query.where { has(AvatarBody.id) }
            .eval()
            .firstOrNull { entity ->
                entity.isLocal() && entity.getComponent<AvatarBody>().isPlayerControlled
            }
            ?.getComponent<AvatarBody>()
            ?: return emptyList()

        return listOf(avatarBody.leftHand, avatarBody.rightHand)
            .mapNotNull { it.tryGetComponent<Controller>() }
    }

    @Deprecated("Quest system Back routes this immersive Spatial experience to the 2D Activity.")
    override fun onBackPressed() {
        // Keep the system Back route equivalent to the visible control-panel action.
        HybridTransitionController.returnToPanelInHome(this)
    }

    override fun onSpatialShutdown() {
        val startedAtMs = SystemClock.elapsedRealtime()
        val transitionId = HybridTransitionController.transitionIdFor(
            this,
        )
        val protectedHandoff = HybridTransitionController.isPlayerHandoffProtected(this, transitionId)
        val surface = panelSurface
        Log.i(
            TAG,
            "onSpatialShutdown transition=$transitionId protectedHandoff=$protectedHandoff " +
                "surface=${surface?.let(::identity)} validBefore=${surface?.isValid} " +
                "action=detach_before_destination_surface",
        )
        try {
            detachPanelSurface(protectedHandoff, transitionId)
            super.onSpatialShutdown()
        } finally {
            Log.i(
                TAG,
                "onSpatialShutdown transition=$transitionId protectedHandoff=$protectedHandoff " +
                    "surface=${surface?.let(::identity)} validAfter=${surface?.isValid} " +
                    "elapsedMs=${SystemClock.elapsedRealtime() - startedAtMs}",
            )
        }
    }

    override fun onStop() {
        val startedAtMs = SystemClock.elapsedRealtime()
        try {
            playerManager.refreshPlaybackPosition()
            super.onStop()
        } finally {
            Log.i(TAG, "onStop elapsedMs=${SystemClock.elapsedRealtime() - startedAtMs}")
        }
    }

    override fun onDestroy() {
        val startedAtMs = SystemClock.elapsedRealtime()
        try {
            controllerRestoreRunnable?.let(controllerRestoreHandler::removeCallbacks)
            controllerRestoreRunnable = null
            val transitionId = HybridTransitionController.transitionIdFor(
                this,
            )
            detachPanelSurface(
                protectedHandoff = HybridTransitionController.isPlayerHandoffProtected(this, transitionId),
                transitionId = transitionId,
            )
            playerManager.player.removeListener(playerListener)
            HybridTransitionController.onActivityDestroying(this, transitionId)
            super.onDestroy()
            HybridTransitionController.onActivityDestroyed(this)
            releasePlayerIfUnowned()
        } finally {
            Log.i(TAG, "onDestroy elapsedMs=${SystemClock.elapsedRealtime() - startedAtMs}")
        }
    }

    private fun onPanelSurfaceAvailable(surface: Surface) {
        panelSurface = surface
        val transitionId = incomingTransitionId.takeIf { it >= 0 }
        Log.i(
            TAG,
            "Spatial panel surface callback; transition=$transitionId surface=${identity(surface)} " +
                "valid=${surface.isValid}",
        )
        if (!playerManager.attachSurface(surface, HandoffTarget.IMMERSIVE, transitionId)) {
            Log.w(TAG, "Spatial panel player attach failed; transition=$transitionId surface=${identity(surface)}")
            return
        }

        awaitingFirstFrameSurface = surface
        HybridTransitionController.onDestinationSurfaceAttached(
            activity = this,
            transitionId = transitionId,
            destination = HandoffTarget.IMMERSIVE,
        )
        Log.i(TAG, "Spatial panel player attached; transition=$transitionId surface=${identity(surface)}")
    }

    private fun detachPanelSurface(protectedHandoff: Boolean, transitionId: Long?) {
        val startedAtMs = SystemClock.elapsedRealtime()
        try {
            val surface = panelSurface ?: return
            if (protectedHandoff) {
                Log.i(
                    TAG,
                    "skip_app_clear_for_spatial_shutdown transition=$transitionId " +
                        "surface=${identity(surface)} ownership=preserved_for_destination_replacement",
                )
                return
            }
            panelSurface = null
            awaitingFirstFrameSurface = null
            playerManager.detachSurface(
                surface = surface,
                target = HandoffTarget.IMMERSIVE,
                transitionId = transitionId,
            )
            // VideoSurfacePanelRegistration owns this Surface. Do not release it here.
            Log.i(TAG, "Spatial panel surface detached; surface=${identity(surface)}")
        } finally {
            Log.i(TAG, "detachPanelSurface elapsedMs=${SystemClock.elapsedRealtime() - startedAtMs}")
        }
    }

    private fun releasePlayerIfUnowned() {
        val startedAtMs = SystemClock.elapsedRealtime()
        try {
            if (HybridTransitionController.shouldReleasePlayer(this, isChangingConfigurations)) {
                playerManager.release()
            }
        } finally {
            Log.i(TAG, "releasePlayerIfUnowned elapsedMs=${SystemClock.elapsedRealtime() - startedAtMs}")
        }
    }

    private fun Intent.transitionId(): Long =
        getLongExtra(HybridTransitionController.TRANSITION_ID_EXTRA, NO_TRANSITION)

    private companion object {
        const val TAG = "ViriviriHybridPoC"
        const val NO_TRANSITION = -1L
        const val VIDEO_PANEL_ID = 1
        const val CONTROL_PANEL_ID = 2
        const val VIEW_ORIGIN_X = 0f
        const val VIEW_ORIGIN_Y = 0f
        const val VIEW_ORIGIN_Z = 2f
        const val VIEW_ORIGIN_YAW_DEGREES = 180f
        const val PANEL_WIDTH_METERS = 2.4f
        const val PANEL_HEIGHT_METERS = 1.35f
        // This pose is expressed in the official HybridSample LOCAL_FLOOR frame.
        const val PANEL_DISTANCE_METERS = 0f
        const val PANEL_HEIGHT_OFFSET_METERS = 1.5f
        const val CONTROL_PANEL_HEIGHT_OFFSET_METERS = 2.45f
        // Quaternion parameters use the Spatial SDK's local (w, x, y, z) convention.
        const val ROTATION_W = 0f
        const val ROTATION_Y = 1f
        const val PANEL_WIDTH_PIXELS = 1920
        const val PANEL_HEIGHT_PIXELS = 1080
        const val CONTROL_PANEL_WIDTH_METERS = 1.3f
        const val CONTROL_PANEL_HEIGHT_METERS = 0.45f
        const val CONTROL_PANEL_WIDTH_DP = 1024f
        const val CONTROL_PANEL_HEIGHT_DP = 384f
        const val CONTROL_PANEL_DPI = 160
        fun identity(value: Any): String = Integer.toHexString(System.identityHashCode(value))
    }
}
