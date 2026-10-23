/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 *
 * This source code is licensed under the MIT license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.m0e_n00b.viriviri

import android.Manifest
import android.app.PendingIntent
import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ObjectAnimator
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import com.m0e_n00b.spatialworkbench.core.PanelSlot
import com.m0e_n00b.spatialworkbench.core.PlaybackCanvas
import com.m0e_n00b.spatialworkbench.core.PlaybackCanvasEvent
import android.util.Log
import android.view.ContextThemeWrapper
import android.view.MotionEvent
import android.view.MenuItem
import android.view.Surface
import android.view.View
import android.widget.Button
import android.widget.PopupMenu
import android.widget.SeekBar
import android.widget.TextView
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.ComposeView
import androidx.core.app.ActivityCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.audio.ChannelMixingAudioProcessor
import androidx.media3.common.audio.ChannelMixingMatrix
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.Renderer
import androidx.media3.exoplayer.SeekParameters
import androidx.media3.exoplayer.audio.AudioRendererEventListener
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.exoplayer.audio.MediaCodecAudioRenderer
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import androidx.media3.exoplayer.metadata.MetadataOutput
import androidx.media3.exoplayer.text.TextOutput
import androidx.media3.exoplayer.video.VideoRendererEventListener
import com.meta.spatial.castinputforward.CastInputForwardFeature
import com.meta.spatial.compose.ComposeFeature
import com.meta.spatial.compose.ComposeViewPanelRegistration
import com.meta.spatial.core.Entity
import com.meta.spatial.core.Pose
import com.meta.spatial.core.Quaternion
import com.meta.spatial.core.SpatialFeature
import com.meta.spatial.core.Vector3
import com.meta.spatial.datamodelinspector.DataModelInspectorFeature
import com.meta.spatial.debugtools.HotReloadFeature
import com.meta.spatial.isdk.IsdkBoxCollider
import com.meta.spatial.isdk.IsdkGrabbable
import com.meta.spatial.isdk.IsdkPanelGrabHandle
import com.meta.spatial.ovrmetrics.OVRMetricsDataModel
import com.meta.spatial.ovrmetrics.OVRMetricsFeature
import com.meta.spatial.runtime.ButtonBits
import com.meta.spatial.runtime.PanelConfigOptions
import com.meta.spatial.runtime.PanelSceneObject
import com.meta.spatial.runtime.PanelShapeLayerBlendType
import com.meta.spatial.runtime.ReferenceSpace
import com.meta.spatial.runtime.SceneAudioAsset
import com.meta.spatial.runtime.SceneMaterial
import com.meta.spatial.runtime.SceneMesh
import com.meta.spatial.runtime.SceneObject
import com.meta.spatial.runtime.SessionState
import com.meta.spatial.runtime.StereoMode
import com.meta.spatial.toolkit.ActivityPanelRegistration
import com.meta.spatial.toolkit.AppSystemActivity
import com.meta.spatial.toolkit.AvatarSystem
import com.meta.spatial.toolkit.CylinderShapeOptions
import com.meta.spatial.toolkit.DpDisplayOptions
import com.meta.spatial.toolkit.GLXFInfo
import com.meta.spatial.toolkit.Hittable
import com.meta.spatial.toolkit.IntentPanelRegistration
import com.meta.spatial.toolkit.LayoutXMLPanelRegistration
import com.meta.spatial.toolkit.Material
import com.meta.spatial.toolkit.MediaPanelRenderOptions
import com.meta.spatial.toolkit.MediaPanelShapeOptions
import com.meta.spatial.toolkit.MediaPanelSettings
import com.meta.spatial.toolkit.Mesh
import com.meta.spatial.toolkit.MeshCollision
import com.meta.spatial.toolkit.Panel
import com.meta.spatial.toolkit.PanelDimensions
import com.meta.spatial.toolkit.PanelInputOptions
import com.meta.spatial.toolkit.PanelRegistration
import com.meta.spatial.toolkit.PanelRenderMode
import com.meta.spatial.toolkit.PanelStyleOptions
import com.meta.spatial.toolkit.PixelDisplayOptions
import com.meta.spatial.toolkit.QuadShapeOptions
import com.meta.spatial.toolkit.Scale
import com.meta.spatial.toolkit.SceneObjectSystem
import com.meta.spatial.toolkit.Transform
import com.meta.spatial.toolkit.TransformParent
import com.meta.spatial.toolkit.UIPanelRenderOptions
import com.meta.spatial.toolkit.UIPanelSettings
import com.meta.spatial.toolkit.UIPanelShapeOptions
import com.meta.spatial.toolkit.createPanelEntity
import com.meta.spatial.toolkit.Visible
import com.meta.spatial.vr.LocomotionSystem
import com.meta.spatial.vr.VRFeature
import java.util.concurrent.CompletableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

fun lerp(start: Float, end: Float, fraction: Float): Float = start + (end - start) * fraction

// default activity
class SpatialVideoSampleActivity : AppSystemActivity() {

  val player: ExoPlayer
    get() = ViriViriApplication.appState.playerSession.player
  lateinit var controllerView: View
  private var transportOverlayState = ImmersiveTransportOverlayState()
  private val canvasHandler = Handler(Looper.getMainLooper())

  /** DEBUG-only synthetic input driver; null in release. See [DebugInputBridge]. */
  private var debugInputBridge: DebugInputBridge? = null
  private val transportTimelineUpdater =
      object : Runnable {
        override fun run() {
          syncTransportTimeline()
          canvasHandler.postDelayed(this, TRANSPORT_TIMELINE_UPDATE_INTERVAL_MS)
        }
      }
  private var transportTimelineUpdatesStarted = false
  private var seekDragPositionMs: Long? = null
  private var spatialVideoPanelSceneObject: PanelSceneObject? = null
  @Volatile private var stageClosed = false
  private var productionVideoGeometry: PlaybackStageLayerGeometry? = null
  private var productionVideoGeneration = 0L
  private var productionVideoRegistered = false
  private val productionFrameQueued = java.util.concurrent.atomic.AtomicBoolean(false)
  private val productionFrame = Runnable {
    productionFrameQueued.set(false)
    if (!stageClosed && videoSurfaceEntity != null) {
      try {
        if (spatialVideoPanelSceneObject == null) replaceProductionVideo(playbackStageController.layout)
        playbackStageController.flush()
      } catch (error: Exception) {
        danmakuOverlayEntity?.setComponent(Visible(false))
        Log.e("ViriViriStage", "production geometry failed; latest layout retained for retry", error)
      }
    }
  }

  private fun requestProductionStageFrame() {
    synchronized(productionFrameQueued) {
      if (!stageClosed && productionFrameQueued.compareAndSet(false, true)) {
        canvasHandler.postDelayed(productionFrame, PRODUCTION_GEOMETRY_INTERVAL_MS)
      }
    }
  }

  /** Release consumes the latest complete request without waiting for the next interval. */
  private fun flushProductionStageFrame() {
    synchronized(productionFrameQueued) {
      if (stageClosed) return
      canvasHandler.removeCallbacks(productionFrame)
      productionFrameQueued.set(true)
      canvasHandler.post(productionFrame)
    }
  }
  /** Set once the restored (persisted) curvatures have been logged, so the log is readable. */
  private var loggedRestoredCurvature = false
  /**
   * Authored local Z of each stage overlay inside the stage root, before the curvature offset. Kept
   * as constants because the offset is recomputed from the base on every reshape — reading the
   * entity's current Z instead would accumulate.
   */
  private val stageBackdropBaseLocalZ = PlaybackStageLayer.BACKDROP.baseLocalZ

  /**
   * Authored local Z of the danmaku layer, cycled at runtime by the debug rail.
   *
   * The authored value is -0.01 (the danmaku is meant to sit in front of the video), but on device the
   * layer reads as buried BEHIND the video at that separation, and the 1 cm gap is far too small to
   * argue with a compositor layer's draw order. Rather than guess the sign and magnitude again, the
   * debug button steps this through candidates so the value that actually renders on top can be read
   * off the headset.
   */
  private val stageDanmakuBaseLocalZ: Float
    get() = DANMAKU_BASE_LOCAL_Z_CANDIDATES[danmakuBaseLocalZIndex]

  private var danmakuBaseLocalZIndex = 0

  /** Authored local Z of the video surface inside the stage root, before the curvature offset. */
  private val videoSurfaceBaseLocalZ = PlaybackStageLayer.VIDEO.baseLocalZ

  /**
   * Entity that carries the curved video surface.
   *
   * The media panel used to be bound straight to `R.id.spatialized_video_panel`, but that entity is
   * the stage ROOT: it parents both rails, transport, navigation, centre content, keyboard and both
   * overlays. A curved layer has to move by one radius (see
   * [PlaybackStageCurvature.surfaceAnchorOffsetMeters]), so binding the surface to the root would
   * drag every one of those with it. The surface therefore gets its own child entity and only that
   * entity moves; the root never does.
   */
  private var videoSurfaceEntity: Entity? = null
  private var spatialVideoAspectProbeState = SpatialVideoAspectProbeState()
  private var wristDebugPanelEntity: Entity? = null
  private var danmakuOverlayEntity: Entity? = null
  private var stageBackdropEntity: Entity? = null
  private var centerContentEntity: Entity? = null
  private var inputMethodPanelEntity: Entity? = null
  private var grabBarEntity: Entity? = null
  /** Grouping anchor: stage + all workbench panels hang off it (Scheme A root). */
  private var workbenchRootEntity: Entity? = null
  /** Current video content half-height (metres), used to park the grab bar under the stage. */
  private var grabBarContentHalfHeight: Float = MR_SCREEN_HEIGHT / 2f
  /**
   * The scene-authored `WorkbenchOuterDismiss` node.
   *
   * Deliberately taken OUT of hit testing and hidden at runtime — see [hideOuterDismissHitLayer] for the
   * reasoning. Dismissal from that region is owned by the stage tap's `VOID_DISMISS` outcome, so there is
   * no suppression window and no input listener left on this entity.
   */
  private var outerDismissEntity: Entity? = null
  /**
   * DEBUG-only panel-occlusion guard for the stage trigger (task 10-07-stage-tap-cannot-close-workbench).
   *
   * On means "do not toggle the workbench when an interactive panel is what the ray actually hits", which
   * is the conservative default: the geometric stage gate cannot see panels in front of the stage plane,
   * and those panels consume the same trigger. It is switchable because the verdict cannot be reached
   * statically and a single device session should be able to settle it.
   */
  private var stagePrimaryActionOcclusionGuardEnabled = true
  /**
   * DEBUG-only backdrop-order candidate index (task 10-07-workbench-backdrop-layer-order).
   *
   * 0 = BASELINE is today's behaviour and the only candidate a release build can reach, so the default
   * path carries no unverified visual change.
   */
  private var backdropOrderCandidate = BACKDROP_ORDER_ZINDEX
  private var backdropOrderStatus: TextView? = null
  private var stageTapGuardButton: Button? = null
  private var hasWorkbenchDataSource = false
  private val stagePanelRenderer by lazy {
    PlaybackStagePanelRenderer(
        config = { layout, layer ->
          if (layer.role == PlaybackStageLayer.VIDEO) videoPanelConfig(layout.geometry)
          else stageOverlayPanelSettings(
                  layer.content.widthMeters, layer.content.heightMeters, layer.curvature,
                  isBackdrop = layer.role == PlaybackStageLayer.BACKDROP,
              ).toPanelConfigOptions().apply {
                if (layer.role == PlaybackStageLayer.DANMAKU) configureDanmakuLayer(this)
                else applyBackdropOrderCandidate(this, BackdropOrderLayer.BACKDROP)
              }
        },
        prepareStage = { updateGrabBarPose(it.viewport.halfHeight) },
        stageWorldPose = { worldPoseOf(Entity(R.id.spatialized_video_panel)) },
        replaceVideo = ::replaceProductionVideo,
    )
  }
  private val playbackStageController by lazy {
    PlaybackStageController<PanelSceneObject>(
        MR_SCREEN_WIDTH, MR_SCREEN_HEIGHT,
        resolve = ::resolveStagePanel,
        commit = { layout, panels -> stagePanelRenderer.apply(layout, panels) },
    )
  }
  private lateinit var spatialPanelVisibilityController: SpatialPanelVisibilityController
  private lateinit var immersivePlaybackCanvasHost: ImmersivePlaybackCanvasHost
  lateinit var audio: SceneAudioAsset
  var seekBar: CompletableFuture<SeekBar> = CompletableFuture<SeekBar>()
  var elapsedTime: CompletableFuture<TextView> = CompletableFuture<TextView>()
  var durationTime: CompletableFuture<TextView> = CompletableFuture<TextView>()
  var qualityButton: CompletableFuture<Button> = CompletableFuture<Button>()
  var volumeButton: CompletableFuture<Button> = CompletableFuture<Button>()
  var speedButton: CompletableFuture<Button> = CompletableFuture<Button>()
  var playPauseButton: CompletableFuture<Button> = CompletableFuture<Button>()
  val panner: ChannelMixingAudioProcessor = ChannelMixingAudioProcessor()
  var isPlaying: Boolean = false
  var isSeeking: Boolean = false
  private val seekDragPlaybackPolicy = SeekDragPlaybackPolicy()
  var isFirstReadyDone: Boolean = false
  lateinit var locomotionSystem: LocomotionSystem
  lateinit var avatarSystem: AvatarSystem
  var setUri: Uri? = null
  var targetLights: Float = 1.0f
  val PERMISSIONS_REQUEST_CODE = 100
  var alphaAnimator: ObjectAnimator? = null
  var mrPanelPose: Pose = Pose()
  var skydome: Entity? = null
  var skydomeMat: SceneMaterial? = null
  var environmentGLXF: Entity? = null
  var inMrMode: Boolean = false
    private set

  private var gltfxEntity: Entity? = null
  private val activityScope = CoroutineScope(Dispatchers.Main)
  private var immersiveBrowseSession = ImmersiveBrowseSession()
  private var previousBrowseDestination: ViriViriDestination? = null
  // Centralized Workbench spatial tuning; replace/bind for future settings presets.
  private val layout = WorkbenchLayoutConfig.DEFAULT
  private var browseSelectionObserver: Job? = null
  private var browseCommandObserver: Job? = null
  private var shouldReattachImmersiveOutput = false
  private lateinit var immersiveMediaStageHost: ImmersiveMediaStageHost<Surface>
  private lateinit var immersiveWorkbenchHost: ImmersiveWorkbenchHost
  private val immersiveStagePlayerListener =
      object : Player.Listener {
        override fun onPlayerStateChanged(playWhenReady: Boolean, playbackState: Int) {
          reportImmersiveStageClock()
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
          reportImmersiveStageClock()
        }

        override fun onPositionDiscontinuity(reason: Int) {
          reportImmersiveStageClock()
          if (::immersiveMediaStageHost.isInitialized) {
            immersiveMediaStageHost.reportSeek(player.currentPosition)
          }
        }
      }
  private val immersiveControlsPlayerListener =
      object : Player.Listener {
        override fun onPlayerStateChanged(playWhenReady: Boolean, playbackState: Int) {
          syncTransportTimeline()
          syncPlaybackControls()
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
          syncPlaybackControls()
          dispatchPlaybackCanvas(PlaybackCanvasEvent.PlaybackStateChanged(isPlaying))
        }

        override fun onPositionDiscontinuity(reason: Int) {
          syncTransportTimeline()
          syncPlaybackControls()
        }

        override fun onPlaybackParametersChanged(playbackParameters: androidx.media3.common.PlaybackParameters) {
          syncPlaybackSpeedLabel()
        }

        override fun onVolumeChanged(volume: Float) {
          syncPlaybackVolumeLabel()
        }

        override fun onVideoSizeChanged(videoSize: androidx.media3.common.VideoSize) {
          updateSpatialVideoContentQuad(
              videoWidth = videoSize.width,
              videoHeight = videoSize.height,
              pixelWidthHeightRatio = videoSize.pixelWidthHeightRatio,
          )
        }

        override fun onPlayerError(error: PlaybackException) {
          // Recover the existing player/media path only; do not create another player or target.
          setUri?.let(::setVideo)
          Log.e("ExoPlayer", "Player encountered an error: $error")
        }
      }

  override fun registerFeatures(): List<SpatialFeature> {
    val features = mutableListOf<SpatialFeature>(VRFeature(this), ComposeFeature())
    if (BuildConfig.DEBUG) {
      features.add(CastInputForwardFeature(this))
      features.add(HotReloadFeature(this))
      features.add(OVRMetricsFeature(this, OVRMetricsDataModel() { numberOfMeshes() }))
      features.add(DataModelInspectorFeature(spatial, this.componentManager))
    }
    return features
  }

  override fun onSessionStateChanged(state: SessionState) {
    super.onSessionStateChanged(state)
    Log.i("ViriViriSpatial", "sessionState=$state")
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    shouldReattachImmersiveOutput =
        intent.getBooleanExtra(EXTRA_REATTACH_IMMERSIVE_OUTPUT, false)

    requestPermissions()

    appPackageName = getPackageName()
    appContext = spatialContext

    panner.putChannelMixingMatrix(ChannelMixingMatrix.create(2, 2))

    immersiveMediaStageHost =
        ImmersiveMediaStageHost(
            attachVideoOutput = ViriViriApplication.appState.playerSession::attachImmersiveSurface,
            onEffect = { effect -> Log.d(TAG, "MediaStage effect=$effect") },
            reattachVideoOutput = ViriViriApplication.appState.playerSession::reattachImmersiveSurface,
        )
    spatialPanelVisibilityController =
        SpatialPanelVisibilityController(canvasHandler) { Log.d(WORKBENCH_TRACE_TAG, it) }
    immersiveWorkbenchHost =
        ImmersiveWorkbenchHost(::applyWorkbenchModules) { Log.d(WORKBENCH_TRACE_TAG, it) }
    immersivePlaybackCanvasHost =
        ImmersivePlaybackCanvasHost(applyVisibleSlots = ::applyPlaybackCanvasSlots)
    player.addListener(immersiveStagePlayerListener)
    player.addListener(immersiveControlsPlayerListener)
    browseSelectionObserver =
        activityScope.launch {
          ViriViriApplication.appState.state.collect { appState ->
            val nextHasDataSource = appState.selected != null
            if (nextHasDataSource != hasWorkbenchDataSource) {
              hasWorkbenchDataSource = nextHasDataSource
              if (::immersiveWorkbenchHost.isInitialized) {
                applyWorkbenchModules(ImmersiveWorkbenchReducer.modules(immersiveWorkbenchHost.state))
              }
            }
            syncPlaybackQualityLabel(appState.playbackQuality)
            applyPlaybackStageParameters(
                appState.playbackDisplayRatio, appState.playbackStageScale, appState.playbackCurvature
            )
            if (!loggedRestoredCurvature) {
              loggedRestoredCurvature = true
              // Surfaces stale preferences: a layer reading "r=6.00 m" for the 6 m flatmost radius can
              // only have come from a persisted value, never from the thumbstick.
              Log.i("ViriViriCurve", "restored curvature ${curvatureLabel(appState.playbackCurvature)}")
            }
            syncInputMethodPanelVisibility(appState)
            val previousDestination = previousBrowseDestination
            val transition =
                ImmersiveBrowseSessionReducer.onAppState(
                    session = immersiveBrowseSession,
                    canvas = immersivePlaybackCanvasHost.state.canvas,
                    destination = appState.destination,
                    previousDestination = previousDestination ?: appState.destination,
                )
            previousBrowseDestination = appState.destination
            immersiveBrowseSession = transition.session
            if (transition.returnToPlayback) dispatchPlaybackCanvas(PlaybackCanvasEvent.OpenPlayback)
          }
        }
    browseCommandObserver =
        activityScope.launch {
          ViriViriApplication.appState.immersiveBrowseCommands.collect { command ->
            if (command == ImmersiveBrowseCommand.RETURN_TO_PLAYBACK) {
              val transition = ImmersiveBrowseSessionReducer.cancel(immersiveBrowseSession)
              immersiveBrowseSession = transition.session
              if (transition.returnToPlayback) dispatchPlaybackCanvas(PlaybackCanvasEvent.OpenPlayback)
            }
          }
        }

    audio = SceneAudioAsset.loadLocalFile("data/common/audio/ui_press_direct.ogg")

    // since this is MR mode, we want to disable the controllers rendering
    // We will enable the controllers when not in MR, but will not enable avatar
    avatarSystem = systemManager.findSystem<AvatarSystem>()
    avatarSystem.setShowControllers(false)
    avatarSystem.setShowHands(false)

    // Locomotion stays off for the lifetime of the scene. This is a viewing app, and the right
    // thumbstick is reserved for the stage tuning control (see AnalogMediaStageTuningSystem).
    // VRFeature registers LocomotionSystem enabled on the right controller by default, so this
    // explicit disable is load-bearing rather than redundant; it also stops locomotion from
    // stealing thumbstick input while scrolling panels.
    locomotionSystem = systemManager.findSystem<LocomotionSystem>()
    locomotionSystem.enableLocomotion(false)

    loadGLXF { composition ->
      environmentGLXF = composition.getNodeByName("MediaRoom").entity
      // UX: the one scene-authored center panel hosts Search/List only and never owns video output.
      centerContentEntity = composition.getNodeByName("WorkbenchCenterContent").entity
      outerDismissEntity = composition.getNodeByName("WorkbenchOuterDismiss").entity
      environmentGLXF?.let {
        val environmentMesh = it.getComponent<Mesh>()
        it.setComponent(
            environmentMesh.apply { defaultShaderOverride = SceneMaterial.UNLIT_SHADER }
        )
      }
      bindCenterContentPanel()
      canvasHandler.post { hideOuterDismissHitLayer() }
      // Apply the layer ORDERING once the panels exist: scene geometry < video < backdrop < UI panels.
      // The UI panels' zIndex is written into their PanelConfigOptions, which only a reshape produces,
      // so without this the panels keep the LayerConfig default 0 and the video layer draws over them
      // (observed on device 2026-10-20: the video covered the centre list). Posted after a frame so the
      // panel scene objects have landed; the reshape is the same idempotent path the debug switch uses.
      canvasHandler.postDelayed({ applyBackdropOrderCandidateLive() }, LAYER_ORDER_APPLY_DELAY_MS)
      if (::immersiveWorkbenchHost.isInitialized) {
        applyWorkbenchModules(ImmersiveWorkbenchReducer.modules(immersiveWorkbenchHost.state))
      }
      setMrMode(scene.isSystemPassthroughEnabled())
    }
  }

  override fun registerPanels(): List<PanelRegistration> {
    return mutableListOf(
        controlsPanelRegistration(),
        selectorPanelRegistration(),
        mrPanelRegistration(),
        modePanelRegistration(),
        centerContentPanelRegistration(),
        inputMethodPanelRegistration(),
        stageBackdropPanelRegistration(),
        danmakuOverlayPanelRegistration(),
        grabBarPanelRegistration(),
    )
        .apply {
          if (BuildConfig.DEBUG) add(wristDebugPanelRegistration())
        }
  }

  private fun requestPermissions() {
    val permissionsNeeded =
        arrayOf("com.oculus.permission.USE_SCENE", Manifest.permission.READ_EXTERNAL_STORAGE)

    ActivityCompat.requestPermissions(this, permissionsNeeded, PERMISSIONS_REQUEST_CODE)
  }

  override fun onRequestPermissionsResult(
      requestCode: Int,
      permissions: Array<out String>,
      grantResults: IntArray,
  ) {
    when (requestCode) {
      PERMISSIONS_REQUEST_CODE -> {
        val granted = grantResults.all { it == PackageManager.PERMISSION_GRANTED }
        if (granted) {
          // All permissions have been granted
          Log.i(TAG, "All permissions have been granted")
        } else {
          // One or more permissions have been denied
          Log.i(TAG, "One or more permissions were DENIED!")
        }
      }
    }
  }

  override fun onSceneReady() {
    super.onSceneReady()

    // set the reference space to enable recentering
    scene.setReferenceSpace(ReferenceSpace.LOCAL_FLOOR)

    // DEBUG-only in-process input driver (docs/research/immersive-input-injection-testing.md).
    // Installed here rather than in onCreate so the scene/system manager are live; it no-ops in release.
    debugInputBridge = DebugInputBridge(this, scene, systemManager, canvasHandler).also { it.install() }

    systemManager.registerSystem(SpatialAudioSystem(panner, this))
    componentManager.registerComponent<SpatializedAudioPanel>(SpatializedAudioPanel.Companion)
    componentManager.registerComponent<PanelLayerAlpha>(PanelLayerAlpha.Companion)
    componentManager.registerComponent<WristAttached>(WristAttached.Companion)
    systemManager.registerSystem(PanelLayerAlphaSystem(systemManager.findSystem()))
    systemManager.registerSystem(WristAttachedSystem())
    val pointerInfoSystem = PointerInfoSystem()
    systemManager.registerSystem(pointerInfoSystem)
    systemManager.registerSystem(
        GrabBarHoverSystem(
            pointerInfo = pointerInfoSystem,
            barEntity = Entity(R.id.grab_bar_panel),
            barAnchorProvider = { workbenchRootEntity },
            playingOnlyProvider = {
              // Playing-only = a video is selected while the Workbench panels are collapsed
              // (only the MediaStage remains). That is the state where the bar fades out.
              val workbenchVisible =
                  ::immersiveWorkbenchHost.isInitialized && immersiveWorkbenchHost.state.visible
              ViriViriApplication.appState.state.value.selected != null && !workbenchVisible
            },
        )
    )
    systemManager.registerSystem(
        AnalogMediaStageTuningSystem(
            pointerInfo = pointerInfoSystem,
            // Resolved per frame: the frame depends on the live stage pose and scaled footprint.
            stageFrame = ::currentStageFrame,
            // ANALOG half: the thumbstick keeps its playing-only gate — a live stick under the panels
            // made them unusable.
            stageInputEnabled = ::isStageInputEnabled,
            // TRIGGER half: a separate gate that deliberately stays OPEN while the panels are up,
            // because the stage is now the workbench's toggle (see AnalogMediaStageTuningSystem).
            stagePrimaryActionEnabled = ::isStagePrimaryActionEnabled,
            // DEBUG switch for the panel-occlusion guard; see the class KDoc on the tuning system.
            stagePrimaryActionOcclusionGuardEnabled = { stagePrimaryActionOcclusionGuardEnabled },
            // Only used if the stage pose cannot be read; keeps the stage controllable either way.
            fallbackStageEntity = { videoSurfaceEntity.takeIf { playbackStageController.appliedLayout != null } },
            onScaleDelta = ViriViriApplication.appState::adjustPlaybackStageScale,
            onCurvatureDelta = ViriViriApplication.appState::adjustPlaybackCurvature,
            onStageAction = ::onStagePrimaryAction,
            // The void outcome only applies while the workbench is on screen; the host owns that fact.
            workbenchVisible = {
              ::immersiveWorkbenchHost.isInitialized && immersiveWorkbenchHost.state.visible
            },
            onStageVoidTap = ::onStageVoidTap,
            onInteractionFinished = ::flushProductionStageFrame,
        )
    )
    // Escape hatch: the stage is the only other way into the workbench, and its hit geometry moves with
    // the curvature offset, so a bad curvature would otherwise lock the user out of the reset.
    if (BuildConfig.DEBUG) {
      systemManager.registerSystem(
          AnalogWorkbenchSummonSystem(onSummon = ::summonWorkbenchFromController)
      )
    }
    // One frame owner commits the latest layout only after the full panel group is ready.
    systemManager.registerSystem(PlaybackStageGeometrySystem {
      requestProductionStageFrame()
    })

    // Scheme A: the ANCHOR is the grabbable entity; the stage hangs under it, so the SDK moves
    // everything when the anchor is dragged — no mirroring system needed. Watch the anchor's
    // grab state to persist the stage's world height on release.
    systemManager.registerSystem(
        VideoStageGrabPersistenceSystem(
            anchorProvider = { workbenchRootEntity },
            onGrabFinished = { anchorY ->
              // Stage world centre Y = anchor world Y + local stage offset.
              val stageY = anchorY + currentStageAnchorOffsetY()
              if (stageY.isFinite() && stageY > 0f) {
                ViriViriApplication.appState.persistWorkbenchStageY(stageY)
              }
            },
        )
    )

    scene.isSystemPassthroughEnabled().let { isMrMode ->
      scene.enablePassthrough(isMrMode)
      // Locomotion is intentionally never re-enabled here, not even in VR mode: the right
      // thumbstick belongs to the stage tuning control.
      avatarSystem.setShowControllers(!isMrMode)
      avatarSystem.setShowHands(!isMrMode)
      inMrMode = isMrMode
    }

    scene.setViewOrigin(0f, 0.0f, 0.0f, 0.0f)
    Log.i("ViriViriSpatial", "referenceSpace=LOCAL_FLOOR viewOrigin=0,0,0,0")

    skydome =
        Entity.create(
            listOf(
                Mesh(Uri.parse("mesh://skybox"), hittable = MeshCollision.NoCollision),
                Material().apply {
                  baseTextureAndroidResourceId = R.drawable.skydome
                  unlit = true
                },
                Transform(Pose(Vector3(x = 0f, y = 0f, z = 0f))),
                Visible(false),
            )
        )

    scene.updateIBLEnvironment("chromatic.env")
    if (BuildConfig.DEBUG) createWristDebugPanel()
  }

  private fun bindCenterContentPanel() {
    // UX: the center content follows the movable MediaStage root but remains a non-grabbable UI layer.
    centerContentEntity?.setComponent(TransformParent(Entity(R.id.spatialized_video_panel)))
    // TransformParent preserves world pose on reparent, so the authored scene translation
    // is recomputed into local space. Set the local pose explicitly: the panel is taller
    // and its anchor is shifted down by half the added height so the TOP edge stays fixed
    // while the panel extends downward.
    centerContentEntity?.setComponent(
        Transform(Pose(Vector3(0f, layout.centerLocalY, layout.centerLocalZ)))
    )
    // The exported scene hardcodes PanelDimensions at the old size; the GLXF is
    // regenerated by app:export and would otherwise keep the 0.84m height even when
    // QuadShapeOptions/display are taller, squashing content vertically. Set the real
    // physical size at runtime so content pixels are not compressed.
    centerContentEntity?.setComponent(
        PanelDimensions(com.meta.spatial.core.Vector2(layout.centerWidth, layout.centerHeight))
    )
  }

  /**
   * Takes the scene-authored `WorkbenchOuterDismiss` slab OUT of hit testing.
   *
   * Its history is worth keeping, because this is the third position the slab has held. k82 had to make it
   * engine-visible with an alpha-0 `Blend` material, because `Visible(false)` removes an entity from hit
   * testing and the slab was then the ONLY "click outside the workbench" target. That is no longer what we
   * want: a hittable slab in empty space makes the SDK draw a target-dot indicator over what should read
   * as nothing, which is exactly the counter-intuitive cue reported on device 2026-10-07.
   *
   * So the slab is now both hidden and non-hittable:
   * - no `Hittable` component is added. The exported `Composition.glxf` declares no components for this
   *   node either, so its hittability came only from Kotlin and there is nothing to fight at scene level;
   * - `Visible(false)` is now safe AND preferable. It is the same mechanism that removed the slab from
   *   hit testing in the first place, and it closes k82's still-open risk that the alpha-0 `Blend`
   *   material writes depth and occludes the environment geometry behind the workbench. The material is
   *   deliberately left as it is.
   *
   * Dismissal from that region is owned by the stage tap's `VOID_DISMISS` outcome, which needs the ray to
   * reach NOTHING there. That makes it depend on nothing hittable sitting behind the slab, which is not
   * knowable statically — the `ViriViriStageTap` diagnostic line reports whatever absorbs the ray.
   */
  private fun hideOuterDismissHitLayer() {
    val entity = outerDismissEntity ?: return
    entity.setComponent(Visible(false))
    Log.i(WORKBENCH_TRACE_TAG, "outerDismiss layer hidden and non-hittable")
  }

  /**
   * Scheme A carrier split (2026-09-09): the grabbable ANCHOR is a non-Panel entity carrying a
   * whole-surface [IsdkBoxCollider] + [IsdkGrabbable] — unlike IsdkPanelGrabHandle, a box
   * collider has no "content area", so the ENTIRE bar strip is grabbable (the panel handle only
   * ever produced edge/corner colliders → the "outer ring only" bug).
   *
   * The visible bar (icon + label + fade) stays a Panel, parented to the anchor as a pure
   * visual child. The two raycasts are independent (SceneObject mesh ray → hover,
   * ISDK native collider ray → grab), so both coexist on the same spot.
   */
  private fun createWorkbenchAnchor(initialPose: Pose, persistedStageY: Float) {
    val anchorWorldY = persistedStageY - currentBarOffsetY()
    // 1) Grab layer: non-Panel anchor with a full-strip box collider.
    val anchor =
        Entity.create(
            listOf(
                Transform(initialPose * Pose(Vector3(0f, anchorWorldY, 2f))),
                IsdkBoxCollider(
                    size =
                        Vector3(
                            GRAB_BAR_WIDTH_METERS,
                            GRAB_BAR_HEIGHT_METERS,
                            GRAB_BAR_HIT_DEPTH_METERS,
                        ),
                    offset = Vector3(0f, 0f, GRAB_BAR_HIT_FORWARD_OFFSET),
                ),
                IsdkGrabbable(),
            )
        )
    workbenchRootEntity = anchor
    Log.i(WORKBENCH_TRACE_TAG, "workbenchAnchor created worldY=$anchorWorldY")

    // 2) Visual layer: spawn the bar panel (icon/label/fade) and parent it to the anchor.
    val visual = Entity(R.id.grab_bar_panel)
    grabBarEntity = visual
    visual.setComponents(
        listOf(
            Panel(R.id.grab_bar_panel),
            Transform(Pose(Vector3(0f, 0f, 0f), Quaternion(0f, 0f, 0f))),
            TransformParent(anchor),
        )
    )
    visual.setComponent(Visible(true))
    // Start at the idle fade level (fade in on hover is driven by GrabBarHoverSystem).
    visual.setComponent(PanelLayerAlpha(GRAB_BAR_IDLE_ALPHA))
    Log.i(
        WORKBENCH_TRACE_TAG,
        "grabBarVisual parented hasPanel=${visual.tryGetComponent<Panel>() != null}",
    )
  }

  /** Distance from the stage centre down to the bar centre (bar under the video bottom + gap). */
  private fun currentBarOffsetY(): Float {
    val gapPlusHalf = GRAB_BAR_HEIGHT_METERS / 2f + GRAB_BAR_BOTTOM_GAP_METERS
    // [grabBarContentHalfHeight] already carries the stage scale because every content quad is
    // computed from the scaled stage footprint (see [scaledStageWidth] / [scaledStageHeight]).
    return grabBarContentHalfHeight + gapPlusHalf
  }

  /** Stage-local +Y offset above the bar anchor so the video centre sits at the authored height. */
  private fun currentStageAnchorOffsetY(): Float = currentBarOffsetY()

  /**
   * Stage footprint in metres with the shared stage scale already BAKED IN.
   *
   * The controller supplies one layout for all panels. Size is baked into shapes;
   * the renderer keeps native scale at one and commits surface/axis carrier poses together.
   */
  private fun scaledStageWidth(): Float = currentPlaybackStageGeometry().viewport.widthMeters

  private fun scaledStageHeight(): Float = currentPlaybackStageGeometry().viewport.heightMeters

  /** SDK shape uses final physical dimensions; the default native mask belongs to that same panel. */
  private fun videoShapeOptions(
      width: Float,
      height: Float,
      curvature: PlaybackStageCurvature,
  ): MediaPanelShapeOptions =
      when (curvature) {
        is PlaybackStageCurvature.Cylinder ->
            CylinderShapeOptions(curvature.radiusMeters, width, height)
        PlaybackStageCurvature.Flat -> QuadShapeOptions(width, height)
      }

  /**
   * Writes the compositor ordering after render options have produced the LayerConfig.
   * Higher zIndex values sort other compositor layers in front of video. This does not
   * alter scene-mesh depth, panel curvature, or the carrier radius correction.
   */
  private fun applyVideoLayerDepth(config: com.meta.spatial.runtime.PanelConfigOptions) {
    config.layerConfig?.zIndex = VIDEO_LAYER_Z_INDEX
    // Video is opaque over its entire aspect-fitted rectangle, even with no danmaku present.
    // The default SDK builder turns this into the corresponding full-rectangle aperture.
    config.layerBlendType = PanelShapeLayerBlendType.OPAQUE
  }

  // ---------------------------------------------------------------------------------------------
  // DEBUG-only backdrop-order candidates (task 10-07-workbench-backdrop-layer-order)
  // ---------------------------------------------------------------------------------------------

  /** Which of the three relative layer roles a candidate's zIndex is written for. */
  private enum class BackdropOrderLayer {
    VIDEO,
    BACKDROP,
    UI_PANEL,
  }

  /** One UI panel that takes part in the backdrop-order candidates. */
  private class BackdropOrderPanelTarget(
      val name: String,
      val entity: () -> Entity?,
      val settings: () -> UIPanelSettings,
  )

  /**
   * Render options for a `UIPanelSettings`, per the active backdrop-order candidate.
   *
   * UI panels are compositor layers, with SDK-generated apertures in the main scene buffer.
   * Use ALPHA_BLEND for transparent roots, glyph edges and the translucent backdrop; the SDK
   * default MASKED mode treats texture alpha as a binary aperture and writes depth at glyphs.
   * Layer zIndex orders the panel textures, while these apertures integrate them with the scene.
   * Transform parenting does not flatten the panels into a single compositor group.
   */
  private fun uiPanelRenderOptions(isBackdrop: Boolean = false): UIPanelRenderOptions =
      if (BuildConfig.DEBUG && backdropOrderUsesMesh(isBackdrop)) {
        // PanelRenderMode.Mesh makes `applyTo` call `setLayerConfig(null)`: ordinary scene geometry that
        // takes part in depth testing instead of a compositor layer.
        UIPanelRenderOptions(PanelRenderMode.Mesh())
      } else {
        UIPanelRenderOptions(
            PanelRenderMode.Layer(layerBlendType = PanelShapeLayerBlendType.ALPHA_BLEND)
        )
      }

  private fun backdropOrderUsesMesh(isBackdrop: Boolean): Boolean =
      when (backdropOrderCandidate) {
        BACKDROP_ORDER_BACKDROP_MESH -> isBackdrop
        BACKDROP_ORDER_ALL_UI_MESH -> true
        else -> false
      }

  /**
   * Writes the candidate's `LayerConfig.zIndex`. Runs AFTER `toPanelConfigOptions()`, which is what the
   * SDK's own `rendering.applyTo` populated, so the mutation wins — the same ordering
   * [applyVideoLayerDepth] relies on.
   *
   * No-op outside candidate 1. `LayerConfig()` defaults `zIndex` to 0, so the ordering used here is
   * video (1) < backdrop (2) < other UI panels (3): every role is written explicitly rather than relying
   * on a default that coincides with another role's value.
   */
  private fun applyBackdropOrderCandidate(config: PanelConfigOptions, layer: BackdropOrderLayer) {
    if (backdropOrderCandidate != BACKDROP_ORDER_ZINDEX) return
    config.layerConfig?.zIndex =
        when (layer) {
          BackdropOrderLayer.VIDEO -> VIDEO_LAYER_Z_INDEX
          BackdropOrderLayer.BACKDROP -> BACKDROP_LAYER_Z_INDEX
          BackdropOrderLayer.UI_PANEL -> UI_PANEL_LAYER_Z_INDEX
        }
  }

  /**
   * Cycles to the next backdrop-order candidate and applies it to the live panels, without a restart.
   *
   * Invalidates the shared stage transaction; other workbench panels retain their own settings.
   *
   * DEBUG-only: a release build stays on candidate 0 and this is a no-op.
   */
  private fun cycleBackdropOrderCandidate() {
    if (!BuildConfig.DEBUG) return
    backdropOrderCandidate = (backdropOrderCandidate + 1) % BACKDROP_ORDER_CANDIDATE_COUNT
    applyBackdropOrderCandidateLive()
    syncBackdropOrderUi()
  }

  private fun applyBackdropOrderCandidateLive() {
    requestStageGeometry()
    val detail = reshapeUiPanelsForBackdropOrder()
    // One line per switch, on a fixed tag, carrying the candidate AND the values actually written, so a
    // device session can line "what I set" up against "what I see".
    Log.i(
        BACKDROP_ORDER_TAG,
        "candidate=$backdropOrderCandidate (${BACKDROP_ORDER_CANDIDATE_LABELS[backdropOrderCandidate]}) " +
            "video{${describeBackdropOrderConfig(videoLayerProbeConfig(), BackdropOrderLayer.VIDEO)}} " +
            "backdrop{${describeBackdropOrderConfig(overlayProbeConfig(isBackdrop = true), BackdropOrderLayer.BACKDROP)}} " +
            "danmaku{${describeBackdropOrderConfig(overlayProbeConfig(isBackdrop = false), BackdropOrderLayer.UI_PANEL)}} " +
            detail,
    )
  }

  /**
   * Rebuilds and reshapes every non-overlay `UIPanelSettings` panel from its own settings factory, with
   * the candidate applied. Panels whose scene object has not been created yet (or whose reshape the SDK
   * refuses) are reported rather than silently skipped.
   */
  private fun reshapeUiPanelsForBackdropOrder(): String =
      backdropOrderPanelTargets()
          .joinToString(" ") { target ->
            val settings = target.settings()
            val config = settings.toPanelConfigOptions()
            applyBackdropOrderCandidate(config, BackdropOrderLayer.UI_PANEL)
            val written = describeBackdropOrderConfig(config, BackdropOrderLayer.UI_PANEL)
            val entity = target.entity()
            if (entity == null) {
              "${target.name}{$written skipped=no-entity}"
            } else {
              val panel =
                  systemManager.findSystem<SceneObjectSystem>().getSceneObject(entity)?.getNow(null)
                      as? PanelSceneObject
              if (panel == null) {
                "${target.name}{$written skipped=no-scene-object}"
              } else {
                runCatching { panel.reshape(config) }
                    .fold(
                        onSuccess = { "${target.name}{$written}" },
                        onFailure = { error ->
                          "${target.name}{$written reshape-failed=${error.javaClass.simpleName}}"
                        },
                    )
              }
            }
          }

  /** Every UI panel whose relative order against the backdrop the candidates can affect. */
  private fun backdropOrderPanelTargets(): List<BackdropOrderPanelTarget> =
      mutableListOf<BackdropOrderPanelTarget>().apply {
        add(BackdropOrderPanelTarget("controls_id", { Entity(R.id.controls_id) }, ::controlsPanelSettings))
        add(
            BackdropOrderPanelTarget(
                "video_selector_panel",
                { Entity(R.id.video_selector_panel) },
                ::selectorPanelSettings,
            )
        )
        add(BackdropOrderPanelTarget("mr_panel", { Entity(R.id.mr_panel) }, ::mrPanelSettings))
        add(BackdropOrderPanelTarget("mode_panel", { Entity(R.id.mode_panel) }, ::modePanelSettings))
        add(
            BackdropOrderPanelTarget(
                "center_content_panel",
                { centerContentEntity },
                ::centerContentPanelSettings,
            )
        )
        add(
            BackdropOrderPanelTarget(
                "input_method_panel",
                { inputMethodPanelEntity },
                ::inputMethodPanelSettings,
            )
        )
        add(BackdropOrderPanelTarget("grab_bar_panel", { grabBarEntity }, ::grabBarPanelSettings))
        if (BuildConfig.DEBUG) {
          add(
              BackdropOrderPanelTarget(
                  "wrist_debug_panel",
                  { wristDebugPanelEntity },
                  ::wristDebugPanelSettings,
              )
          )
        }
      }

  /**
   * The video panel's real layer config, without touching the panel: built from the same settings the
   * creation / reshape path uses, so the log reports the value that path writes.
   */
  private fun videoLayerProbeConfig(): PanelConfigOptions = videoPanelConfig(currentPlaybackStageGeometry())

  /** Creation, reshape and diagnostics must use the identical layer/aperture configuration. */
  private fun videoPanelConfig(geometry: PlaybackStageGeometry): PanelConfigOptions {
    val content = geometry.videoShape
    return MediaPanelSettings(
              shape = videoShapeOptions(content.widthMeters, content.heightMeters, geometry.curvature),
              display =
                  PixelDisplayOptions(
                      width = IMMERSIVE_VIDEO_OUTPUT_WIDTH,
                      height = IMMERSIVE_VIDEO_OUTPUT_HEIGHT,
                  ),
              rendering = MediaPanelRenderOptions(stereoMode = StereoMode.None, zIndex = VIDEO_LAYER_Z_INDEX),
              input = PanelInputOptions(0),
          )
        .toPanelConfigOptions()
        .apply {
          applyVideoLayerDepth(this)

        }
  }

  /** An overlay config from the same factory used by the stage batch renderer. */
  private fun overlayProbeConfig(isBackdrop: Boolean): PanelConfigOptions {
    val geometry = currentPlaybackStageGeometry()
    val content = geometry.viewport
    return stageOverlayPanelSettings(
            content.widthMeters,
            content.heightMeters,
            geometry.curvature,
            isBackdrop = isBackdrop,
        )
        .toPanelConfigOptions()
  }

  private fun describeBackdropOrderConfig(
      config: PanelConfigOptions,
      layer: BackdropOrderLayer,
  ): String {
    // Read back only after the candidate has been applied, so this reports what was WRITTEN, not the
    // pre-candidate default the SDK produced.
    applyBackdropOrderCandidate(config, layer)
    val renderMode = if (config.layerConfig == null) "Mesh" else "Layer"
    return "renderMode=$renderMode layerZIndex=${config.layerConfig?.zIndex ?: "none"} " +
        "blend=${config.layerBlendType} customMesh=${config.sceneMeshCreator != null}"
  }

  private fun cycleBackdropOrderCandidateLabel(): String =
      if (!BuildConfig.DEBUG) {
        "1 ${BACKDROP_ORDER_CANDIDATE_LABELS[BACKDROP_ORDER_ZINDEX]} (release)"
      } else {
        "$backdropOrderCandidate ${BACKDROP_ORDER_CANDIDATE_LABELS[backdropOrderCandidate]}"
      }

  private fun syncBackdropOrderUi() {
    backdropOrderStatus?.text = "backdrop order: ${cycleBackdropOrderCandidateLabel()}"
  }

  private fun syncStageTapGuardUi() {
    stageTapGuardButton?.text =
        "Stage tap guard: ${if (stagePrimaryActionOcclusionGuardEnabled) "ON" else "OFF"}"
  }

  /**
   * DEBUG-only switch for the stage trigger's panel-occlusion guard
   * ([AnalogMediaStageTuningSystem]'s `stagePrimaryActionOcclusionGuardEnabled`).
   *
   * The guard cannot be settled statically: `StageRayTargeting` is pure geometry and cannot see a panel in
   * front of the stage plane. Defaults ON (conservative). A release build keeps ON.
   */
  private fun toggleStagePrimaryActionOcclusionGuard() {
    if (!BuildConfig.DEBUG) return
    stagePrimaryActionOcclusionGuardEnabled = !stagePrimaryActionOcclusionGuardEnabled
    Log.i(STAGE_TAP_TAG, "stageTapOcclusionGuard=$stagePrimaryActionOcclusionGuardEnabled")
    syncStageTapGuardUi()
  }

  /**
   * Overlay layer shape for the danmaku/backdrop panels. Dimensions and curvature are the same as
   * full stage viewport, independent of the video source aspect ratio.
   */
  private fun overlayShapeOptions(
      width: Float,
      height: Float,
      curvature: PlaybackStageCurvature,
  ): UIPanelShapeOptions =
      when (curvature) {
        is PlaybackStageCurvature.Cylinder ->
            CylinderShapeOptions(curvature.radiusMeters, width, height)
        PlaybackStageCurvature.Flat -> QuadShapeOptions(width, height)
      }

  /**
   * Records the current content half-height; bar/stage anchor geometry derives from it via
   * [currentBarOffsetY] / [currentStageAnchorOffsetY].
   */
  private fun updateGrabBarPose(contentHalfHeightMeters: Float) {
    grabBarContentHalfHeight = contentHalfHeightMeters
    // Re-pin the stage child offset so the video bottom stays above the bar by the gap.
    val stageEntity = Entity(R.id.spatialized_video_panel)
    val pose = stageEntity.tryGetComponent<Transform>()?.transform ?: return
    val newLocalY = currentStageAnchorOffsetY()
    if (kotlin.math.abs(pose.t.y - newLocalY) > 0.0001f) {
      stageEntity.setComponent(Transform(Pose(Vector3(pose.t.x, newLocalY, pose.t.z), pose.q)))
    }
  }

  private fun loadGLXF(onLoaded: ((GLXFInfo) -> Unit) = {}): Job {
    gltfxEntity = Entity.create()
    return activityScope.launch {
      try {
        glXFManager.inflateGLXF(
            Uri.parse("apk:///scenes/Composition.glxf"),
            rootEntity = gltfxEntity!!,
            onLoaded = onLoaded,
        )
      } catch (error: Exception) {
        Log.e(TAG, "Unable to load optional environment scene", error)
        setMrMode(scene.isSystemPassthroughEnabled())
      }
    }
  }

  override fun onVRReady() {
    super.onVRReady()
    if (!isFirstReadyDone) {
      val initialPose = Pose()
      // Stage grab follows the Horizon OS window model: ISDK drives the grab (the toolkit
      // Grabbable below was the conflict source and is gone). The stage keeps an authored
      // default pose; the persisted user height (Y only) is applied on top. Horizontal
      // position (X/Z) and yaw reset to these authored defaults every launch.
      val persistedStageY = ViriViriApplication.appState.loadWorkbenchStageYOrDefault()
      // Initialize the stage with the persisted scale directly at birth so the canvas matches
      // the danmaku/backdrop overlays from frame one (the state observer's early apply can race
      // panel creation; carrying the value here removes that dependency).
      val initialStageState = ViriViriApplication.appState.state.value
      applyPlaybackStageParameters(initialStageState.playbackDisplayRatio,
          initialStageState.playbackStageScale, initialStageState.playbackCurvature)
      // Seed the content half-height WITH the persisted scale before the anchor is placed, so the
      // bar/stage offset already reflects the loaded canvas size (the content quad re-reports the
      // same value via [updateGrabBarPose] once the aspect is known).
      grabBarContentHalfHeight = currentPlaybackStageGeometry().viewport.halfHeight
      // Scheme A layout order: create the grabbable ANCHOR first (world pose at the handle spot
      // below the video), then create the stage DIRECTLY as its child (stage-local (0,+offset,0))
      // so the video sits exactly above the bar — relative z = 0, no world-root -> reparent step
      // that could recompute the transform wrongly.
      createWorkbenchAnchor(initialPose, persistedStageY)
      Entity(R.id.spatialized_video_panel)
          .setComponents(
              listOf(
                  SpatializedAudioPanel(),
                  Transform(
                      Pose(
                          Vector3(0f, currentStageAnchorOffsetY(), 0f),
                          Quaternion(0f, 0f, 0f),
                      )
                  ),
                  TransformParent(workbenchRootEntity ?: Entity.nullEntity()),
              )
          )
      // Left rail is parented to the MediaStage with a STAGE-LOCAL transform (y=0 tracks the
      // stage height; z = railLocalZ sits it in front of the stage). This makes the rail ride
      // the stage when it is grabbed/lifted, unlike the earlier world-pose reparent mistake.
      Entity(R.id.video_selector_panel)
          .setComponents(
              listOf(
                  Panel(R.id.video_selector_panel),
                  Transform(
                      Pose(
                          // UX: left rail is the vertical mirror of the right rail.
                          Vector3(-layout.railX, 0f, layout.railLocalZ),
                          Quaternion(0f, -layout.railYawDegrees, 0f),
                      )
                  ),
                  TransformParent(Entity(R.id.spatialized_video_panel)),
              )
          )
      Entity(R.id.controls_id)
          .setComponents(
              listOf(
                  Panel(R.id.controls_id),
                  Transform(Pose(Vector3(0.0f, layout.transportLocalY, layout.transportLocalZ), Quaternion(layout.transportPitchDegrees, 0f, 0f))),
                  TransformParent(Entity(R.id.spatialized_video_panel)),
              )
          )
      Entity(R.id.mr_panel)
          .setComponents(
              listOf(
                  Panel(R.id.mr_panel),
                  Transform(Pose(Vector3(0.0f, layout.navLocalY, layout.navLocalZ), Quaternion(layout.navPitchDegrees, 0f, 0f))),
                  TransformParent(Entity(R.id.spatialized_video_panel)),
              )
          )
      bindCenterContentPanel()
      // Right rail mirrors the left rail: stage-local transform + parented to the stage.
      Entity(R.id.mode_panel)
          .setComponents(
              listOf(
                  Panel(R.id.mode_panel),
                  Transform(
                      Pose(
                          Vector3(layout.railX, 0f, layout.railLocalZ),
                          Quaternion(0f, layout.railYawDegrees, 0f),
                      )
                  ),
                  TransformParent(Entity(R.id.spatialized_video_panel)),
              )
          )
      environmentGLXF?.setComponents(listOf(Visible(false), Transform(initialPose)))
      mrPanelPose = (workbenchRootEntity ?: Entity(R.id.spatialized_video_panel))
          .getComponent<Transform>().transform
      Log.i("ViriViriSpatial", "vrReady videoPanelPose=$mrPanelPose")
      createVideoPanel()
      createInputMethodPanel()
      createStageBackdropPanel()
      createDanmakuOverlayPanel()
      canvasHandler.post { traceStageInputTargets() }
      immersivePlaybackCanvasHost.applyInitialState()
      // Quiet Watch is valid only after a video exists. Otherwise Browse is the sole entry route.
      if (ViriViriApplication.appState.state.value.selected == null) {
        dispatchPlaybackCanvas(PlaybackCanvasEvent.OpenBrowse)
      }
      setMrMode(scene.isSystemPassthroughEnabled())
      isFirstReadyDone = true
    }
  }

  /**
   * Clears the persisted stage height and snaps the stage + rails back to their authored
   * default world Y immediately (no restart needed). Debug/testing aid for tuning the
   * default height without being masked by a previously persisted value.
   */
  fun resetStageYToDefault() {
    val defaultY = ViriViriApplication.appState.resetWorkbenchStageY()
    applyStageWorldY(defaultY)
  }

  /**
   * Resets the grab-bar anchor so the STAGE centre returns to world Y=[y] (bar sits below the
   * stage by [currentBarOffsetY]); stage + all children follow the bar.
   */
  private fun applyStageWorldY(y: Float) {
    val initialPose = Pose()
    val root = workbenchRootEntity ?: Entity(R.id.spatialized_video_panel)
    root.setComponent(
        Transform(initialPose * Pose(Vector3(0f, y - currentBarOffsetY(), 2f), Quaternion(0f, 0f, 0f)))
    )
    Log.i(TAG, "resetStageYToDefault stageY=$y barY=${y - currentBarOffsetY()}")
  }

  /**
   * Creates, on first use, the child entity that carries the video surface.
   *
   * Mirrors the rail panels (`Transform` + `TransformParent` in one component list) so the pose is
   * stage-local. Creation only registers the carrier; the controller commits its absolute
   * curvature offset together with every other layer once the full panel group is ready.
   */
  private fun ensureVideoSurfaceEntity(stageRoot: Entity): Entity {
    videoSurfaceEntity?.let { return it }
    val entity =
        Entity.create(
            listOf(
                Transform(Pose(Vector3(0f, 0f, videoSurfaceBaseLocalZ))),
                TransformParent(stageRoot),
            )
        )
    videoSurfaceEntity = entity
    return entity
  }

  // Video Panel
  @androidx.annotation.OptIn(UnstableApi::class)
  private fun createVideoPanel() {
    if (stageClosed) return
    if (Looper.myLooper() != Looper.getMainLooper()) {
      canvasHandler.post { createVideoPanel() }
      return
    }
    runCatching { replaceProductionVideo(playbackStageController.layout) }.onFailure {
      Log.e("ViriViriStage", "initial video creation failed; main frame will retry", it)
      requestProductionStageFrame()
    }
    player.videoScalingMode = androidx.media3.common.C.VIDEO_SCALING_MODE_SCALE_TO_FIT
    run {
          player.repeatMode = Player.REPEAT_MODE_ONE
          player.setSeekParameters(SeekParameters.CLOSEST_SYNC)
          seekBar.thenAccept { it ->
            it.setOnSeekBarChangeListener(
                object : SeekBar.OnSeekBarChangeListener {
                  override fun onProgressChanged(
                      seekBar: SeekBar?,
                      progress: Int,
                      fromUser: Boolean,
                  ) {
                    if (fromUser) {
                      seekDragPositionMs = progress.toLong()
                      syncTransportTimeline()
                      player.seekTo(progress.toLong())
                      resetControllerFadeOutTimer()
                    }
                  }

                  override fun onStartTrackingTouch(seekBar: SeekBar?) {
                    isSeeking = true
                    seekDragPositionMs = seekBar?.progress?.toLong()
                    if (seekDragPlaybackPolicy.start(player.playWhenReady)) {
                      player.playWhenReady = false
                    }
                    resetControllerFadeOutTimer()
                  }

                  override fun onStopTrackingTouch(seekBar: SeekBar?) {
                    isSeeking = false
                    seekDragPositionMs = null
                    syncTransportTimeline()
                    seekDragPlaybackPolicy.finish()?.let { player.playWhenReady = it }
                  }
                }
            )
          }

          // Stage actions belong exclusively to AnalogMediaStageTuningSystem's press edge.
          // A native onClick callback here fires on release and would toggle the workbench twice.

        }

    // The shared player may already have decoded dimensions before this Activity subscribes.
    val size = player.videoSize
    playbackStageController.setVideoSize(size.width, size.height, size.pixelWidthHeightRatio)
    requestStageGeometry()
  }

  /** Detach old media output before destroying its SDK Surface. Do not reshape video. */
  private fun replaceProductionVideo(layout: PlaybackStageLayout): PanelSceneObject {
    check(Looper.myLooper() == Looper.getMainLooper())
    check(!stageClosed)
    val geometry = layout.layers.first { it.role == PlaybackStageLayer.VIDEO }
    val entity = ensureVideoSurfaceEntity(Entity(R.id.spatialized_video_panel))
    val old = spatialVideoPanelSceneObject
    if (old != null && productionVideoGeometry == geometry) return old
    val replacementStartedAt = android.os.SystemClock.elapsedRealtimeNanos()
    productionVideoGeometry = null
    val objects = systemManager.findSystem<SceneObjectSystem>()
    disposeProductionVideo()
    entity.setComponent(Transform(geometry.localPose()))
    val next = PanelSceneObject(scene, entity, videoPanelConfig(layout.geometry))
    // Own the candidate before registration/bind can fail; retry cleans it up before replacing.
    spatialVideoPanelSceneObject = next
    objects.addSceneObject(entity, CompletableFuture.completedFuture<SceneObject>(next))
    productionVideoRegistered = true
    entity.setComponent(Hittable(MeshCollision.NoCollision))
    check(next.surface.isValid) { "Invalid production video Surface" }
    attachImmersiveOutput(next)
    productionVideoGeometry = geometry
    productionVideoGeneration++
    Log.i("ViriViriStage", "video created generation=$productionVideoGeneration " +
        "size=${geometry.content.widthMeters}x${geometry.content.heightMeters} " +
        "radius=${geometry.curvature.radiusOrNull()} carrierZ=${geometry.localPose().t.z} " +
        "surface=${System.identityHashCode(next.surface)} " +
        "replaceMs=${(android.os.SystemClock.elapsedRealtimeNanos() - replacementStartedAt) / 1_000_000.0}")
    return next
  }

  /** Failed candidates are owned too, including those not yet registered with the scene system. */
  private fun disposeProductionVideo() {
    val old = spatialVideoPanelSceneObject ?: return
    ViriViriApplication.appState.playerSession.detachSurface(old.surface)
    if (productionVideoRegistered) {
      systemManager.findSystem<SceneObjectSystem>().removeSceneObject(checkNotNull(videoSurfaceEntity))
      productionVideoRegistered = false
    }
    old.destroy()
    spatialVideoPanelSceneObject = null
    productionVideoGeometry = null
  }

  private fun configureDanmakuLayer(config: PanelConfigOptions) {
    config.enableTransparent = true
    config.includeGlass = false
    checkNotNull(config.layerConfig).zIndex = VIDEO_LAYER_Z_INDEX + 1
  }

  // Movies Controller panel
  /**
   * `UIPanelSettings` for the transport strip.
   *
   * Named (rather than inline in the registration) because the backdrop-order candidates have to rebuild
   * the very same settings to reshape this panel at runtime — see [reshapeUiPanelsForBackdropOrder].
   */
  private fun controlsPanelSettings(): UIPanelSettings =
      UIPanelSettings(
          shape = QuadShapeOptions(width = 1.32f, height = 0.38f),
          display = DpDisplayOptions(width = 460f, height = 132f, dpi = 600),
          rendering = uiPanelRenderOptions(),
          style = PanelStyleOptions(themeResourceId = R.style.PanelAppThemeTransparent),
      )

  private fun controlsPanelRegistration(): PanelRegistration {
    return LayoutXMLPanelRegistration(
        R.id.controls_id,
        layoutIdCreator = { R.layout.controls },
        settingsCreator = { controlsPanelSettings() },
        panelSetupWithRootView = { rootView, _, _ ->
          val localSeekBar = rootView.findViewById<SeekBar>(R.id.seek_bar)!!
          seekBar.complete(localSeekBar)
          elapsedTime.complete(rootView.findViewById(R.id.elapsed_time))
          durationTime.complete(rootView.findViewById(R.id.duration_time))
          syncTransportTimeline()
          startTransportTimelineUpdates()

          val browseButton = rootView.findViewById<Button>(R.id.browse_button)!!
          browseButton.setOnClickListener { openBrowseCanvas() }
          setupHoverAndTouchListeners(browseButton)
          val volumeButtonLocal = rootView.findViewById<Button>(R.id.volume_button)!!
          volumeButton.complete(volumeButtonLocal)
          syncPlaybackVolumeLabel()
          volumeButtonLocal.setOnClickListener { showPlaybackVolumeMenu(volumeButtonLocal) }
          setupHoverAndTouchListeners(volumeButtonLocal)
          val qualityButtonLocal = rootView.findViewById<Button>(R.id.quality_button)!!
          qualityButton.complete(qualityButtonLocal)
          syncPlaybackQualityLabel(ViriViriApplication.appState.state.value.playbackQuality)
          qualityButtonLocal.setOnClickListener { showPlaybackQualityMenu(qualityButtonLocal) }
          setupHoverAndTouchListeners(qualityButtonLocal)
          val speedButtonLocal = rootView.findViewById<Button>(R.id.speed_button)!!
          speedButton.complete(speedButtonLocal)
          syncPlaybackSpeedLabel()
          speedButtonLocal.setOnClickListener { showPlaybackSpeedMenu(speedButtonLocal) }
          setupHoverAndTouchListeners(speedButtonLocal)
          val stageSettingsButton = rootView.findViewById<Button>(R.id.stage_settings_button)
          stageSettingsButton.setOnClickListener { showStageSettingsMenu(stageSettingsButton) }
          setupHoverAndTouchListeners(stageSettingsButton)
          val playPauseButtonLocal = rootView.findViewById<Button>(R.id.play_pause_button)!!
          playPauseButton.complete(playPauseButtonLocal)
          syncPlaybackControls()
          playPauseButtonLocal.setOnClickListener { togglePlay() }
          setupHoverAndTouchListeners(playPauseButtonLocal)
          val backButton = rootView.findViewById<Button>(R.id.back_button)!!
          backButton.setOnClickListener { ViriViriApplication.appState.selectAdjacentRecommendation(-1) }
          setupHoverAndTouchListeners(backButton)
          val forwardButton = rootView.findViewById<Button>(R.id.forward_button)!!
          forwardButton.setOnClickListener { ViriViriApplication.appState.selectAdjacentRecommendation(1) }
          setupHoverAndTouchListeners(forwardButton)
          controllerView = rootView
          applyTransportOverlayVisibility(transportOverlayState.visible)
        },
    )
  }

  private fun centerContentPanelSettings(): UIPanelSettings =
      UIPanelSettings(
          // UX: center Search/List stays readable at three columns without retaining a high-resolution panel buffer.
          shape = QuadShapeOptions(width = layout.centerWidth, height = layout.centerHeight),
          display = DpDisplayOptions(width = 768f, height = 615f, dpi = 512),
          rendering = uiPanelRenderOptions(),
          style = PanelStyleOptions(themeResourceId = R.style.PanelAppThemeTransparent),
      )

  private fun centerContentPanelRegistration(): PanelRegistration =
      ComposeViewPanelRegistration(
          R.id.center_content_panel,
          composeViewCreator = { _, context ->
            ComposeView(context).apply {
              setContent {
                // UX: blank centre-panel space dismisses only from the WORKBENCH_EMPTY route
                // (see RecommendationUi.CenterContentWorkspace). Outside the Workbench,
                // WorkbenchOuterDismiss still owns dismissal.
                ImmersiveCenterContentPanel(
                    appState = ViriViriApplication.appState,
                    onVideoSelected = ::returnToPlaybackFromCenterContent,
                    onDismissWorkbench = { dismissWorkbenchFromCenterContent("centerPanelBlank") },
                )
              }
            }
          },
          settingsCreator = { centerContentPanelSettings() },
      )

  private fun inputMethodPanelSettings(): UIPanelSettings =
      UIPanelSettings(
          shape = QuadShapeOptions(width = 1.62f, height = 0.68f),
          display = DpDisplayOptions(width = 832f, height = 348f, dpi = 512),
          rendering = uiPanelRenderOptions(),
          style = PanelStyleOptions(themeResourceId = R.style.PanelAppThemeTransparent),
      )

  private fun inputMethodPanelRegistration(): PanelRegistration =
      ComposeViewPanelRegistration(
          R.id.input_method_panel,
          composeViewCreator = { _, context ->
            ComposeView(context).apply {
              setContent { ImmersiveInputMethodPanel(appState = ViriViriApplication.appState) }
            }
          },
          settingsCreator = { inputMethodPanelSettings() },
      )

  private fun stageBackdropPanelRegistration(): PanelRegistration =
      ComposeViewPanelRegistration(
          R.id.stage_backdrop_panel,
          composeViewCreator = { _, context -> ComposeView(context).apply { setContent { StageBackdrop() } } },
          settingsCreator = {
            val geometry = currentPlaybackStageGeometry()
            val content = geometry.backdropContent
            stageOverlayPanelSettings(
                content.widthMeters,
                content.heightMeters,
                geometry.curvature,
                isBackdrop = true,
            )
          },
      )

  private fun danmakuOverlayPanelRegistration(): PanelRegistration =
      ComposeViewPanelRegistration(
          R.id.danmaku_overlay_panel,
          composeViewCreator = { _, context ->
            ComposeView(context).apply {
              setContent {
                // OBSERVE, do not snapshot. `state.value` read in a composable is NOT observable, so
                // passing it straight down froze `selectedVideoId` at first composition: with no video
                // selected the overlay never appeared, and after switching videos it kept rendering the
                // OLD video's runtime. The overlay used to own this observation itself
                // (`state.collectAsState()`); the refactor moved the read to this host boundary without
                // moving the subscription, which is exactly the gap this line closes.
                val uiState by ViriViriApplication.appState.state.collectAsState()
                // The host supplies the facts the overlay needs. Reading the singleton here, at the host
                // boundary, is the intended split: the overlay itself owns no global state.
                DanmakuOverlay(
                    DanmakuOverlayProps(
                        selectedVideoId = uiState.selected?.videoId,
                        selectedContentId = uiState.selected?.contentId,
                        streamSourceFor = ViriViriApplication.appState::danmakuStreamSource,
                        playerPositionMs = {
                          ViriViriApplication.appState.playerSession.player.currentPosition
                        },
                        isPlaying = { ViriViriApplication.appState.playerSession.player.isPlaying },
                    )
                )
              }
            }
          },
          settingsCreator = {
            val geometry = currentPlaybackStageGeometry()
            val content = geometry.danmakuContent
            stageOverlayPanelSettings(content.widthMeters, content.heightMeters, geometry.curvature)
          },
      ).config { configureDanmakuLayer(this) }

  /**
   * Visual grab bar under the video stage. It is a NON-grabbable, non-interactive indicator
   * (NoCollision): the ISDK grab handle already makes the stage edges grabbable, this bar just
   * makes the bottom grab zone visible so the user knows where to grab to move the whole
   * workbench. See task 09-02-workbench-lift-and-grab.
   */
  private fun grabBarPanelSettings(): UIPanelSettings =
      UIPanelSettings(
          shape = QuadShapeOptions(width = GRAB_BAR_WIDTH_METERS, height = GRAB_BAR_HEIGHT_METERS),
          display = DpDisplayOptions(width = 512f, height = 56f, dpi = 600),
          rendering = uiPanelRenderOptions(),
          input = PanelInputOptions(0),
          style = PanelStyleOptions(themeResourceId = R.style.PanelAppThemeTransparent),
      )

  private fun grabBarPanelRegistration(): PanelRegistration =
      LayoutXMLPanelRegistration(
          R.id.grab_bar_panel,
          layoutIdCreator = { R.layout.grab_bar },
          settingsCreator = { grabBarPanelSettings() },
      )

  /**
   * @param isBackdrop true only for `stage_backdrop_panel`; the backdrop-order candidates treat the dim
   *   layer differently from the danmaku overlay it shares this settings builder with.
   */
  private fun stageOverlayPanelSettings(
      width: Float,
      height: Float,
      curvature: PlaybackStageCurvature,
      isBackdrop: Boolean = false,
  ): UIPanelSettings {
    return UIPanelSettings(
          shape = overlayShapeOptions(width, height, curvature),
          display = DpDisplayOptions(width = 1280f, height = 720f, dpi = 160),
          rendering = if (isBackdrop) uiPanelRenderOptions(isBackdrop = true)
              else UIPanelRenderOptions(PanelRenderMode.Layer(layerBlendType = PanelShapeLayerBlendType.ALPHA_BLEND)),
          input = PanelInputOptions(0),
          style = PanelStyleOptions(themeResourceId = R.style.PanelAppThemeTransparent),
      )
  }

  private fun createInputMethodPanel() {
    if (inputMethodPanelEntity != null) return
    inputMethodPanelEntity =
        Entity.createPanelEntity(
                R.id.input_method_panel,
                // Below the center content and farther forward than Transport for near-field typing.
                Transform(Pose(Vector3(0f, layout.keyboardLocalY, layout.keyboardLocalZ), Quaternion(layout.keyboardPitchDegrees, 0f, 0f))),
                TransformParent(Entity(R.id.spatialized_video_panel)),
                Visible(false),
            )
            .also { it.setComponent(Panel(R.id.input_method_panel)) }
    syncInputMethodPanelVisibility(ViriViriApplication.appState.state.value)
  }

  private fun syncInputMethodPanelVisibility(appState: ViriViriUiState) {
    val entity = inputMethodPanelEntity ?: return
    val visible =
        appState.inputUi.targetId != null && appState.inputUi.visible &&
            appState.inputUi.mode == com.m0e_n00b.spatialworkbench.core.InputMode.INTERNAL
    if (::spatialPanelVisibilityController.isInitialized) {
      spatialPanelVisibilityController.setVisible(PanelSlot.ACTION_SHEET, entity, visible)
    } else {
      entity.setComponent(Visible(visible))
    }
  }

  private fun createStageBackdropPanel() {
    if (stageBackdropEntity != null) return
    stageBackdropEntity =
        Entity.createPanelEntity(
                R.id.stage_backdrop_panel,
                Transform(Pose(Vector3(0f, 0f, stageBackdropBaseLocalZ))),
                TransformParent(Entity(R.id.spatialized_video_panel)),
                // Disabled until a uniform Spatial material replaces compositor-dithered UI alpha.
                Visible(false),
            )
            // PanelInputOptions disables buttons but does not disable the panel's raycast collider.
            .also { it.setComponent(Panel(R.id.stage_backdrop_panel, MeshCollision.NoCollision)) }
    // Sync immediately so the dim layer reflects the current Workbench visibility
    // even when the panel is created while a Workbench is already on screen.
    if (::immersiveWorkbenchHost.isInitialized) {
      val modules = ImmersiveWorkbenchReducer.modules(immersiveWorkbenchHost.state)
      spatialPanelVisibilityController.setVisible(PanelSlot.MEDIA_STAGE, stageBackdropEntity!!, modules.isNotEmpty())
    }
    requestStageGeometry()
  }

  private fun createDanmakuOverlayPanel() {
    if (danmakuOverlayEntity != null) return
    danmakuOverlayEntity =
        Entity.createPanelEntity(
                R.id.danmaku_overlay_panel,
                Transform(Pose(Vector3(0f, 0f, stageDanmakuBaseLocalZ))),
                TransformParent(Entity(R.id.spatialized_video_panel)),
                Visible(false), // First shown by the geometry owner after B is applied.
            )
            // The overlay must render but never block the stage's controller/hand raycasts.
            .also { it.setComponent(Panel(R.id.danmaku_overlay_panel, MeshCollision.NoCollision)) }
    requestStageGeometry()
  }

  private fun traceStageInputTargets() {
    val stageEntity = Entity(R.id.spatialized_video_panel)
    val currentStageObject =
        systemManager.findSystem<SceneObjectSystem>().getSceneObject(stageEntity)?.getNow(null)
    Log.d(
        WORKBENCH_TRACE_TAG,
        "stageInputTargets videoPanel=${stageEntity.tryGetComponent<Panel>()?.hittable} " +
            "videoHittable=${stageEntity.tryGetComponent<Hittable>()?.hittable} " +
            "danmakuPanel=${Entity(R.id.danmaku_overlay_panel).tryGetComponent<Panel>()?.hittable} " +
            "backdropPanel=${Entity(R.id.stage_backdrop_panel).tryGetComponent<Panel>()?.hittable} " +
            "listenerObjectCurrent=${currentStageObject === spatialVideoPanelSceneObject}",
    )
  }

  private fun wristDebugPanelSettings(): UIPanelSettings =
      UIPanelSettings(
          shape = QuadShapeOptions(width = WRIST_DEBUG_PANEL_WIDTH, height = WRIST_DEBUG_PANEL_HEIGHT),
          display = DpDisplayOptions(width = 224f, height = 80f, dpi = 1600),
          rendering = uiPanelRenderOptions(),
          style = PanelStyleOptions(themeResourceId = R.style.PanelAppThemeTransparent),
      )

  private fun wristDebugPanelRegistration(): PanelRegistration {
    return LayoutXMLPanelRegistration(
        R.id.wrist_debug_panel,
        layoutIdCreator = { R.layout.wrist_debug_panel },
        settingsCreator = { wristDebugPanelSettings() },
        panelSetupWithRootView = { rootView, _, _ ->
          rootView.findViewById<TextView>(R.id.wrist_debug_label).apply {
            text = "DEV ${BuildConfig.GIT_SHA} · 调试设置"
            setOnClickListener { showStageSettingsMenu(this, includeDebug = true) }
          }
        },
    )
  }

  private fun createWristDebugPanel() {
    if (wristDebugPanelEntity != null) return
    wristDebugPanelEntity =
        Entity.createPanelEntity(
            R.id.wrist_debug_panel,
            Transform(Pose()),
            WristAttached(position = Vector3(0f, 0.03f, 0.02f), faceUser = true),
            Visible(false),
        )
  }

  private fun modePanelSettings(): UIPanelSettings =
      UIPanelSettings(
          // W3: match left rail raster aspect; old 280x464 stretched the same 0.70x0.90m quad.
          shape = QuadShapeOptions(width = layout.railWidth, height = layout.leftRailHeight),
          display = DpDisplayOptions(width = 361f, height = 464f, dpi = 800),
          rendering = uiPanelRenderOptions(),
          style = PanelStyleOptions(themeResourceId = R.style.PanelAppThemeTransparent),
      )

  private fun modePanelRegistration(): PanelRegistration =
      ComposeViewPanelRegistration(
          R.id.mode_panel,
          composeViewCreator = { _, context ->
            ComposeView(context).apply {
              setContent { ImmersiveRightPanel(appState = ViriViriApplication.appState) }
            }
          },
          settingsCreator = { modePanelSettings() },
      )

  private fun launchPanelModeInHome() {
    val panelIntent =
        Intent(applicationContext, PancakeActivity::class.java).apply {
          action = Intent.ACTION_MAIN
          addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    val pendingPanelIntent =
        PendingIntent.getActivity(
            applicationContext,
            0,
            panelIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    startActivity(
        Intent(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_HOME)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            .putExtra("extra_launch_in_home_pending_intent", pendingPanelIntent)
    )
  }

  override fun onNewIntent(intent: Intent) {
    super.onNewIntent(intent)
    setIntent(intent)
    shouldReattachImmersiveOutput =
        intent.getBooleanExtra(EXTRA_REATTACH_IMMERSIVE_OUTPUT, false)
  }

  override fun onResume() {
    super.onResume()
    if (stageClosed) return
    if (productionVideoGeometry != null) spatialVideoPanelSceneObject?.let(::attachImmersiveOutput)
    else requestProductionStageFrame()
    if (::immersivePlaybackCanvasHost.isInitialized) immersivePlaybackCanvasHost.applyCurrentState()
  }

  override fun onDestroy() {
    stageClosed = true
    playbackStageController.close()
    productionVideoGeometry = null
    runCatching {
      disposeProductionVideo()
      videoSurfaceEntity?.destroy()
      videoSurfaceEntity = null
    }.onFailure { Log.e("ViriViriStage", "production video cleanup failed", it) }
    player.removeListener(immersiveStagePlayerListener)
    player.removeListener(immersiveControlsPlayerListener)
    browseSelectionObserver?.cancel()
    browseSelectionObserver = null
    browseCommandObserver?.cancel()
    browseCommandObserver = null
    canvasHandler.removeCallbacks(transportTimelineUpdater)
    canvasHandler.removeCallbacksAndMessages(null)
    if (::spatialPanelVisibilityController.isInitialized) spatialPanelVisibilityController.clear()
    if (::immersiveMediaStageHost.isInitialized) immersiveMediaStageHost.close()
    wristDebugPanelEntity?.destroy()
    wristDebugPanelEntity = null
    super.onDestroy()
  }

  private fun attachImmersiveOutput(panel: PanelSceneObject) {
    if (stageClosed) return
    if (shouldReattachImmersiveOutput) {
      immersiveMediaStageHost.attachOutputAfterHandoff(panel.surface)
      shouldReattachImmersiveOutput = false
    } else {
      immersiveMediaStageHost.attachOutput(panel.surface)
    }
    reportImmersiveStageClock()
  }

  private fun reportImmersiveStageClock() {
    if (::immersiveMediaStageHost.isInitialized) {
      immersiveMediaStageHost.updateClock(
          positionMs = player.currentPosition,
          durationMs = player.duration.takeIf { it >= 0L },
          isPlaying = player.isPlaying,
      )
    }
  }

  private fun setupHoverAndTouchListeners(view: View) {
    view.setOnTouchListener { v, event ->
      val action = event.action
      when (action) {
        MotionEvent.ACTION_DOWN -> {}
        MotionEvent.ACTION_MOVE -> {
          openPlaybackCanvasIfQuiet()
          resetControllerFadeOutTimer()
        }
        MotionEvent.ACTION_UP -> {
          openPlaybackCanvasIfQuiet()
          resetControllerFadeOutTimer()
        }
        MotionEvent.ACTION_CANCEL -> {}
      }
      false
    }
    view.setOnHoverListener { v, event ->
      val action = event.action
      when (action) {
        MotionEvent.ACTION_HOVER_ENTER -> {
          openPlaybackCanvasIfQuiet()
          animateControllerVisibility(true)
          resetControllerFadeOutTimer()
        }
        MotionEvent.ACTION_HOVER_MOVE -> {
          openPlaybackCanvasIfQuiet()
          resetControllerFadeOutTimer()
        }
        MotionEvent.ACTION_HOVER_EXIT -> {}
      }
      true
    }
  }

  fun animateControllerVisibility(visible: Boolean) {
    if (!this::controllerView.isInitialized) return
    alphaAnimator?.cancel()
    transportOverlayState = transportOverlayState.copy(visible = visible)
    if (visible) controllerView.visibility = View.VISIBLE
    controllerView.isClickable = visible
    controllerView.isFocusable = visible
    alphaAnimator =
        ObjectAnimator.ofFloat(controllerView, "alpha", controllerView.alpha, if (visible) 1.0f else 0.0f)
            .apply {
              duration = TRANSPORT_FADE_DURATION_MS
              if (!visible) {
                addListener(
                    object : AnimatorListenerAdapter() {
                      override fun onAnimationEnd(animation: Animator) {
                        if (!transportOverlayState.visible) controllerView.visibility = View.INVISIBLE
                      }
                    }
                )
              }
              start()
            }
  }

  private fun applyTransportOverlayVisibility(visible: Boolean) {
    transportOverlayState = transportOverlayState.copy(visible = visible)
    controllerView.visibility = if (visible) View.VISIBLE else View.INVISIBLE
    controllerView.alpha = if (visible) 1.0f else 0.0f
    controllerView.isClickable = visible
    controllerView.isFocusable = visible
  }

  private fun showTransportOverlay() {
    animateControllerVisibility(true)
    resetControllerFadeOutTimer()
  }

  private fun openPlaybackCanvasIfQuiet() {
    if (::immersivePlaybackCanvasHost.isInitialized &&
        immersivePlaybackCanvasHost.state.canvas == PlaybackCanvas.QUIET_WATCH) {
      immersivePlaybackCanvasHost.dispatch(PlaybackCanvasEvent.PrimaryStageAction)
    }
  }

  fun resetControllerFadeOutTimer() {
    if (!this::controllerView.isInitialized) return
    // UX: Transport no longer owns an independent timeout; it remains visible with the Workbench.
    if (!transportOverlayState.visible && alphaAnimator?.isRunning != true) {
      animateControllerVisibility(true)
    }
  }

  /**
   * The ANALOG half of the stage control (thumbstick scale / curvature) belongs to the user only while
   * the workbench is away.
   *
   * With the panels up the user is working them, and the stage sits right in the middle of that
   * interaction volume, so a live stick underneath the panels made them unusable.
   *
   * This is NOT the gate for the trigger half — see [isStagePrimaryActionEnabled].
   */
  private fun isStageInputEnabled(): Boolean =
      !(::immersiveWorkbenchHost.isInitialized && immersiveWorkbenchHost.state.visible)

  /**
   * The TRIGGER half of the stage control, split from [isStageInputEnabled] on purpose.
   *
   * The stage is the workbench's toggle, so the trigger has to keep working while the panels are up.
   * Under the old single gate the whole system self-disabled the moment the workbench appeared, which is
   * why a second stage tap could neither reach [onStagePrimaryAction] nor close anything: the workbench
   * was openable from the stage and never closable from it.
   *
   * The value is unconditionally true rather than "true when visible": the stage action is meaningful in
   * every canvas state, and the reducer — not this predicate — owns what it does.
   */
  private fun isStagePrimaryActionEnabled(): Boolean = true

  /**
   * The stage tap's void outcome: the trigger was pressed while the ray pointed at NO object at all.
   *
   * This is the gesture the user asked for — "click any void with no entity" — and it exists because the
   * old `WorkbenchOuterDismiss` slab, which used to own "click outside the workbench", can no longer be
   * the target without the SDK drawing a target-dot indicator over empty space. See
   * [hideOuterDismissHitLayer].
   *
   * Dismissal goes through [dismissWorkbenchFromCenterContent] like every other dismissal, so the state
   * transition still has exactly one owner. `source` is only there to tell the paths apart in a trace.
   */
  private fun onStageVoidTap() {
    val visible = ::immersiveWorkbenchHost.isInitialized && immersiveWorkbenchHost.state.visible
    Log.i(STAGE_TAP_TAG, "voidTap dismiss workbenchVisible=$visible")
    if (visible) dismissWorkbenchFromCenterContent("voidTap")
  }

  /**
   * World-space stage plane and scaled footprint, used by the geometric stage targeting.
   *
   * Built from the stage ROOT (`R.id.spatialized_video_panel`) rather than from the render carrier: the
   * root is the entity that never receives a curvature offset, and its plane is where the compensated
   * picture lands. See [StageRayTargeting] for why targeting must not be driven by
   * `PointerInfoSystem.rightEntity`.
   *
   * The pose MUST be resolved through the parent chain. `Transform` holds the LOCAL pose, and the stage
   * root hangs under `workbenchRootEntity`, so its raw transform reads `(0, currentStageAnchorOffsetY(),
   * 0)` — z = 0 — while the stage actually sits at the anchor's world z (about 2 m). Feeding that local
   * pose to the targeting made every ray miss, because the controller ray crosses z = 0 behind its own
   * origin (s < 0).
   */
  private fun currentStageFrame(): StageRayTargeting.Frame? {
    val applied = playbackStageController.appliedLayout ?: return null
    val pose = worldPoseOf(Entity(R.id.spatialized_video_panel)) ?: return null
    val centre = pose.t
    fun axis(x: Float, y: Float, z: Float): StageRayTargeting.Vec3 {
      val world = pose * Vector3(x, y, z)
      return StageRayTargeting.Vec3(world.x - centre.x, world.y - centre.y, world.z - centre.z)
    }
    return StageRayTargeting.Frame(
        centre = StageRayTargeting.Vec3(centre.x, centre.y, centre.z),
        normal = axis(0f, 0f, 1f),
        right = axis(1f, 0f, 0f),
        up = axis(0f, 1f, 0f),
        halfWidth = applied.geometry.viewport.halfWidth,
        halfHeight = applied.geometry.viewport.halfHeight,
    )
  }

  /**
   * Composes [entity]'s [Transform] with every `TransformParent` above it into a world pose.
   *
   * Needed because the SDK exposes only the local pose here — there is no world-transform accessor on
   * `Transform` or `Entity` in 0.13.2 (checked with javap). The depth guard covers a self-parenting or
   * cyclic scene graph rather than trusting the chain to terminate.
   */
  private fun worldPoseOf(entity: Entity): Pose? {
    var pose = entity.tryGetComponent<Transform>()?.transform ?: return null
    var parent: Entity? = entity.tryGetComponent<TransformParent>()?.entity
    var depth = 0
    while (parent != null && parent != Entity.nullEntity() && depth++ < MAX_PARENT_DEPTH) {
      val parentPose = parent.tryGetComponent<Transform>()?.transform ?: break
      pose = parentPose * pose
      parent = parent.tryGetComponent<TransformParent>()?.entity
    }
    return pose
  }

  /**
   * The single definition of "the user acted on the MediaStage".
   *
   * Both the stage click and the controller escape hatch ([AnalogWorkbenchSummonSystem]) go through
   * here, so the two can never drift into different canvas / workbench states. The stage click is not
   * the owner of the transition either — it just injects [PlaybackCanvasEvent.PrimaryStageAction] and
   * lets the canvas resolve slot visibility, which is what drives the workbench.
   *
   * The action is a TOGGLE and this handler must not special-case either direction: the reducer alone
   * decides whether it opens or closes. The previous `if (workbench visible) { reveal transport }` branch
   * was exactly that special case, and it is why a second stage tap could never close the workbench.
   */
  private fun onStagePrimaryAction() {
    val wasPlaybackCanvas =
        ::immersivePlaybackCanvasHost.isInitialized &&
            immersivePlaybackCanvasHost.state.canvas == PlaybackCanvas.PLAYBACK
    dispatchPlaybackCanvas(PlaybackCanvasEvent.PrimaryStageAction)
    if (wasPlaybackCanvas) {
      // Closing: the transport panel goes with the workbench, exactly as it does on every other
      // dismissal (`dismissWorkbenchFromCenterContent`). Its Android root is faded too, so a hidden
      // transport can never keep accepting input behind a dismissed workbench.
      animateControllerVisibility(false)
    } else {
      when (ImmersiveTransportOverlayPolicy.primaryAction()) {
        ImmersiveTransportPrimaryAction.REVEAL_TRANSPORT -> showTransportOverlay()
      }
    }
  }

  private fun dispatchPlaybackCanvas(event: PlaybackCanvasEvent) {
    if (::immersivePlaybackCanvasHost.isInitialized) immersivePlaybackCanvasHost.dispatch(event)
  }

  /**
   * Runs the MediaStage's primary action from the controller instead of from a stage hit.
   *
   * This exists because the stage's hit geometry belongs to the video surface entity, which moves with
   * the curvature offset: a bad curvature can make the stage unclickable, and the debug rail that owns
   * the curvature reset is itself a workbench module, so there was no way out but clearing app data.
   *
   * It calls [onStagePrimaryAction] rather than dispatching a [WorkbenchEvent] itself. An earlier
   * version dispatched `RevealTransport` directly and that was wrong: the workbench is DOWNSTREAM of
   * the canvas, so the canvas stayed in QUIET_WATCH while the workbench went visible. Slot visibility
   * ([ImmersivePlaybackCanvasHost.applyCurrentVisibleSlots] -> `applyPlaybackCanvasSlots`) never ran,
   * which is why the A-summoned workbench came up without the transport and dismissed unreliably.
   */
  private fun summonWorkbenchFromController() {
    Log.i("ViriViriWorkbench", "stage primary action from controller A")
    onStagePrimaryAction()
  }

  fun openHomeCanvas() {
    val appState = ViriViriApplication.appState
    val wasShowingSearchResults = appState.state.value.isShowingSearchResults
    immersiveBrowseSession = ImmersiveBrowseSessionReducer.open(appState.state.value.selected?.videoId)
    appState.closeSearchWorkspace()
    if (wasShowingSearchResults) appState.returnToRecommendationsFeed()
    else appState.returnToRecommendations()
    dispatchPlaybackCanvas(PlaybackCanvasEvent.OpenBrowse)
  }

  fun openBrowseCanvas() {
    val appState = ViriViriApplication.appState
    immersiveBrowseSession = ImmersiveBrowseSessionReducer.open(appState.state.value.selected?.videoId)
    appState.closeSearchWorkspace()
    appState.returnToRecommendations()
    dispatchPlaybackCanvas(PlaybackCanvasEvent.OpenBrowse)
  }

  fun openSearchCanvas() {
    val appState = ViriViriApplication.appState
    immersiveBrowseSession = ImmersiveBrowseSessionReducer.open(appState.state.value.selected?.videoId)
    appState.openSearchWorkspace()
    dispatchPlaybackCanvas(PlaybackCanvasEvent.OpenBrowse)
  }

  private fun returnToPlaybackFromCenterContent() {
    // UX: selection hides the center layer immediately; the existing app state continues sole-player playback resolution.
    immersiveBrowseSession = ImmersiveBrowseSessionReducer.cancel(immersiveBrowseSession).session
    dispatchPlaybackCanvas(PlaybackCanvasEvent.OpenPlayback)
  }

  fun closeCenterContentFromNavigation() {
    // UX: ContentNavigation Back closes the current center route before touching the wider Workbench canvas.
    returnToPlaybackFromCenterContent()
  }

  /**
   * Collapses the Workbench. [source] only exists for the trace, so a logcat capture can tell
   * the two entry points apart: `outerDismiss` (scene hit layer) vs `centerPanelBlank`
   * (WORKBENCH_EMPTY blank space inside the centre panel).
   */
  private fun dismissWorkbenchFromCenterContent(source: String) {
    Log.i(WORKBENCH_TRACE_TAG, "dismissWorkbench source=$source")
    // UX: non-action center content clicks share the established canvas dismissal behavior.
    immersiveBrowseSession = ImmersiveBrowseSessionReducer.cancel(immersiveBrowseSession).session
    dispatchPlaybackCanvas(PlaybackCanvasEvent.Dismiss)
    animateControllerVisibility(false)
    // UX: reset the centre route at dismissal time so the next summon opens straight into the
    // video list instead of visibly switching route on open.
    //
    // The reset used to happen only via `applyPlaybackCanvasSlots`, whose condition
    // (`BROWSE !in slots && TRANSPORT in slots && selected != null`) cannot hold right after a
    // dismissal: `PlaybackCanvasEvent.Dismiss` moves the canvas to QUIET_WATCH, whose visible
    // slots are MEDIA_STAGE only. So the route stayed stale until the next summon flipped the
    // canvas back to PLAYBACK -- which is the route switch the user saw on open.
    //
    // Gated on a selected video on purpose: with no video there is no list to fall back to, so
    // the route must be left untouched. The `applyPlaybackCanvasSlots` call stays because it
    // serves the summon-time path (e.g. a route left dirty by video selection).
    if (ViriViriApplication.appState.state.value.selected != null) {
      ViriViriApplication.appState.openWorkbenchEmpty()
    }
  }

  private fun applyPlaybackCanvasSlots(visibleSlots: Set<PanelSlot>) {
    Log.d(WORKBENCH_TRACE_TAG, "applyPlaybackCanvasSlots slots=$visibleSlots")
    if (::immersiveWorkbenchHost.isInitialized) immersiveWorkbenchHost.applyCanvasSlots(visibleSlots)
    val appState = ViriViriApplication.appState
    if (PanelSlot.BROWSE !in visibleSlots && PanelSlot.TRANSPORT in visibleSlots && appState.state.value.selected != null) {
      appState.openWorkbenchEmpty()
    }
  }

  private fun applyWorkbenchModules(visibleModules: Set<WorkbenchModule>) {
    Log.d(WORKBENCH_TRACE_TAG, "applyWorkbenchModules modules=$visibleModules")
    if (!::spatialPanelVisibilityController.isInitialized) return
    val moduleEntities =
        mapOf(
            WorkbenchModule.NAVIGATION to Entity(R.id.mr_panel),
            WorkbenchModule.TRANSPORT to Entity(R.id.controls_id),
            WorkbenchModule.DETAIL_RAIL to Entity(R.id.video_selector_panel),
            WorkbenchModule.VIDEO_CONTEXT to Entity(R.id.mode_panel),
        )
    moduleEntities.forEach { (module, entity) ->
      spatialPanelVisibilityController.setVisible(
          module.toPanelSlot(),
          entity,
          shouldShowWorkbenchModule(module, visibleModules, hasWorkbenchDataSource),
      )
    }
    centerContentEntity?.let { entity ->
      spatialPanelVisibilityController.setVisible(
          PanelSlot.BROWSE,
          entity,
          WorkbenchModule.CENTER_CONTENT in visibleModules,
      )
    }
    stageBackdropEntity?.let { entity ->
      // The only translucent layer: dim MediaStage whenever a Workbench surface is present.
      spatialPanelVisibilityController.setVisible(
          PanelSlot.MEDIA_STAGE,
          entity,
          visibleModules.isNotEmpty(),
      )
    }
  }

  private fun WorkbenchModule.toPanelSlot(): PanelSlot =
      when (this) {
        WorkbenchModule.TRANSPORT, WorkbenchModule.SHORTS_ACTIONS -> PanelSlot.TRANSPORT
        WorkbenchModule.CENTER_CONTENT -> PanelSlot.BROWSE
        WorkbenchModule.DETAIL_RAIL, WorkbenchModule.VIDEO_CONTEXT -> PanelSlot.CONTEXT
        WorkbenchModule.NAVIGATION, WorkbenchModule.PLAYBACK_CONFIG -> PanelSlot.SYSTEM_TOOLBAR
      }

  fun togglePlay() {
    scene.playSound(audio, 1f)
    player.playWhenReady = !player.playWhenReady
  }

  public fun setVideo(video: Uri) {
    setUri = video
    ViriViriApplication.appState.playerSession.setMediaItem(MediaItem.fromUri(video))
  }

  public fun playVideo() {
    player.play()
  }

  public fun pauseVideo() {
    player.pause()
  }

  private fun syncPlaybackSpeedLabel() {
    speedButton.thenAccept { it.text = PlaybackSpeedControl.label(player.playbackParameters.speed) }
  }

  private fun syncPlaybackVolumeLabel() {
    volumeButton.thenAccept { it.text = PlaybackVolumeControl.compactLabel(player.volume) }
  }

  private fun syncPlaybackQualityLabel(quality: PlaybackQuality) {
    qualityButton.thenAccept { it.text = quality.label }
  }

  /** The existing native/app-state emission is the only owner of stage tuning parameters. */
  private fun applyPlaybackStageParameters(
      displayRatio: PlaybackDisplayRatio,
      stageScale: Float,
      curvature: PlaybackStageCurvature,
  ) {
    if (stageClosed) return
    val target = SpatialVideoAspectProbeTarget.from(displayRatio)
    if (spatialVideoAspectProbeState.appliedTarget != target) {
      spatialVideoAspectProbeState = spatialVideoAspectProbeState.copy(pendingTarget = target, appliedTarget = target)
    }
    playbackStageController.setParameters(PlaybackStageParameters(stageScale, target.displayAspectRatio, curvature))
  }

  /**
   * Debug escape hatch: puts every stage layer back to [PlaybackStageCurvature.Flat].
   *
   * Routed through [ViriViriAppState.setPlaybackCurvature] so the PERSISTED value is overwritten
   * too. A curvature bad enough to swallow the stage hit survives a relaunch otherwise, which left
   * clearing app data as the only recovery.
   */
  private fun resetPlaybackCurvatureToFlat() {
    ViriViriApplication.appState.setPlaybackCurvature(PlaybackStageCurvature.Flat)
    val state = ViriViriApplication.appState.state.value
    applyPlaybackStageParameters(state.playbackDisplayRatio, state.playbackStageScale, PlaybackStageCurvature.Flat)
    Log.i("ViriViriCurve", "curvature reset to flat for every stage layer")
  }

  /** Debug-rail formatting for a curvature: "flat", or the cylinder radius in metres. */
  private fun curvatureLabel(curvature: PlaybackStageCurvature): String =
      curvature.radiusOrNull()?.let { "r=%.2f m".format(it) } ?: "flat"

  /**
   * Builds a playback-control menu with the application's dark popup theme.
   *
   * The Activities use a bare `android:Theme` (Theme.Transparent), so an unwrapped [PopupMenu]
   * inherited the platform default (light) popup and clashed with the surrounding dark Compose
   * panels. Wrapping only the context changes the popup's appearance: item order, item ids,
   * checkable groups and click handling are all unchanged.
   */
  private fun darkPopupMenu(anchor: View): PopupMenu =
      PopupMenu(ContextThemeWrapper(this, R.style.Theme_ViriViri_DarkPopup), anchor)

  /** Product settings moved off Context; debug actions stay behind the existing wrist entry. */
  private fun showStageSettingsMenu(anchor: View, includeDebug: Boolean = false) {
    val appState = ViriViriApplication.appState
    val current = appState.state.value
    darkPopupMenu(anchor).apply {
      val ratios = menu.addSubMenu("显示比例")
      PlaybackDisplayRatio.entries.forEachIndexed { index, ratio ->
        ratios.add(1, 100 + index, index, ratio.label).isChecked = ratio == current.playbackDisplayRatio
      }
      ratios.setGroupCheckable(1, true, true)
      val sizes = menu.addSubMenu("画布大小")
      PlaybackCanvasSize.entries.forEachIndexed { index, size ->
        sizes.add(2, 200 + index, index, size.label).isChecked = size == current.playbackCanvasSize
      }
      sizes.setGroupCheckable(2, true, true)
      menu.add(0, 300, 0, "重置舞台高度")
      menu.add(0, 301, 1, "恢复平面")
      menu.add(0, 302, 2, "打开 2D 窗口")
      if (includeDebug && BuildConfig.DEBUG) {
        menu.add(0, 303, 3, "弹幕 Z：${stageDanmakuBaseLocalZ}")
        menu.add(0, 304, 4, "合成顺序：${cycleBackdropOrderCandidateLabel()}")
        menu.add(0, 305, 5, "舞台遮挡保护：$stagePrimaryActionOcclusionGuardEnabled")
      }
      setOnMenuItemClickListener { item ->
        when {
          item.itemId in 100 until 100 + PlaybackDisplayRatio.entries.size ->
              appState.selectPlaybackDisplayRatio(PlaybackDisplayRatio.entries[item.itemId - 100])
          item.itemId in 200 until 200 + PlaybackCanvasSize.entries.size ->
              appState.selectPlaybackCanvasSize(PlaybackCanvasSize.entries[item.itemId - 200])
          item.itemId == 300 -> resetStageYToDefault()
          item.itemId == 301 -> resetPlaybackCurvatureToFlat()
          item.itemId == 302 -> {
            appState.playerSession.beginOutputHandoff()
            launchPanelModeInHome()
          }
          item.itemId == 303 -> {
            danmakuBaseLocalZIndex = (danmakuBaseLocalZIndex + 1) % DANMAKU_BASE_LOCAL_Z_CANDIDATES.size
            playbackStageController.setDanmakuDepth(stageDanmakuBaseLocalZ)
          }
          item.itemId == 304 -> cycleBackdropOrderCandidate()
          item.itemId == 305 -> toggleStagePrimaryActionOcclusionGuard()
          else -> return@setOnMenuItemClickListener false
        }
        true
      }
      show()
    }
  }

  private fun showPlaybackDisplayRatioMenu(anchor: View) {
    darkPopupMenu(anchor).apply {
      menu.setGroupCheckable(0, true, true)
      val selectedRatio = ViriViriApplication.appState.state.value.playbackDisplayRatio
      PlaybackDisplayRatio.entries.forEachIndexed { index, displayRatio ->
        menu.add(0, index, index, displayRatio.label).isChecked = displayRatio == selectedRatio
      }
      setOnMenuItemClickListener { item ->
        val displayRatio = PlaybackDisplayRatio.entries.getOrNull(item.itemId)
            ?: return@setOnMenuItemClickListener false
        ViriViriApplication.appState.selectPlaybackDisplayRatio(displayRatio)
        true
      }
      show()
    }
  }

  private fun showPlaybackCanvasSizeMenu(anchor: View) {
    darkPopupMenu(anchor).apply {
      menu.setGroupCheckable(0, true, true)
      val selectedSize = ViriViriApplication.appState.state.value.playbackCanvasSize
      PlaybackCanvasSize.entries.forEachIndexed { index, canvasSize ->
        menu.add(0, index, index, canvasSize.label).isChecked = canvasSize == selectedSize
      }
      setOnMenuItemClickListener { item ->
        val canvasSize = PlaybackCanvasSize.entries.getOrNull(item.itemId)
            ?: return@setOnMenuItemClickListener false
        ViriViriApplication.appState.selectPlaybackCanvasSize(canvasSize)
        true
      }
      show()
    }
  }

  private fun showPlaybackQualityMenu(anchor: View) {
    darkPopupMenu(anchor).apply {
      menu.setGroupCheckable(0, true, true)
      val selectedQuality = ViriViriApplication.appState.state.value.playbackQuality
      PlaybackQuality.entries.forEachIndexed { index, quality ->
        menu.add(0, index, index, quality.label).isChecked = quality == selectedQuality
      }
      setOnMenuItemClickListener { item ->
        val quality = PlaybackQuality.entries.getOrNull(item.itemId)
            ?: return@setOnMenuItemClickListener false
        ViriViriApplication.appState.selectPlaybackQuality(quality)
        true
      }
      show()
    }
  }

  private fun showPlaybackVolumeMenu(anchor: View) {
    darkPopupMenu(anchor).apply {
      menu.setGroupCheckable(0, true, true)
      PlaybackVolumeControl.supportedVolumes.forEachIndexed { index, volume ->
        menu.add(0, index, index, PlaybackVolumeControl.label(volume)).isChecked =
            volume == PlaybackVolumeControl.normalizedForDisplay(player.volume)
      }
      setOnMenuItemClickListener { item: MenuItem ->
        val volume = PlaybackVolumeControl.supportedVolumes.getOrNull(item.itemId)
            ?: return@setOnMenuItemClickListener false
        player.volume = volume
        true
      }
      show()
    }
  }

  private fun showSpatialVideoAspectTargetMenu(anchor: View) {
    darkPopupMenu(anchor).apply {
      SpatialVideoAspectProbeTarget.entries.forEachIndexed { index, target ->
        menu.add(0, index, index, target.label).isChecked = target == spatialVideoAspectProbeState.pendingTarget
      }
      menu.setGroupCheckable(0, true, true)
      setOnMenuItemClickListener { item ->
        val target = SpatialVideoAspectProbeTarget.entries.getOrNull(item.itemId)
            ?: return@setOnMenuItemClickListener false
        spatialVideoAspectProbeState = SpatialVideoAspectProbeReducer.selectTarget(spatialVideoAspectProbeState, target)
            true
      }
      show()
    }
  }

  private fun applySpatialVideoAspectProbe() {
    ViriViriApplication.appState.selectPlaybackDisplayRatio(
        spatialVideoAspectProbeState.pendingTarget.displayRatio
    )
  }

  private fun currentPlaybackStageGeometry(): PlaybackStageGeometry = playbackStageController.layout.geometry

  /** Only panel identity resolution lives in the host; the controller owns readiness and replay. */
  private fun resolveStagePanel(role: PlaybackStageLayer): PanelSceneObject? {
    if (role == PlaybackStageLayer.VIDEO) return spatialVideoPanelSceneObject
    val entity = when (role) {
      PlaybackStageLayer.BACKDROP -> stageBackdropEntity
      PlaybackStageLayer.DANMAKU -> danmakuOverlayEntity
      PlaybackStageLayer.VIDEO -> videoSurfaceEntity
    } ?: return null
    return systemManager.findSystem<SceneObjectSystem>().getSceneObject(entity)?.getNow(null) as? PanelSceneObject
  }

  private fun requestStageGeometry() = playbackStageController.invalidate()

  private fun updateSpatialVideoContentQuad(
      videoWidth: Int,
      videoHeight: Int,
      pixelWidthHeightRatio: Float,
  ) {
    playbackStageController.setVideoSize(videoWidth, videoHeight, pixelWidthHeightRatio)
  }

  private fun startTransportTimelineUpdates() {
    if (transportTimelineUpdatesStarted) return
    transportTimelineUpdatesStarted = true
    canvasHandler.post(transportTimelineUpdater)
  }

  private fun syncTransportTimeline() {
    val timeline =
        immersiveTransportTimeline(
            playerPositionMs = player.currentPosition,
            playerDurationMs = player.duration,
            dragPositionMs = seekDragPositionMs.takeIf { isSeeking },
        )
    seekBar.thenAccept { seek ->
      seek.isEnabled = timeline.canSeek
      seek.max = timeline.maxMs
      if (!isSeeking) seek.progress = timeline.positionMs
    }
    elapsedTime.thenAccept { it.text = timeline.elapsedLabel }
    durationTime.thenAccept { it.text = timeline.durationLabel }
  }

  private fun showPlaybackSpeedMenu(anchor: View) {
    darkPopupMenu(anchor).apply {
      menu.setGroupCheckable(0, true, true)
      PlaybackSpeedControl.supportedSpeeds.forEachIndexed { index, speed ->
        menu.add(0, index, index, PlaybackSpeedControl.label(speed)).isChecked =
            speed == PlaybackSpeedControl.normalizedForDisplay(player.playbackParameters.speed)
      }
      setOnMenuItemClickListener { item: MenuItem ->
        val speed = PlaybackSpeedControl.supportedSpeeds.getOrNull(item.itemId) ?: return@setOnMenuItemClickListener false
        player.playbackParameters = player.playbackParameters.withSpeed(speed)
        true
      }
      show()
    }
  }

  private fun syncPlaybackControls() {
    val state = immersivePlaybackControlState(player.playWhenReady, player.isPlaying)
    isPlaying = state.isActuallyPlaying
    playPauseButton.thenAccept { button ->
      button.setCompoundDrawablesWithIntrinsicBounds(
          0,
          if (state.showPauseIcon) R.drawable.pause else R.drawable.play,
          0,
          0,
      )
    }
    if (state.isActuallyPlaying) {
      dimLights()
      resetControllerFadeOutTimer()
    } else {
      brightenLights()
      animateControllerVisibility(true)
      if (::immersivePlaybackCanvasHost.isInitialized) immersivePlaybackCanvasHost.applyCurrentState()
    }
  }

  public fun dimLights() {
    targetLights = 0.0f
  }

  public fun brightenLights() {
    targetLights = 1.0f
  }

  // Movies List Panel
  private fun selectorPanelSettings(): UIPanelSettings =
      UIPanelSettings(
          // UX: the left Detail rail mirrors the right context rail width so the
          // two are vertically symmetric; its body scrolls within the shorter height.
          shape = QuadShapeOptions(width = layout.railWidth, height = layout.leftRailHeight),
          display = DpDisplayOptions(width = 361f, height = 464f, dpi = 800),
          rendering = uiPanelRenderOptions(),
          input =
              // want to disable left hand pinch so we can drag the panel around with hands
              PanelInputOptions(
                  ButtonBits.ButtonA or ButtonBits.ButtonTriggerL or ButtonBits.ButtonTriggerR
              ),
      )

  private fun selectorPanelRegistration(): PanelRegistration {
    return ActivityPanelRegistration(
        R.id.video_selector_panel,
        classIdCreator = { MoviePanel::class.java },
        settingsCreator = { selectorPanelSettings() },
    )
  }

  // Passthrough (MR) panel
  private fun mrPanelSettings(): UIPanelSettings =
      UIPanelSettings(
          // UX: top-stack keeps GlobalNavigation and ContentNavigation in one existing Spatial panel.
          shape = QuadShapeOptions(width = 1.24f, height = 0.30f),
          display = DpDisplayOptions(width = 520f, height = 128f, dpi = 600),
          rendering = uiPanelRenderOptions(),
      )

  private fun mrPanelRegistration(): PanelRegistration {
    return IntentPanelRegistration(
        registrationId = R.id.mr_panel,
        intentCreator = {
          Intent(spatialContext, MRPanel::class.java).apply {
            putExtra("isMrMode", scene.isSystemPassthroughEnabled().toString())
          }
        },
        settingsCreator = { mrPanelSettings() },
    )
  }

  public fun setMrMode(isMrMode: Boolean) {
    if (isMrMode) {
      environmentGLXF?.setComponent(Visible(false))
      skydome?.setComponent(Visible(false))
    } else {
      environmentGLXF?.setComponent(Visible(true))
      skydome?.setComponent(Visible(true))
    }

    // Video is a render carrier, not an ISDK interactive panel. Publishing dimensions here
    // reintroduces the curvature-dependent collider retired by the geometric stage input path.

    scene.enablePassthrough(isMrMode)
    // Locomotion is never re-enabled on a mode switch; see the scene-ready disable for why.
    avatarSystem.setShowControllers(!isMrMode)
    avatarSystem.setShowHands(!isMrMode)
    inMrMode = isMrMode
  }

  companion object {
    private const val PRODUCTION_GEOMETRY_INTERVAL_MS = 33L
    const val TAG = "SpatialVideoSampleActivity"
    const val WORKBENCH_TRACE_TAG = "ViriViriWorkbench"

    /** Fixed tag for the DEBUG-only backdrop-order candidates. */
    const val BACKDROP_ORDER_TAG = "ViriViriBackdrop"

    const val EXTRA_REATTACH_IMMERSIVE_OUTPUT =
        "com.m0e_n00b.viriviri.extra.REATTACH_IMMERSIVE_OUTPUT"
    lateinit var appContext: Context
    lateinit var appPackageName: String

    const val LIGHTS_UP_SCALE: Float = 1.0f
    const val LIGHTS_DOWN_SCALE: Float = 0.25f
    const val TRANSPORT_FADE_DURATION_MS: Long = 200L
    const val TRANSPORT_TIMELINE_UPDATE_INTERVAL_MS: Long = 500L
    // Workbench spatial tuning lives in WorkbenchLayoutConfig (bind it to future
    // settings presets / preferences instead of hardcoding positions here).
    const val WRIST_DEBUG_PANEL_WIDTH: Float = 0.14f
    const val WRIST_DEBUG_PANEL_HEIGHT: Float = 0.05f

    /**
     * Candidates for the danmaku layer's authored local Z, stepped by the debug rail. This is only its
     * authored separation from the video/backdrop; the shared radius correction is applied identically
     * to every carrier by [PlaybackStagePanelRenderer].
     */
    val DANMAKU_BASE_LOCAL_Z_CANDIDATES: FloatArray = floatArrayOf(-0.01f, 0.01f, -0.05f, 0.05f)

    /** Depth guard for world-pose resolution over the `TransformParent` chain. */
    const val MAX_PARENT_DEPTH: Int = 8

    /**
     * Compositor layer order: video (1), backdrop (2), other UI (3).
     * Each panel owns its own LayerConfig; there is no shared compositor stage group.
     * These values sort compositor layers only. Panel shape and carrier transforms
     * independently own physical dimensions, curvature and placement.
     */
    const val VIDEO_LAYER_Z_INDEX: Int = 1

    /** DEBUG-only backdrop-order candidates; see [uiPanelRenderOptions] for why they exist. */
    const val BACKDROP_ORDER_BASELINE: Int = 0
    const val BACKDROP_ORDER_ZINDEX: Int = 1
    const val BACKDROP_ORDER_BACKDROP_MESH: Int = 2
    const val BACKDROP_ORDER_ALL_UI_MESH: Int = 3
    const val BACKDROP_ORDER_CANDIDATE_COUNT: Int = 4

    /** Delay before the startup layer-ordering pass, so the panel scene objects exist. */
    const val LAYER_ORDER_APPLY_DELAY_MS: Long = 900L

    val BACKDROP_ORDER_CANDIDATE_LABELS: Array<String> =
        arrayOf("BASELINE", "ZINDEX", "BACKDROP_MESH", "ALL_UI_MESH")

    /**
     * `LayerConfig.zIndex` used by the ZINDEX candidate. `LayerConfig()` defaults the field to 0, so the
     * ordering is written explicitly for every role instead of letting two roles share that default:
     * video [VIDEO_LAYER_Z_INDEX] (1) < backdrop [BACKDROP_LAYER_Z_INDEX] < other UI panels
     * [UI_PANEL_LAYER_Z_INDEX].
     */
    const val BACKDROP_LAYER_Z_INDEX: Int = 2

    const val UI_PANEL_LAYER_Z_INDEX: Int = 3

    const val MR_SCREEN_WIDTH: Float = 16.0f / 10.0f
    const val MR_SCREEN_HEIGHT: Float = 9.0f / 10.0f
    const val VR_SCREEN_RATIO: Float = 2.5f

    // Visual grab bar under the video stage (indicates the ISDK bottom-edge grab zone).
    const val GRAB_BAR_WIDTH_METERS: Float = 1.0f
    const val GRAB_BAR_HEIGHT_METERS: Float = 0.08f
    /** Bar world Z offset toward the user so the grab ray hits the bar, not the stage surface. */
    const val GRAB_BAR_FRONT_OFFSET: Float = 0.06f
    /** Idle (non-hovered) grab-bar alpha — fades up on hover. */
    const val GRAB_BAR_IDLE_ALPHA: Float = 0.45f
    /** Extra outward expansion of the grab hit colliders (m), so the whole strip is grabbable. */
    const val GRAB_BAR_HIT_OUTSET: Float = 0.02f
    /** Depth (m) of the whole-strip box collider on the anchor. */
    const val GRAB_BAR_HIT_DEPTH_METERS: Float = 0.05f
    /** Forward offset (m, panel-local, toward the user) of the box collider vs the visual bar. */
    const val GRAB_BAR_HIT_FORWARD_OFFSET: Float = 0.0f
    /** Bar vertical gap below the stage bottom edge (stage local, negative = down). */
    const val GRAB_BAR_BOTTOM_GAP_METERS: Float = 0.02f

    // spawns debug menu if true
    const val DEBUG: Boolean = false
  }
}

class CustomRenderersFactory : DefaultRenderersFactory {
  private val context_: Context
  private val audioSink_: AudioSink

  constructor(context: Context, audioSink: AudioSink) : super(context) {
    context_ = context
    audioSink_ = audioSink
  }

  override fun createRenderers(
      eventHandler: Handler,
      videoRendererEventListener: VideoRendererEventListener,
      audioRendererEventListener: AudioRendererEventListener,
      textRendererOutput: TextOutput,
      metadataRendererOutput: MetadataOutput,
  ): Array<Renderer> {
    val renderers =
        super.createRenderers(
            eventHandler,
            videoRendererEventListener,
            audioRendererEventListener,
            textRendererOutput,
            metadataRendererOutput,
        )
    var rendererList = renderers.toMutableList()
    val audioRenderer = MediaCodecAudioRenderer(
        context_,
        getCodecAdapterFactory(),
        MediaCodecSelector.DEFAULT,
        false,
        eventHandler,
        audioRendererEventListener,
        audioSink_,
    )
    rendererList.add(0, audioRenderer)
    return rendererList.toTypedArray()
  }
}
