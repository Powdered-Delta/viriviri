package com.viriviri.app.meta

import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import android.view.Surface
import androidx.media3.common.Player
import com.viriviri.app.meta.player.PlayerManager
import com.viriviri.app.meta.player.PlaybackLifecycleOwnership
import com.viriviri.core.state.HandoffTarget
import com.meta.spatial.core.Color4
import com.meta.spatial.core.Entity
import com.meta.spatial.core.Pose
import com.meta.spatial.core.Quaternion
import com.meta.spatial.core.SpatialFeature
import com.meta.spatial.core.Vector3
import com.meta.spatial.runtime.ReferenceSpace
import com.meta.spatial.toolkit.AppSystemActivity
import com.meta.spatial.toolkit.Material
import com.meta.spatial.toolkit.MediaPanelSettings
import com.meta.spatial.toolkit.Mesh
import com.meta.spatial.toolkit.PixelDisplayOptions
import com.meta.spatial.toolkit.QuadShapeOptions
import com.meta.spatial.toolkit.Transform
import com.meta.spatial.toolkit.VideoSurfacePanelRegistration
import com.meta.spatial.toolkit.Visible
import com.meta.spatial.toolkit.createPanelEntity
import com.meta.spatial.vr.VRFeature

class ImmersiveActivity : AppSystemActivity() {
    private val playerManager by lazy { PlayerManager.getInstance(this) }
    private var incomingTransitionId = NO_TRANSITION
    private var panelSurface: Surface? = null
    private var awaitingFirstFrameSurface: Surface? = null
    private var panelsCreated = false

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
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        playerManager.loadTestMedia()
        incomingTransitionId = intent.transitionId()
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
        if (panelsCreated) return

        val videoPanelPose = Pose(
            Vector3(0f, PANEL_HEIGHT_OFFSET_METERS, PANEL_DISTANCE_METERS),
            Quaternion(ROTATION_W, 0f, ROTATION_Y, 0f),
        )
        val coordinateArrowPose = Pose(
            Vector3(
                COORDINATE_ARROW_X_OFFSET_METERS,
                PANEL_HEIGHT_OFFSET_METERS - COORDINATE_ARROW_Y_OFFSET_METERS,
                PANEL_DISTANCE_METERS,
            ),
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
            panelsCreated = true

            val coordinateArrowEntity = Entity.create(
                Mesh(Mesh.AXIS_URI),
                Material().apply {
                    baseColor = Color4(1f, 0.8f, 0.1f, 1f)
                    unlit = true
                },
                Transform(coordinateArrowPose),
                Visible(true),
            )
            Log.i(
                TAG,
                "Spatial coordinate arrow entity created; entity=${coordinateArrowEntity.id} " +
                    "pose.translation=${coordinateArrowPose.t} pose.quaternion=${coordinateArrowPose.q} " +
                    "unlit=true calibrationDiagnostic=true",
            )
        } catch (error: RuntimeException) {
            Log.e(TAG, "Spatial entity creation failed", error)
        }
    }

    @Deprecated("Quest system Back routes this video-only Spatial panel to the 2D Activity.")
    override fun onBackPressed() {
        // Quest's system Back action is the minimal accessible route while this first panel is video-only.
        HybridTransitionController.returnToPanelInHome(this)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        incomingTransitionId = intent.transitionId()
    }

    override fun onSpatialShutdown() {
        val startedAtMs = SystemClock.elapsedRealtime()
        try {
            detachPanelSurface()
            super.onSpatialShutdown()
        } finally {
            Log.i(TAG, "onSpatialShutdown elapsedMs=${SystemClock.elapsedRealtime() - startedAtMs}")
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
            detachPanelSurface()
            playerManager.player.removeListener(playerListener)
            releasePlayerIfUnowned()
            HybridTransitionController.onActivityDestroyed(this)
            super.onDestroy()
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

    private fun detachPanelSurface() {
        val startedAtMs = SystemClock.elapsedRealtime()
        try {
            val surface = panelSurface ?: return
            panelSurface = null
            awaitingFirstFrameSurface = null
            playerManager.detachSurface(
                surface = surface,
                target = HandoffTarget.IMMERSIVE,
                transitionId = incomingTransitionId.takeIf { it >= 0 },
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
            if (
                PlaybackLifecycleOwnership.shouldReleasePlayer(
                    isChangingConfigurations = isChangingConfigurations,
                    isHandoffProtected = HybridTransitionController.isPlayerHandoffProtected(
                        activity = this,
                        incomingTransitionId = incomingTransitionId.takeIf { it >= 0 },
                    ),
                )
            ) {
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
        const val VIEW_ORIGIN_X = 0f
        const val VIEW_ORIGIN_Y = 0f
        const val VIEW_ORIGIN_Z = 2f
        const val VIEW_ORIGIN_YAW_DEGREES = 180f
        const val PANEL_WIDTH_METERS = 2.4f
        const val PANEL_HEIGHT_METERS = 1.35f
        // This pose is expressed in the official HybridSample LOCAL_FLOOR frame.
        const val PANEL_DISTANCE_METERS = 0f
        const val PANEL_HEIGHT_OFFSET_METERS = 1.5f
        const val COORDINATE_ARROW_X_OFFSET_METERS = 1.45f
        const val COORDINATE_ARROW_Y_OFFSET_METERS = 1.0f
        // Quaternion parameters use the Spatial SDK's local (w, x, y, z) convention.
        const val ROTATION_W = 0f
        const val ROTATION_Y = 1f
        const val PANEL_WIDTH_PIXELS = 1920
        const val PANEL_HEIGHT_PIXELS = 1080
        fun identity(value: Any): String = Integer.toHexString(System.identityHashCode(value))
    }
}
