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
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
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
import com.meta.spatial.isdk.updateIsdkComponentProperties
import com.meta.spatial.ovrmetrics.OVRMetricsDataModel
import com.meta.spatial.ovrmetrics.OVRMetricsFeature
import com.meta.spatial.runtime.AlphaMode
import com.meta.spatial.runtime.ButtonBits
import com.meta.spatial.runtime.HitInfo
import com.meta.spatial.runtime.InputListener
import com.meta.spatial.runtime.PanelSceneObject
import com.meta.spatial.runtime.ReferenceSpace
import com.meta.spatial.runtime.SceneAudioAsset
import com.meta.spatial.runtime.SceneMaterial
import com.meta.spatial.runtime.SceneMesh
import com.meta.spatial.runtime.SceneObject
import com.meta.spatial.runtime.SceneTexture
import com.meta.spatial.runtime.SessionState
import com.meta.spatial.runtime.StereoMode
import com.meta.spatial.runtime.TriangleMesh
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
import com.meta.spatial.toolkit.PanelStyleOptions
import com.meta.spatial.toolkit.PixelDisplayOptions
import com.meta.spatial.toolkit.QuadShapeOptions
import com.meta.spatial.toolkit.Scale
import com.meta.spatial.toolkit.SceneObjectSystem
import com.meta.spatial.toolkit.Transform
import com.meta.spatial.toolkit.TransformParent
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

/**
 * Subdivision of the video surface while the video layer is curved. The flat case stays a single
 * quad, so its mesh remains exactly the one the panel has always used.
 */
private const val CURVED_VIDEO_COLUMNS = 32
private const val CURVED_VIDEO_ROWS = 18

/** Geometry of the flat drop-shadow footprint appended to the video surface. */
private const val VIDEO_SHADOW_VERTEX_COUNT = 4
private const val VIDEO_SHADOW_INDEX_COUNT = 6
private const val VIDEO_SHADOW_DEPTH_METERS = 0.1f
private const val VIDEO_SHADOW_ROUNDING_METERS = 0.075f

/** Stereo mode of the video panel. Its materials must agree with the panel render options. */
private val videoPanelStereoMode = StereoMode.None

// default activity
class SpatialVideoSampleActivity : AppSystemActivity() {

  val player: ExoPlayer
    get() = ViriViriApplication.appState.playerSession.player
  lateinit var controllerView: View
  private var transportOverlayState = ImmersiveTransportOverlayState()
  private val canvasHandler = Handler(Looper.getMainLooper())
  private val transportTimelineUpdater =
      object : Runnable {
        override fun run() {
          syncTransportTimeline()
          canvasHandler.postDelayed(this, TRANSPORT_TIMELINE_UPDATE_INTERVAL_MS)
        }
      }
  private var transportTimelineUpdatesStarted = false
  private var seekDragPositionMs: Long? = null
  private var spatialVideoTriangleMesh: TriangleMesh? = null
  private var spatialVideoPanelSceneObject: PanelSceneObject? = null
  /** Video panel instance whose geometry has already been re-asserted after creation. */
  private var reassertedVideoPanelSceneObject: PanelSceneObject? = null
  /** Last shape handed to the video panel; keeps the debug log from repeating itself. */
  private var lastLoggedVideoPanelShape: String? = null
  /** Set once the restored (persisted) curvatures have been logged, so the log is readable. */
  private var loggedRestoredCurvature = false
  /**
   * Authored local Z of each stage overlay inside the stage root, before the curvature offset. Kept
   * as constants because the offset is recomputed from the base on every reshape — reading the
   * entity's current Z instead would accumulate.
   */
  private val stageBackdropBaseLocalZ = 0.01f

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
  private val videoSurfaceBaseLocalZ = 0f

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
  private var lastAspectDiagnostic: SpatialVideoAspectDiagnostic? = null
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
  private var outerDismissEntity: Entity? = null
  private var outerDismissInputAttached = false
  private var suppressOuterDismissUntilMs = 0L
  private var hasWorkbenchDataSource = false
  private var appliedStageScale: Float? = null
  /** True while a stage-overlay reshape is waiting for its PanelSceneObject to exist. */
  private var stageOverlaysPendingReshape = false
  // Per-layer curvature is independent: the video, the danmaku layer and the backdrop dim layer
  // each bend on their own radius. Scale stays shared across all three layers.
  private var appliedVideoCurvature: PlaybackStageCurvature = PlaybackStageCurvature.Flat
  private var appliedDanmakuCurvature: PlaybackStageCurvature = PlaybackStageCurvature.Flat
  private var appliedBackdropCurvature: PlaybackStageCurvature = PlaybackStageCurvature.Flat
  private lateinit var spatialPanelVisibilityController: SpatialPanelVisibilityController
  private lateinit var immersivePlaybackCanvasHost: ImmersivePlaybackCanvasHost
  lateinit var audio: SceneAudioAsset
  var seekBar: CompletableFuture<SeekBar> = CompletableFuture<SeekBar>()
  var elapsedTime: CompletableFuture<TextView> = CompletableFuture<TextView>()
  var durationTime: CompletableFuture<TextView> = CompletableFuture<TextView>()
  var currentMediaTitle: CompletableFuture<TextView> = CompletableFuture<TextView>()
  var currentMediaDetail: CompletableFuture<TextView> = CompletableFuture<TextView>()
  var retryMediaButton: CompletableFuture<Button> = CompletableFuture<Button>()
  var qualityButton: CompletableFuture<Button> = CompletableFuture<Button>()
  var displayRatioButton: CompletableFuture<Button> = CompletableFuture<Button>()
  var canvasSizeButton: CompletableFuture<Button> = CompletableFuture<Button>()
  var debugAspectDetail: CompletableFuture<TextView> = CompletableFuture<TextView>()
  var debugAspectTargetButton: CompletableFuture<Button> = CompletableFuture<Button>()
  var debugAspectPlanButton: CompletableFuture<Button> = CompletableFuture<Button>()
  var debugAspectApplyButton: CompletableFuture<Button> = CompletableFuture<Button>()
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
            updateImmersiveMediaStatus(
                selected = appState.selected,
                error = appState.error.takeIf { appState.destination == ViriViriDestination.VIEWER },
                isResolvingPlayback = appState.isResolvingPlayback,
            )
            updateImmersiveRetryAvailability(
                destination = appState.destination,
                selected = appState.selected,
                error = appState.error,
                isResolvingPlayback = appState.isResolvingPlayback,
            )
            syncPlaybackQualityLabel(appState.playbackQuality)
            syncPlaybackDisplayRatioLabel(appState.playbackDisplayRatio)
            syncPlaybackCanvasSizeLabel(appState.playbackCanvasSize)
            applyPlaybackDisplayRatio(appState.playbackDisplayRatio)
            applyPlaybackStageScale(appState.playbackStageScale)
            applyPlaybackVideoCurvature(appState.playbackVideoCurvature)
            applyPlaybackDanmakuCurvature(appState.playbackDanmakuCurvature)
            applyPlaybackBackdropCurvature(appState.playbackBackdropCurvature)
            if (!loggedRestoredCurvature) {
              loggedRestoredCurvature = true
              // Surfaces stale preferences: a layer reading "r=6.00 m" for the 6 m flatmost radius can
              // only have come from a persisted value, never from the thumbstick.
              Log.i(
                  "ViriViriCurve",
                  "restored curvature video=${curvatureLabel(appState.playbackVideoCurvature)} " +
                      "danmaku=${curvatureLabel(appState.playbackDanmakuCurvature)} " +
                      "backdrop=${curvatureLabel(appState.playbackBackdropCurvature)}",
              )
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
      canvasHandler.post { attachOuterDismissInput() }
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
            stageInputEnabled = ::isStageInputEnabled,
            // Only used if the stage pose cannot be read; keeps the stage controllable either way.
            fallbackStageEntity = { videoSurfaceEntity ?: Entity(R.id.spatialized_video_panel) },
            onScaleDelta = ViriViriApplication.appState::adjustPlaybackStageScale,
            onCurvatureDelta = ViriViriApplication.appState::adjustPlaybackCurvature,
            onStageAction = ::onStagePrimaryAction,
            onInteractionFinished = {},
        )
    )
    // Escape hatch: the stage is the only other way into the workbench, and its hit geometry moves with
    // the curvature offset, so a bad curvature would otherwise lock the user out of the reset.
    if (BuildConfig.DEBUG) {
      systemManager.registerSystem(
          AnalogWorkbenchSummonSystem(onSummon = ::summonWorkbenchFromController)
      )
    }
    // Panel scene objects are created a frame after their entities, so an overlay reshape requested
    // while creating those panels is replayed here until it lands.
    systemManager.registerSystem(StageOverlayReshapeSystem { flushPendingStageOverlayReshape() })
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

  private fun attachOuterDismissInput() {
    if (outerDismissInputAttached) return
    val entity = outerDismissEntity ?: return
    entity.setComponent(Hittable())
    // UX: deliberately NOT hidden. This is the outside-the-Workbench dismiss hit layer, and
    // `Visible(false)` removes an entity from hit testing -- which is exactly why it could never
    // be clicked (confirmed on device 2026-10-06: zero outerDismiss onClick calls in 19 s).
    // Invisibility is the material's job instead: app/scenes/WorkbenchOuterDismiss uses
    // alphaMode Blend with an alpha-0 baseColorFactor, so the 9x6 m slab renders nothing while
    // staying present for the controller ray.
    systemManager.findSystem<SceneObjectSystem>().getSceneObject(entity)?.thenAccept { sceneObject ->
      sceneObject.addInputListener(
          object : InputListener {
            override fun onClick(
                receiver: SceneObject,
                hitInfo: HitInfo,
                sourceOfInput: Entity,
            ) {
              // Trace: proves whether the scene-authored hit layer is reached at all. If clicking
              // blank space outside the Workbench produces no "outerDismiss click" line, the
              // WorkbenchOuterDismiss geometry/orientation is at fault, not this handler.
              val workbenchVisible =
                  ::immersiveWorkbenchHost.isInitialized && immersiveWorkbenchHost.state.visible
              val suppressed = SystemClock.uptimeMillis() < suppressOuterDismissUntilMs
              Log.i(
                  WORKBENCH_TRACE_TAG,
                  "outerDismiss click workbenchVisible=$workbenchVisible suppressed=$suppressed",
              )
              if (suppressed) {
                suppressOuterDismissUntilMs = 0L
                return
              }
              if (workbenchVisible) {
                dismissWorkbenchFromCenterContent("outerDismiss")
              }
            }
          }
      )
      outerDismissInputAttached = true
    }
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

  private fun currentStageScale(): Float = appliedStageScale ?: PlaybackCanvasSize.STANDARD.scale

  /**
   * Stage footprint in metres with the shared stage scale already BAKED IN.
   *
   * k17 root cause: the video is a manually built [PanelSceneObject] whose mesh ignored the entity
   * `Scale` component, while the danmaku/backdrop overlays are standard panels that honoured it —
   * that asymmetry is why the canvas loaded at the wrong size. The SDK re-runs the panel shape
   * builder on every [PanelSceneObject.reshape] (confirmed in `PanelShape` bytecode), so we bake the
   * scale into the shape dimensions instead of relying on entity `Scale`. This is the size source
   * for the video layer and for the danmaku / backdrop overlays alike (see [applyOverlayStageShape]).
   */
  private fun scaledStageWidth(): Float = MR_SCREEN_WIDTH * currentStageScale()

  private fun scaledStageHeight(): Float = MR_SCREEN_HEIGHT * currentStageScale()

  /**
   * Video layer shape: quad while the video layer is [PlaybackStageCurvature.Flat], a compositor
   * cylinder while it is a [PlaybackStageCurvature.Cylinder].
   *
   * The compositor owns the video pixels: every media panel carries a `LayerConfig`
   * (`MediaPanelRenderOptions.applyTo` always calls `setLayerConfig`), so the visible surface is the
   * shape handed to the layer here - never our own mesh. A quad shape keeps the video flat no matter
   * what [createVideoSceneMesh] builds, which is why a small radius used to change nothing.
   *
   * Geometry follows `XR_KHR_composition_layer_cylinder`, which the SDK feeds with
   * `radius = max(radius, width / 2pi)`, `centralAngle = width / radius` and
   * `aspectRatio = width / height`: the layer pose is the centre point of the visible section of the
   * cylinder, so the surface stays anchored on the stage centre while its edges wrap toward the
   * viewer (the stage local -Z side). That is why no positional compensation is applied here.
   *
   * [createVideoSceneMesh] stays the owner of the panel mesh (drop shadow + hole punch).
   */
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
   * EXPERIMENT: sets the depth on the LayerConfig itself, not on the render options.
   *
   * `MediaPanelRenderOptions(zIndex = ...)` was measured to change nothing (build F158ABB7). That field
   * and `LayerConfig.zIndex` are different objects, and `LayerConfig` is the one the compositor is
   * actually handed via `setLayerConfig`, so this is the last knob that could order the layer against
   * the scene. `MediaPanelRenderOptions.applyTo` writes that config during `toPanelConfigOptions()`,
   * i.e. BEFORE this `apply { }` block runs, so mutating it here wins.
   *
   * If this is ignored too, then layer ordering is simply not ours to control and the display path
   * itself has to change — see Stage 3k in docs/research/stage-video-mesh-curvature.md.
   */
  private fun applyVideoLayerDepth(config: com.meta.spatial.runtime.PanelConfigOptions) {
    config.layerConfig?.zIndex = VIDEO_LAYER_Z_INDEX
  }

  /**
   * Builds the video layer mesh for a [width] x [height] surface, with the video layer current
   * curvature baked into the vertices.
   *
   * This is the only owner of the video layer geometry, and it has to be handed to the SDK both when
   * the panel is created and on every [PanelSceneObject.reshape]: a reshape rebuilds the panel shape
   * from a fresh config, and a config without a `sceneMeshCreator` makes the SDK substitute its own
   * generated shape mesh (see [reshapeSpatialVideoPanel]).
   *
   * Layout, identical to the original hand-built quad while the surface is flat:
   * - the stage surface from [CurvedStageMeshBuilder] (one quad when flat, a
   *   [CURVED_VIDEO_COLUMNS] x [CURVED_VIDEO_ROWS] grid when curved), then
   * - four flat vertices for the drop shadow under the stage bottom.
   */
  private fun createVideoSceneMesh(
      texture: SceneTexture,
      width: Float,
      height: Float,
  ): SceneMesh {
    val isCurved = appliedVideoCurvature is PlaybackStageCurvature.Cylinder
    val columns = if (isCurved) CURVED_VIDEO_COLUMNS else 1
    val rows = if (isCurved) CURVED_VIDEO_ROWS else 1
    val surface =
        CurvedStageMeshBuilder.build(
            widthMeters = width,
            heightMeters = height,
            geometry = appliedVideoCurvature.toStageGeometry(),
            columns = columns,
            rows = rows,
        )
    // The flat quad already ships both windings in its original triangle order; the builder emits
    // front faces only for a grid, so the mirrored triangles are generated here.
    val surfaceIndices = if (isCurved) curvedSurfaceIndices(columns, rows) else surface.indices
    val shadowVertexBase = surface.vertexCount
    val vertexCount = shadowVertexBase + VIDEO_SHADOW_VERTEX_COUNT
    val halfWidth = width / 2f
    val halfHeight = height / 2f
    val halfDepth = VIDEO_SHADOW_DEPTH_METERS
    val rounding = VIDEO_SHADOW_ROUNDING_METERS
    val shadowUvs = FloatArray(VIDEO_SHADOW_VERTEX_COUNT * 2)
    for (index in shadowUvs.indices step 2) {
      shadowUvs[index] = halfWidth - rounding
      shadowUvs[index + 1] = halfDepth - rounding
    }
    val indices =
        surfaceIndices +
            intArrayOf(
                shadowVertexBase,
                shadowVertexBase + 2,
                shadowVertexBase + 1,
                shadowVertexBase,
                shadowVertexBase + 3,
                shadowVertexBase + 2,
            )
    val triMesh =
        TriangleMesh(
            vertexCount,
            indices.size,
            videoMeshMaterialIndexRanges(surfaceIndices.size / 2),
            arrayOf(
                // The video panel is created and reshaped with
                // MediaPanelRenderOptions(stereoMode = StereoMode.None), so the hand-built materials
                // reuse the same stereo mode.
                SceneMaterial(texture, AlphaMode.TRANSLUCENT, "data/shaders/spatial/reflect")
                    .apply {
                      setStereoMode(videoPanelStereoMode)
                      setUnlit(true)
                    },
                SceneMaterial(texture, AlphaMode.TRANSLUCENT, "data/shaders/spatial/shadow")
                    .apply { setUnlit(true) },
                SceneMaterial(texture, AlphaMode.HOLE_PUNCH, SceneMaterial.HOLE_PUNCH_SHADER)
                    .apply {
                      setStereoMode(videoPanelStereoMode)
                      setUnlit(true)
                    },
            ),
        )
    // The entity is displaced by the curvature offset so the COMPOSITOR surface lands back on the
    // authored plane. This mesh rides that same entity, so without the inverse it is dragged a whole
    // radius out of the layer stack — which is exactly what put it in the pointer ray's path below
    // r = 1.75 m (its world z is anchorZ - r, and at anchorZ 2 m that crosses the controller at 0.25 m)
    // and what displaced the drop shadow. Baking the inverse into the vertices keeps the rendered
    // surface and the mesh on the same plane, and costs nothing while flat, where the offset is 0.
    val positions =
        translatedZ(
            surface.positions +
                floatArrayOf(
                    // Shadow footprint: flat, and deliberately not curved.
                    -halfWidth, -halfHeight, halfDepth,
                    halfWidth, -halfHeight, halfDepth,
                    halfWidth, -halfHeight, -halfDepth,
                    -halfWidth, -halfHeight, -halfDepth,
                ),
            -appliedVideoCurvature.surfaceAnchorOffsetMeters(),
        )
    triMesh.updateGeometry(
        0,
        positions,
        surface.normals + FloatArray(VIDEO_SHADOW_VERTEX_COUNT * 3) { if (it % 3 == 2) 1f else 0f },
        surface.uvs + shadowUvs,
        IntArray(vertexCount) { Color.WHITE },
    )
    triMesh.updatePrimitives(0, indices)
    spatialVideoTriangleMesh = triMesh
    return SceneMesh.fromTriangleMesh(triMesh, false)
  }

  /**
   * Double-sided triangle list for a [columns] x [rows] grid in the row-major vertex order
   * [CurvedStageMeshBuilder] produces: every quad contributes its two front triangles followed by
   * the two that reverse the winding, so the stage stays visible from behind like the flat quad.
   */
  private fun curvedSurfaceIndices(columns: Int, rows: Int): IntArray {
    val columnCount = columns + 1
    val indices = IntArray(columns * rows * 12)
    var writeIndex = 0
    for (row in 0 until rows) {
      for (column in 0 until columns) {
        val bottomLeft = row * columnCount + column
        val bottomRight = bottomLeft + 1
        val topLeft = bottomLeft + columnCount
        val topRight = topLeft + 1
        indices[writeIndex++] = bottomLeft
        indices[writeIndex++] = bottomRight
        indices[writeIndex++] = topRight
        indices[writeIndex++] = bottomLeft
        indices[writeIndex++] = topRight
        indices[writeIndex++] = topLeft
        indices[writeIndex++] = bottomLeft
        indices[writeIndex++] = topRight
        indices[writeIndex++] = bottomRight
        indices[writeIndex++] = bottomLeft
        indices[writeIndex++] = topLeft
        indices[writeIndex++] = topRight
      }
    }
    return indices
  }

  /**
   * Per-material `(index start, index count)` triples in the order of the materials handed to
   * [TriangleMesh]: the viewer-facing mirrored triangles carry the video (reflect), then the shadow
   * footprint, then the hole-punch triangles on the far side. For the flat quad this reduces to the
   * original literal `[6, 6, 12, 6, 0, 6]`.
   */
  private fun videoMeshMaterialIndexRanges(frontIndexCount: Int): IntArray =
      intArrayOf(
          frontIndexCount,
          frontIndexCount,
          frontIndexCount * 2,
          VIDEO_SHADOW_INDEX_COUNT,
          0,
          frontIndexCount,
      )

  /**
   * Overlay layer shape for the danmaku/backdrop panels. Each layer supplies its own curvature so
   * the danmaku plane can bend independently of the video and the backdrop.
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
   * Single rebuild entry point for stage geometry: forces a content-quad recompute (which re-bakes
   * the current scale + curvature into the video mesh and both overlay shapes).
   */
  private fun rebuildStageGeometry() {
    lastAspectDiagnostic = null
    // Overlay shapes do not depend on the video mesh, so re-assert them even when no video is loaded
    // yet; the content-quad path below returns before it reaches them in that case.
    requestStageOverlayReshape()
    updateSpatialVideoContentQuad(
        videoWidth = player.videoSize.width,
        videoHeight = player.videoSize.height,
        pixelWidthHeightRatio = player.videoSize.pixelWidthHeightRatio,
    )
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
      pose.t = Vector3(pose.t.x, newLocalY, pose.t.z)
      stageEntity.setComponent(Transform(pose))
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
      val persistedStageScale = ViriViriApplication.appState.state.value.playbackStageScale
      appliedStageScale = PlaybackCanvasSize.clampStageScale(persistedStageScale)
      // Seed the content half-height WITH the persisted scale before the anchor is placed, so the
      // bar/stage offset already reflects the loaded canvas size (the content quad re-reports the
      // same value via [updateGrabBarPose] once the aspect is known).
      grabBarContentHalfHeight = MR_SCREEN_HEIGHT / 2f * appliedStageScale!!
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
   * Shifts every z component of an xyz-interleaved position array by [dz].
   *
   * Used to cancel the curvature displacement on the video mesh, which rides an entity that is moved so
   * the compositor's surface can land on the authored plane. Returns the input untouched for a zero
   * shift, which is the flat case.
   */
  private fun translatedZ(positions: FloatArray, dz: Float): FloatArray {
    if (dz == 0f) return positions
    var index = 2
    while (index < positions.size) {
      positions[index] += dz
      index += 3
    }
    return positions
  }

  /**
   * Creates, on first use, the child entity that carries the video surface.
   *
   * Mirrors the rail panels (`Transform` + `TransformParent` in one component list) so the pose is
   * stage-local. The curvature offset is applied straight away, so a persisted curve is honoured
   * from the first frame instead of only after the first reshape.
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
    applyStageLayerCurvatureOffset(
        entity,
        appliedVideoCurvature,
        videoSurfaceBaseLocalZ,
        MEDIA_PANEL_CURVATURE_OFFSET_SCALE,
    )
    videoSurfaceEntity = entity
    return entity
  }

  // Video Panel
  @androidx.annotation.OptIn(UnstableApi::class)
  private fun createVideoPanel() {
    val videoSurface = ensureVideoSurfaceEntity(Entity(R.id.spatialized_video_panel))
    val settings = MediaPanelSettings(
        // Scale is baked into the shape (not entity Scale) so the video loads at the persisted
        // canvas size from frame one — see [scaledStageWidth].
        shape = videoShapeOptions(scaledStageWidth(), scaledStageHeight(), appliedVideoCurvature),
        display =
            PixelDisplayOptions(
                width = IMMERSIVE_VIDEO_OUTPUT_WIDTH,
                height = IMMERSIVE_VIDEO_OUTPUT_HEIGHT,
            ),
        rendering = MediaPanelRenderOptions(stereoMode = StereoMode.None, zIndex = VIDEO_LAYER_Z_INDEX),
    )
    val panelSceneObject = PanelSceneObject(
        scene,
        videoSurface,
        settings.toPanelConfigOptions().apply {
          applyVideoLayerDepth(this)
          // A reshape rebuilds the panel mesh from this config, so the hand-built video mesh has to
          // be supplied here exactly as it is on creation (see [createVideoSceneMesh]).
          sceneMeshCreator = { texture ->
            createVideoSceneMesh(texture = texture, width = width, height = height)
          }
        },
    )
        .apply {
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

          addInputListener(
              object : InputListener {
                override fun onHoverStart(
                    receiver: SceneObject,
                    sourceOfInput: Entity,
                ) = Unit

                override fun onClick(
                    receiver: SceneObject,
                    hitInfo: HitInfo,
                    sourceOfInput: Entity,
                ) {
                  Log.d(
                      WORKBENCH_TRACE_TAG,
                      "stageClick canvas=${immersivePlaybackCanvasHost.state.canvas} " +
                          "workbench=${if (::immersiveWorkbenchHost.isInitialized) immersiveWorkbenchHost.state else "uninitialized"}",
                  )
                  onStagePrimaryAction()
                }

                override fun onInput(
                    receiver: SceneObject,
                    hitInfo: HitInfo,
                    sourceOfInput: Entity,
                    changed: Int,
                    clicked: Int,
                    downTime: Long,
                ): Boolean = false
              }
          )

        }

    spatialVideoPanelSceneObject = panelSceneObject
    attachImmersiveOutput(panelSceneObject)

    systemManager
        .findSystem<SceneObjectSystem>()
        .addSceneObject(
            videoSurface,
            CompletableFuture<SceneObject>().apply { complete(panelSceneObject) },
        )
    // The video surface is a RENDER CARRIER: no hit geometry at all. Its scene mesh travels with the
    // curvature offset, so anything hittable on this entity sat a whole radius away from the picture and
    // swallowed the controller ray and its cursor for everything behind it. NoCollision is asserted
    // explicitly rather than merely dropping Hittable, because dropping it alone did NOT stop the
    // interception on device. The stage's own tap is geometric now
    // (AnalogMediaStageTuningSystem + StageRayTargeting) and needs no hit geometry.
    videoSurface.setComponent(Hittable(MeshCollision.NoCollision))
    // Do NOT add a `Panel(...)` component here to try to switch the mesh's collision off. An entity with
    // no panel registration crashes the app on launch that way (measured: build 904A4B03). Since
    // `PanelSceneObject` and `PanelConfigOptions` expose no collision switch, this layer offers no way to
    // make the panel mesh unhittable — see the mutual-exclusion note in Stage 3i of
    // docs/research/stage-video-mesh-curvature.md.

    // No IsdkPanelDimensions on the moving surface either. It exists so ISDK can size a panel that was
    // built by hand instead of by a Panel component, and it is the other hit source on this entity:
    // dropping Hittable above did not stop the ray from being intercepted, which is what points here.
    // The stage is deliberately not an ISDK-interactive object, so the dimensions have no consumer.
    // Scheme A: the grab BAR is the single grab target (movable_group bound to the bar handle).
    // The stage itself is intentionally NOT grabbable — removing IsdkGrabbable/IsdkPanelGrabHandle
    // stops the stage edges from competing with the bar for the grab ray, and (per ISDK docs)
    // restores onClick on the stage so "tap video to reveal transport" works again.
    // The panel's own ISDK dimensions are no longer published either: see above, the moving surface has
    // no hit geometry.

    // The mesh creator can run before the PanelSceneObject reference is available for reshape.
    lastAspectDiagnostic = null
    updateSpatialVideoContentQuad(
        videoWidth = player.videoSize.width,
        videoHeight = player.videoSize.height,
        pixelWidthHeightRatio = player.videoSize.pixelWidthHeightRatio,
    )
    // Note: no entity Scale here. The persisted stage scale is baked into the panel shape by
    // [videoShapeOptions] via [scaledStageWidth] / [scaledStageHeight], which is deterministic
    // across PanelSceneObject lifetime (see the k17 note on [scaledStageWidth]).
  }

  // Movies Controller panel
  private fun controlsPanelRegistration(): PanelRegistration {
    return LayoutXMLPanelRegistration(
        R.id.controls_id,
        layoutIdCreator = { R.layout.controls },
        settingsCreator = {
          UIPanelSettings(
              shape = QuadShapeOptions(width = 1.32f, height = 0.38f),
              display = DpDisplayOptions(width = 460f, height = 132f, dpi = 600),
              style = PanelStyleOptions(themeResourceId = R.style.PanelAppThemeTransparent),
          )
        },
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
                    onVideoSelected = ::returnToPlaybackFromCenterContent,
                    onDismissWorkbench = { dismissWorkbenchFromCenterContent("centerPanelBlank") },
                )
              }
            }
          },
          settingsCreator = {
            UIPanelSettings(
                // UX: center Search/List stays readable at three columns without retaining a high-resolution panel buffer.
                shape = QuadShapeOptions(width = layout.centerWidth, height = layout.centerHeight),
                display = DpDisplayOptions(width = 768f, height = 615f, dpi = 512),
                style = PanelStyleOptions(themeResourceId = R.style.PanelAppThemeTransparent),
            )
          },
      )

  private fun inputMethodPanelRegistration(): PanelRegistration =
      ComposeViewPanelRegistration(
          R.id.input_method_panel,
          composeViewCreator = { _, context ->
            ComposeView(context).apply { setContent { ImmersiveInputMethodPanel() } }
          },
          settingsCreator = {
            UIPanelSettings(
                shape = QuadShapeOptions(width = 1.62f, height = 0.68f),
                display = DpDisplayOptions(width = 832f, height = 348f, dpi = 512),
                style = PanelStyleOptions(themeResourceId = R.style.PanelAppThemeTransparent),
            )
          },
      )

  private fun stageBackdropPanelRegistration(): PanelRegistration =
      ComposeViewPanelRegistration(
          R.id.stage_backdrop_panel,
          composeViewCreator = { _, context -> ComposeView(context).apply { setContent { StageBackdrop() } } },
          settingsCreator = {
            // A UI panel physical size comes from this shape, so the shared stage scale is baked
            // in here (see [scaledStageWidth]) and re-asserted by [applyOverlayStageShape].
            stageOverlayPanelSettings(scaledStageWidth(), scaledStageHeight(), appliedBackdropCurvature)
          },
      )

  private fun danmakuOverlayPanelRegistration(): PanelRegistration =
      ComposeViewPanelRegistration(
          R.id.danmaku_overlay_panel,
          composeViewCreator = { _, context -> ComposeView(context).apply { setContent { DanmakuOverlay() } } },
          settingsCreator = {
            // Stage-scaled footprint: see [applyOverlayStageShape].
            stageOverlayPanelSettings(scaledStageWidth(), scaledStageHeight(), appliedDanmakuCurvature)
          },
      )

  /**
   * Visual grab bar under the video stage. It is a NON-grabbable, non-interactive indicator
   * (NoCollision): the ISDK grab handle already makes the stage edges grabbable, this bar just
   * makes the bottom grab zone visible so the user knows where to grab to move the whole
   * workbench. See task 09-02-workbench-lift-and-grab.
   */
  private fun grabBarPanelRegistration(): PanelRegistration =
      LayoutXMLPanelRegistration(
          R.id.grab_bar_panel,
          layoutIdCreator = { R.layout.grab_bar },
          settingsCreator = {
            UIPanelSettings(
                shape = QuadShapeOptions(width = GRAB_BAR_WIDTH_METERS, height = GRAB_BAR_HEIGHT_METERS),
                display = DpDisplayOptions(width = 512f, height = 56f, dpi = 600),
                input = PanelInputOptions(0),
                style = PanelStyleOptions(themeResourceId = R.style.PanelAppThemeTransparent),
            )
          },
      )

  private fun stageOverlayPanelSettings(
      width: Float,
      height: Float,
      curvature: PlaybackStageCurvature,
  ) =
      UIPanelSettings(
          shape = overlayShapeOptions(width, height, curvature),
          display = DpDisplayOptions(width = 1280f, height = 720f, dpi = 800),
          input = PanelInputOptions(0),
          style = PanelStyleOptions(themeResourceId = R.style.PanelAppThemeTransparent),
      )

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
        appState.searchWorkspace.route == SearchWorkspaceRoute.SEARCH_EMPTY &&
            appState.searchWorkspace.isKeyboardVisible &&
            !appState.searchWorkspace.isKeyboardDismissed
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
    // The panel shape has to carry the persisted stage scale from frame one: the state observer may
    // have run its one-shot apply before this panel existed.
    applyOverlayStageShape()
    requestStageOverlayReshape()
  }

  private fun createDanmakuOverlayPanel() {
    if (danmakuOverlayEntity != null) return
    danmakuOverlayEntity =
        Entity.createPanelEntity(
                R.id.danmaku_overlay_panel,
                Transform(Pose(Vector3(0f, 0f, stageDanmakuBaseLocalZ))),
                TransformParent(Entity(R.id.spatialized_video_panel)),
                Visible(true),
            )
            // The overlay must render but never block the stage's controller/hand raycasts.
            .also { it.setComponent(Panel(R.id.danmaku_overlay_panel, MeshCollision.NoCollision)) }
    // Persisted scale + the danmaku layer's own curvature (independent from video/backdrop).
    applyOverlayStageShape()
    requestStageOverlayReshape()
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

  private fun wristDebugPanelRegistration(): PanelRegistration {
    return LayoutXMLPanelRegistration(
        R.id.wrist_debug_panel,
        layoutIdCreator = { R.layout.wrist_debug_panel },
        settingsCreator = {
          UIPanelSettings(
              shape = QuadShapeOptions(width = WRIST_DEBUG_PANEL_WIDTH, height = WRIST_DEBUG_PANEL_HEIGHT),
              display = DpDisplayOptions(width = 224f, height = 80f, dpi = 1600),
              style = PanelStyleOptions(themeResourceId = R.style.PanelAppThemeTransparent),
          )
        },
        panelSetupWithRootView = { rootView, _, _ ->
          rootView.findViewById<TextView>(R.id.wrist_debug_label).text = "DEV ${BuildConfig.GIT_SHA}"
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

  private fun modePanelRegistration(): PanelRegistration {
    return LayoutXMLPanelRegistration(
        R.id.mode_panel,
        layoutIdCreator = { R.layout.mode_panel },
        settingsCreator = {
          UIPanelSettings(
              // UX: the right rail hosts the merged debug/media-status content in a scroll
              // container, so its height now matches the left rail for visual symmetry.
              shape = QuadShapeOptions(width = layout.railWidth, height = layout.leftRailHeight),
              display = DpDisplayOptions(width = 280f, height = 464f, dpi = 600),
              style = PanelStyleOptions(themeResourceId = R.style.PanelAppThemeTransparent),
          )
        },
        panelSetupWithRootView = { rootView, _, _ ->
          currentMediaTitle.complete(rootView.findViewById(R.id.current_media_title))
          currentMediaDetail.complete(rootView.findViewById(R.id.current_media_detail))
          val retryMediaButtonLocal = rootView.findViewById<Button>(R.id.retry_media_button)
          retryMediaButton.complete(retryMediaButtonLocal)
          retryMediaButtonLocal.setOnClickListener { ViriViriApplication.appState.retrySelectedVideo() }
          val appState = ViriViriApplication.appState.state.value
          updateImmersiveMediaStatus(
              selected = appState.selected,
              error = appState.error.takeIf { appState.destination == ViriViriDestination.VIEWER },
              isResolvingPlayback = appState.isResolvingPlayback,
          )
          updateImmersiveRetryAvailability(
              destination = appState.destination,
              selected = appState.selected,
              error = appState.error,
              isResolvingPlayback = appState.isResolvingPlayback,
          )
          val displayRatioButtonLocal = rootView.findViewById<Button>(R.id.display_ratio_button)
          displayRatioButton.complete(displayRatioButtonLocal)
          syncPlaybackDisplayRatioLabel(appState.playbackDisplayRatio)
          displayRatioButtonLocal.setOnClickListener { showPlaybackDisplayRatioMenu(displayRatioButtonLocal) }
          setupHoverAndTouchListeners(displayRatioButtonLocal)
          val canvasSizeButtonLocal = rootView.findViewById<Button>(R.id.canvas_size_button)
          canvasSizeButton.complete(canvasSizeButtonLocal)
          syncPlaybackCanvasSizeLabel(appState.playbackCanvasSize)
          canvasSizeButtonLocal.setOnClickListener { showPlaybackCanvasSizeMenu(canvasSizeButtonLocal) }
          setupHoverAndTouchListeners(canvasSizeButtonLocal)
          val debugBuildLabel = rootView.findViewById<TextView>(R.id.debug_build_label)
          if (BuildConfig.DEBUG) {
            debugBuildLabel.text = "DEV ${BuildConfig.GIT_SHA}"
            debugBuildLabel.visibility = View.VISIBLE
          }
          val debugAspectDetailLocal = rootView.findViewById<TextView>(R.id.debug_aspect_detail)
          val debugAspectTargetButtonLocal = rootView.findViewById<Button>(R.id.debug_aspect_target_button)
          val debugAspectPlanButtonLocal = rootView.findViewById<Button>(R.id.debug_aspect_plan_button)
          val debugAspectApplyButtonLocal = rootView.findViewById<Button>(R.id.debug_aspect_apply_button)
          debugAspectDetail.complete(debugAspectDetailLocal)
          debugAspectTargetButton.complete(debugAspectTargetButtonLocal)
          debugAspectPlanButton.complete(debugAspectPlanButtonLocal)
          debugAspectApplyButton.complete(debugAspectApplyButtonLocal)
          if (BuildConfig.DEBUG) {
            debugAspectDetailLocal.visibility = View.VISIBLE
            debugAspectTargetButtonLocal.visibility = View.VISIBLE
            debugAspectPlanButtonLocal.visibility = View.VISIBLE
            debugAspectApplyButtonLocal.visibility = View.VISIBLE
            syncSpatialVideoAspectProbeUi()
            debugAspectTargetButtonLocal.setOnClickListener {
              showSpatialVideoAspectTargetMenu(debugAspectTargetButtonLocal)
            }
            debugAspectPlanButtonLocal.setOnClickListener {
              showSpatialVideoAspectPlanMenu(debugAspectPlanButtonLocal)
            }
            debugAspectApplyButtonLocal.setOnClickListener { applySpatialVideoAspectProbe() }
          }
          rootView.findViewById<Button>(R.id.open_2d_button).setOnClickListener {
            ViriViriApplication.appState.playerSession.beginOutputHandoff()
            launchPanelModeInHome()
          }
          // Merged debug telemetry (was the standalone debug panel): live danmaku stream + stage scale.
          val scaleText = rootView.findViewById<TextView>(R.id.scale_text)
          val scaleBar = rootView.findViewById<SeekBar>(R.id.scale_bar)
          val stageYText = rootView.findViewById<TextView>(R.id.stage_y_text)
          val danmakuStatus = rootView.findViewById<TextView>(R.id.danmaku_status)
          val curvatureText = rootView.findViewById<TextView>(R.id.curvature_text)
          val danmakuHandler = android.os.Handler(android.os.Looper.getMainLooper())
          val scaleMax = scaleBar?.max ?: 1
          val pollDanmaku =
              object : Runnable {
                override fun run() {
                  val appUiState = ViriViriApplication.appState.state.value
                  // Live stage world Y so the user can read the current height while tuning it.
                  val stageY =
                      Entity(R.id.spatialized_video_panel)
                          .tryGetComponent<Transform>()
                          ?.transform
                          ?.t
                          ?.y
                  stageYText?.text = stageY?.let { "Stage Y: %.2f m".format(it) } ?: "Stage Y: --"
                  // Keep the scale readout and bar in sync with the real state (thumbstick,
                  // presets and restores all mutate appState; only the bar drag mutates it here).
                  val currentScale = appUiState.playbackStageScale
                  scaleText?.text = "Scale: %.2f".format(currentScale)
                  val barProgress =
                      if (currentScale <= PlaybackCanvasSize.MIN_STAGE_SCALE) 0
                      else {
                        val clamped =
                            currentScale.coerceIn(
                                PlaybackCanvasSize.MIN_STAGE_SCALE,
                                PlaybackCanvasSize.MAX_STAGE_SCALE,
                            )
                        (((clamped - PlaybackCanvasSize.MIN_STAGE_SCALE) /
                            (PlaybackCanvasSize.MAX_STAGE_SCALE - PlaybackCanvasSize.MIN_STAGE_SCALE)) *
                            scaleMax)
                            .toInt()
                            .coerceIn(0, scaleMax)
                      }
                  if (scaleBar != null && scaleBar.progress != barProgress) {
                    scaleBar.progress = barProgress
                  }
                  val videoId = appUiState.selected?.videoId
                  val summary =
                      videoId?.let { ViriViriApplication.appState.danmakuStreamSourceOrNull(it)?.debugSummary() }
                  danmakuStatus?.text =
                      if (summary != null) summary
                      else if (appUiState.isLoadingDanmaku) "danmaku: loading..."
                      else "danmaku: idle"
                  curvatureText?.text =
                      "Curve (all layers): ${curvatureLabel(appUiState.playbackVideoCurvature)}\n" +
                          "  danmaku z: ${"%.2f".format(stageDanmakuBaseLocalZ)}"
                  danmakuHandler.postDelayed(this, 500L)
                }
              }
          danmakuHandler.post(pollDanmaku)
          scaleBar?.setOnSeekBarChangeListener(
              object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seek: SeekBar, progress: Int, fromUser: Boolean) {
                  if (fromUser) {
                    val normalized = progress.toFloat() / scaleMax.coerceAtLeast(1)
                    val newScale =
                        PlaybackCanvasSize.MIN_STAGE_SCALE +
                            normalized * (PlaybackCanvasSize.MAX_STAGE_SCALE - PlaybackCanvasSize.MIN_STAGE_SCALE)
                    scaleText?.text = "Scale: %.2f".format(newScale)
                    ViriViriApplication.appState.setPlaybackStageScale(newScale)
                  }
                }

                override fun onStartTrackingTouch(seekBar: SeekBar) = Unit

                override fun onStopTrackingTouch(seekBar: SeekBar) = Unit
              }
          )
          rootView.findViewById<Button>(R.id.reset_stage_y_button).setOnClickListener {
            resetStageYToDefault()
          }
          rootView.findViewById<Button>(R.id.reset_curve_button).setOnClickListener {
            resetPlaybackCurvatureToFlat()
          }
          rootView.findViewById<Button>(R.id.danmaku_z_button).setOnClickListener {
            danmakuBaseLocalZIndex =
                (danmakuBaseLocalZIndex + 1) % DANMAKU_BASE_LOCAL_Z_CANDIDATES.size
            danmakuOverlayEntity?.let {
              applyStageLayerCurvatureOffset(
                  it,
                  appliedDanmakuCurvature,
                  stageDanmakuBaseLocalZ,
                  UI_PANEL_CURVATURE_OFFSET_SCALE,
              )
            }
            Log.i("ViriViriCurve", "danmaku base z -> ${"%.2f".format(stageDanmakuBaseLocalZ)}")
          }
          setupHoverAndTouchListeners(rootView)
        },
    )
  }

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
    val panel =
        videoSurfaceEntity?.let { surface ->
          systemManager.findSystem<SceneObjectSystem>().getSceneObject(surface)?.getNow(null)
        } as? PanelSceneObject
    panel?.let {
      spatialVideoPanelSceneObject = it
      attachImmersiveOutput(it)
    }
    if (::immersivePlaybackCanvasHost.isInitialized) immersivePlaybackCanvasHost.applyCurrentState()
  }

  override fun onDestroy() {
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
   * The stage control belongs to the user only while the workbench is away.
   *
   * With the panels up the user is working them, and the stage sits right in the middle of that
   * interaction volume, so a live stick or trigger underneath the panels made them unusable.
   */
  private fun isStageInputEnabled(): Boolean =
      !(::immersiveWorkbenchHost.isInitialized && immersiveWorkbenchHost.state.visible)

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
        halfWidth = scaledStageWidth() / 2f,
        halfHeight = scaledStageHeight() / 2f,
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
   */
  private fun onStagePrimaryAction() {
    // UX: a stage action must never dismiss the Workbench through the outside-click layer.
    //
    // The stage carries NO hit geometry -- its tap is geometric (AnalogMediaStageTuningSystem +
    // StageRayTargeting), see the NoCollision note on `videoSurface`. So the *release* of this very
    // trigger pull travels past the stage and lands on `WorkbenchOuterDismiss`, which sits behind
    // it. Without this guard the press opened the Workbench and the release closed it.
    //
    // This re-arms on every frame the trigger is held (that system calls here each frame while the
    // user aims at the stage), so the guard stays alive for the whole press rather than relying on
    // a fixed window measured from a single press edge.
    suppressOuterDismissUntilMs = SystemClock.uptimeMillis() + OUTER_DISMISS_SUPPRESSION_MS
    if (::immersiveWorkbenchHost.isInitialized && immersiveWorkbenchHost.state.visible) {
      // Workbench dismissal belongs exclusively to WorkbenchOuterDismiss.
      // A MediaStage action must not cancel a just-opened panel route.
      showTransportOverlay()
    } else {
      dispatchPlaybackCanvas(PlaybackCanvasEvent.PrimaryStageAction)
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
    suppressOuterDismissUntilMs = SystemClock.uptimeMillis() + OUTER_DISMISS_SUPPRESSION_MS
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

  private fun updateImmersiveMediaStatus(
      selected: Recommendation?,
      error: String?,
      isResolvingPlayback: Boolean,
  ) {
    val status = immersiveMediaStatus(selected, error, isResolvingPlayback)
    currentMediaTitle.thenAccept { it.text = status.title }
    currentMediaDetail.thenAccept { it.text = status.detail }
  }

  private fun updateImmersiveRetryAvailability(
      destination: ViriViriDestination,
      selected: Recommendation?,
      error: String?,
      isResolvingPlayback: Boolean,
  ) {
    retryMediaButton.thenAccept { button ->
      val isViewerAttempt = destination == ViriViriDestination.VIEWER && selected != null && isResolvingPlayback
      val canRetry = canRetryImmersiveMedia(destination, selected, error, isResolvingPlayback)
      button.visibility = if (isViewerAttempt || canRetry) View.VISIBLE else View.GONE
      button.isEnabled = canRetry
      button.text = if (isViewerAttempt) "Retrying..." else "Retry"
    }
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

  private fun syncPlaybackDisplayRatioLabel(displayRatio: PlaybackDisplayRatio) {
    displayRatioButton.thenAccept { it.text = "Display ratio: ${displayRatio.label}" }
  }

  private fun syncPlaybackCanvasSizeLabel(canvasSize: PlaybackCanvasSize) {
    canvasSizeButton.thenAccept { it.text = "Canvas size: ${canvasSize.label}" }
  }

  private fun applyPlaybackStageScale(stageScale: Float) {
    val normalizedScale = PlaybackCanvasSize.clampStageScale(stageScale)
    if (appliedStageScale == normalizedScale) return
    appliedStageScale = normalizedScale
    // One shared scale, delivered per layer (see [applyOverlayStageShape]): the danmaku / backdrop
    // overlays take the factor in their panel shape, and the hand-built video panel in its mesh
    // geometry. The grab bar keeps FIXED size; only its local Y re-positions below the scaled
    // bottom edge.
    applyOverlayStageShape()
    rebuildStageGeometry()
  }

  /**
   * Re-bakes the shared stage scale - and each overlay's own curvature - into the overlay panels.
   *
   * A UI panel's physical size comes from its SHAPE, not from an entity `Scale` component, so the
   * scaled footprint is written straight into the reshaped [PanelSceneObject]. Danmaku and
   * stage_backdrop each keep their OWN curvature while sharing the stage footprint.
   *
   * The overlay handles passed in here must be the ones returned by [Entity.createPanelEntity]: a
   * panel registration id and the spawned entity id are different namespaces, so an
   * `Entity(R.id...)` handle built at call time resolves to nothing and the reshape is dropped.
   */
  private fun applyOverlayStageShape() {
    reshapeStageOverlay(stageBackdropEntity, appliedBackdropCurvature, stageBackdropBaseLocalZ)
    reshapeStageOverlay(danmakuOverlayEntity, appliedDanmakuCurvature, stageDanmakuBaseLocalZ)
  }

  /** Applies an independently-configured curvature to the video layer. */
  private fun applyPlaybackVideoCurvature(curvature: PlaybackStageCurvature) {
    if (appliedVideoCurvature == curvature) return
    appliedVideoCurvature = curvature
    // Move the surface entity first: the geometry rebuild below is skipped while no video is loaded.
    videoSurfaceEntity?.let {
      applyStageLayerCurvatureOffset(
          it,
          curvature,
          videoSurfaceBaseLocalZ,
          MEDIA_PANEL_CURVATURE_OFFSET_SCALE,
      )
    }
    rebuildStageGeometry()
  }

  /** Applies an independently-configured curvature to the danmaku layer. */
  private fun applyPlaybackDanmakuCurvature(curvature: PlaybackStageCurvature) {
    if (appliedDanmakuCurvature == curvature) return
    appliedDanmakuCurvature = curvature
    rebuildStageGeometry()
  }

  /** Applies an independently-configured curvature to the backdrop (dim) layer. */
  private fun applyPlaybackBackdropCurvature(curvature: PlaybackStageCurvature) {
    if (appliedBackdropCurvature == curvature) return
    appliedBackdropCurvature = curvature
    rebuildStageGeometry()
  }

  /**
   * Debug escape hatch: puts every stage layer back to [PlaybackStageCurvature.Flat].
   *
   * Routed through [ViriViriAppState.setPlaybackCurvature] so the PERSISTED values are overwritten
   * too. A curvature bad enough to swallow the stage hit survives a relaunch otherwise, which left
   * clearing app data as the only recovery.
   */
  private fun resetPlaybackCurvatureToFlat() {
    ViriViriApplication.appState.setPlaybackCurvature(PlaybackStageCurvature.Flat)
    // Re-assert the poses synchronously: the state observer is asynchronous, and this path exists
    // precisely for the states the user cannot fix by hand.
    videoSurfaceEntity?.let {
      applyStageLayerCurvatureOffset(
          it,
          PlaybackStageCurvature.Flat,
          videoSurfaceBaseLocalZ,
          MEDIA_PANEL_CURVATURE_OFFSET_SCALE,
      )
    }
    stageBackdropEntity?.let {
      applyStageLayerCurvatureOffset(
          it,
          PlaybackStageCurvature.Flat,
          stageBackdropBaseLocalZ,
          UI_PANEL_CURVATURE_OFFSET_SCALE,
      )
    }
    danmakuOverlayEntity?.let {
      applyStageLayerCurvatureOffset(
          it,
          PlaybackStageCurvature.Flat,
          stageDanmakuBaseLocalZ,
          UI_PANEL_CURVATURE_OFFSET_SCALE,
      )
    }
    Log.i("ViriViriCurve", "curvature reset to flat for all layers")
  }

  /** Debug-rail formatting for a curvature: "flat", or the cylinder radius in metres. */
  private fun curvatureLabel(curvature: PlaybackStageCurvature): String =
      curvature.radiusOrNull()?.let { "r=%.2f m".format(it) } ?: "flat"

  /**
   * Coarse shape key for the device log. Quantised to 0.25 m so holding the thumbstick produces a
   * handful of lines instead of one per frame, while still proving which shape the panel received.
   */
  private fun curvatureLogKey(curvature: PlaybackStageCurvature): String =
      curvature.radiusOrNull()?.let { "cylinder r=%.2f m".format(kotlin.math.round(it * 4f) / 4f) }
          ?: "flat"

  /**
   * Records the shape handed to the video panel, so a device run can separate a wiring failure (no
   * line while the stick bends the layer) from a compositor limitation (the line reports a cylinder
   * while the image stays flat).
   */
  private fun logVideoPanelShape(shapeWidth: Float, shapeHeight: Float) {
    if (!BuildConfig.DEBUG) return
    val key =
        "%s %.2fx%.2f".format(curvatureLogKey(appliedVideoCurvature), shapeWidth, shapeHeight)
    if (key == lastLoggedVideoPanelShape) return
    lastLoggedVideoPanelShape = key
    Log.i("ViriViriCurve", "video panel shape -> $key")
  }

  private fun applyPlaybackDisplayRatio(displayRatio: PlaybackDisplayRatio) {
    val target = SpatialVideoAspectProbeTarget.from(displayRatio)
    val current = spatialVideoAspectProbeState
    if (
        current.pendingTarget == target &&
            current.pendingPlan == SpatialVideoAspectProbePlan.PANEL_RESHAPE &&
            current.appliedTarget == target &&
            current.appliedPlan == SpatialVideoAspectProbePlan.PANEL_RESHAPE
    ) return
    spatialVideoAspectProbeState =
        current.copy(
            pendingTarget = target,
            pendingPlan = SpatialVideoAspectProbePlan.PANEL_RESHAPE,
            appliedTarget = target,
            appliedPlan = SpatialVideoAspectProbePlan.PANEL_RESHAPE,
        )
    lastAspectDiagnostic = null
    updateSpatialVideoContentQuad(
        videoWidth = player.videoSize.width,
        videoHeight = player.videoSize.height,
        pixelWidthHeightRatio = player.videoSize.pixelWidthHeightRatio,
    )
    syncSpatialVideoAspectProbeUi()
  }

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
        syncSpatialVideoAspectProbeUi()
        true
      }
      show()
    }
  }

  private fun showSpatialVideoAspectPlanMenu(anchor: View) {
    darkPopupMenu(anchor).apply {
      SpatialVideoAspectProbePlan.entries.forEachIndexed { index, plan ->
        menu.add(0, index, index, plan.label).isChecked = plan == spatialVideoAspectProbeState.pendingPlan
      }
      menu.setGroupCheckable(0, true, true)
      setOnMenuItemClickListener { item ->
        val plan = SpatialVideoAspectProbePlan.entries.getOrNull(item.itemId)
            ?: return@setOnMenuItemClickListener false
        spatialVideoAspectProbeState = SpatialVideoAspectProbeReducer.selectPlan(spatialVideoAspectProbeState, plan)
        syncSpatialVideoAspectProbeUi()
        true
      }
      show()
    }
  }

  private fun applySpatialVideoAspectProbe() {
    spatialVideoAspectProbeState = SpatialVideoAspectProbeReducer.apply(spatialVideoAspectProbeState)
    if (spatialVideoAspectProbeState.appliedPlan == SpatialVideoAspectProbePlan.PANEL_RESHAPE) {
      ViriViriApplication.appState.selectPlaybackDisplayRatio(
          spatialVideoAspectProbeState.appliedTarget.displayRatio
      )
      return
    }
    lastAspectDiagnostic = null
    updateSpatialVideoContentQuad(
        videoWidth = player.videoSize.width,
        videoHeight = player.videoSize.height,
        pixelWidthHeightRatio = player.videoSize.pixelWidthHeightRatio,
    )
  }

  private fun syncSpatialVideoAspectProbeUi() {
    val state = spatialVideoAspectProbeState
    debugAspectTargetButton.thenAccept { it.text = "Target: ${state.pendingTarget.label}" }
    debugAspectPlanButton.thenAccept { it.text = "Plan: ${state.pendingPlan.label}" }
    debugAspectApplyButton.thenAccept { it.text = "Apply" }
    val diagnostic = lastAspectDiagnostic
    debugAspectDetail.thenAccept { detail ->
      detail.text =
          if (diagnostic == null) {
            "Pending ${state.pendingTarget.label} / ${state.pendingPlan.label}"
          } else {
            "src=${diagnostic.displayAspectRatio} target=${resolvedSpatialVideoAspectRatio(diagnostic)} " +
                "quad=${diagnostic.contentHalfWidth}x${diagnostic.contentHalfHeight}"
          }
    }
  }

  private fun resolvedSpatialVideoAspectRatio(diagnostic: SpatialVideoAspectDiagnostic): Float =
      spatialVideoAspectProbeState.appliedTarget.displayAspectRatio ?: diagnostic.displayAspectRatio

  /** Reconfigures the existing native panel and refreshes its matching ISDK hit dimensions. */
  private fun reshapeSpatialVideoPanel(content: SpatialVideoContentQuad) {
    val panel = spatialVideoPanelSceneObject ?: return
    // [content] already carries the shared stage scale (it is derived from the scaled stage
    // footprint), so the shape dimensions below bake scale + curvature together.
    val shapeWidth = content.halfWidth * 2f
    val shapeHeight = content.halfHeight * 2f
    logVideoPanelShape(shapeWidth, shapeHeight)
    panel.reshape(
        MediaPanelSettings(
                shape = videoShapeOptions(shapeWidth, shapeHeight, appliedVideoCurvature),
                display =
                    PixelDisplayOptions(
                        width = IMMERSIVE_VIDEO_OUTPUT_WIDTH,
                        height = IMMERSIVE_VIDEO_OUTPUT_HEIGHT,
                    ),
                rendering = MediaPanelRenderOptions(stereoMode = StereoMode.None, zIndex = VIDEO_LAYER_Z_INDEX),
            )
            .toPanelConfigOptions()
                .apply {
                  applyVideoLayerDepth(this)
                  // Same reason as at creation: a reshape would otherwise swap our curved video
                  // mesh for the SDK generated quad.
                  sceneMeshCreator = { texture ->
                    createVideoSceneMesh(texture = texture, width = width, height = height)
                  }
                }
    )
    panel.updateIsdkComponentProperties(Entity(R.id.spatialized_video_panel))
    // Each overlay keeps its OWN curvature, so the danmaku plane can bend on a different radius
    // than the backdrop (and than the video) while sharing the same scaled stage footprint.
    reshapeStageOverlay(stageBackdropEntity, appliedBackdropCurvature, stageBackdropBaseLocalZ)
    reshapeStageOverlay(danmakuOverlayEntity, appliedDanmakuCurvature, stageDanmakuBaseLocalZ)
    updateGrabBarPose(content.halfHeight)
    if (BuildConfig.DEBUG) {
      Log.i("ViriViriAspect", "isdkPanelDimensions=$shapeWidth x $shapeHeight")
    }
  }

  /**
   * Re-asserts the curvature of both stage overlays.
   *
   * k18 root cause: panel scene objects are instantiated by the SDK's panel-creation system a frame
   * after [Entity.createPanelEntity], so a reshape issued at creation time is silently dropped. The
   * danmaku/backdrop therefore kept the shape their registration produced — and that registration is
   * evaluated before the persisted scale is known — leaving the overlay centred on the stage but
   * never resized. Requesting the reshape here and replaying it from [StageOverlayReshapeSystem]
   * makes the result independent of that creation order.
   */
  private fun requestStageOverlayReshape() {
    stageOverlaysPendingReshape = true
    flushPendingStageOverlayReshape()
  }

  /**
   * Replays the deferred stage reshapes: the danmaku / backdrop overlays while a reshape is still
   * pending, then the video panel geometry once its scene object exists.
   */
  private fun flushPendingStageOverlayReshape() {
    if (stageOverlaysPendingReshape) replayPendingStageOverlayReshape()
    reassertAppliedVideoStageGeometry()
  }

  /** Replays a pending overlay reshape. A no-op once both overlays have landed. */
  private fun replayPendingStageOverlayReshape() {
    val backdropLanded =
        reshapeStageOverlay(stageBackdropEntity, appliedBackdropCurvature, stageBackdropBaseLocalZ)
    val danmakuLanded =
        reshapeStageOverlay(danmakuOverlayEntity, appliedDanmakuCurvature, stageDanmakuBaseLocalZ)
    if (backdropLanded && danmakuLanded) stageOverlaysPendingReshape = false
  }

  /**
   * Re-asserts the video panel geometry once its scene object exists.
   *
   * [applyPlaybackVideoCurvature] can fire before the panel is created (the app-state collector runs
   * during `onCreate`, while the panel is created later), and [reshapeSpatialVideoPanel] silently
   * drops a reshape when the scene object is missing. Because the caller has already recorded the
   * requested curvature in `appliedVideoCurvature`, its `if (applied == curvature) return` guard then
   * blocks every later retry, so a persisted curvature never reached the panel. Re-running the
   * content-quad path once per panel instance makes the result independent of that ordering, the
   * same way the overlay replay above does for the danmaku / backdrop layers.
   */
  private fun reassertAppliedVideoStageGeometry() {
    val panel = spatialVideoPanelSceneObject ?: return
    if (panel === reassertedVideoPanelSceneObject) return
    reassertedVideoPanelSceneObject = panel
    lastAspectDiagnostic = null
    updateSpatialVideoContentQuad(
        videoWidth = player.videoSize.width,
        videoHeight = player.videoSize.height,
        pixelWidthHeightRatio = player.videoSize.pixelWidthHeightRatio,
    )
  }

  /**
   * Reshapes one overlay now, or defers it until its panel scene object exists.
   *
   * [entity] must be the handle returned by [Entity.createPanelEntity]. The panel registration id
   * and the spawned entity id are different namespaces, so a freshly built `Entity(R.id...)` handle
   * addresses nothing: every overlay reshape issued through one was silently dropped, which is why
   * the danmaku and backdrop layers never followed the stage scale.
   */
  private fun reshapeStageOverlay(
      entity: Entity?,
      curvature: PlaybackStageCurvature,
      baseLocalZ: Float,
  ): Boolean {
    val overlayEntity = entity ?: return true
    val overlay =
        systemManager.findSystem<SceneObjectSystem>()
            .getSceneObject(overlayEntity)
            ?.getNow(null) as? PanelSceneObject
    if (overlay == null) {
      stageOverlaysPendingReshape = true
      if (BuildConfig.DEBUG) {
        Log.i("ViriViriStage", "overlay reshape deferred: panel scene object not created yet")
      }
      return false
    }
    overlay.reshape(
        stageOverlayPanelSettings(
                scaledStageWidth(),
                scaledStageHeight(),
                curvature,
            )
            .toPanelConfigOptions()
    )
    applyStageLayerCurvatureOffset(overlayEntity, curvature, baseLocalZ, UI_PANEL_CURVATURE_OFFSET_SCALE)
    return true
  }

  /**
   * Pins a curved layer's SURFACE where the flat panel was, by moving the layer the other way by the
   * same radius (see [PlaybackStageCurvature.surfaceAnchorOffsetMeters]).
   *
   * [baseLocalZ] is the layer's authored separation inside the stage root; it is preserved so the
   * danmaku layer keeps sitting just in front of the backdrop. The offset is recomputed from the
   * base rather than added to the current Z, so repeated reshapes cannot accumulate.
   *
   * [offsetScale] separates the two kinds of layer. It is 1 for the media panel, whose pixels come from
   * a compositor cylinder layer (`CylinderLayerConfig.radius`) whose surface sits one radius away from
   * the scene object's origin — and 0 for the UI-panel overlays, whose cylinder is ordinary geometry
   * anchored on the entity, so they already curve IN PLACE. Measured on device: video r = 1.75 with
   * danmaku r = 1.5 put the danmaku BEHIND the video, i.e. the -1.5 offset was pure error.
   */
  private fun applyStageLayerCurvatureOffset(
      entity: Entity,
      curvature: PlaybackStageCurvature,
      baseLocalZ: Float,
      offsetScale: Float,
  ) {
    val transform = entity.tryGetComponent<Transform>() ?: return
    val pose = transform.transform
    val targetZ = baseLocalZ + offsetScale * curvature.surfaceAnchorOffsetMeters()
    // These layers all sit on the stage-local axis and are separated only along Z, so x/y are part of
    // the contract: re-asserting them also repairs a transform that TransformParent re-derived from
    // the world pose when the entity was attached to the stage root.
    if (kotlin.math.abs(pose.t.x) < 0.0001f &&
        kotlin.math.abs(pose.t.y) < 0.0001f &&
        kotlin.math.abs(pose.t.z - targetZ) < 0.0001f) {
      return
    }
    pose.t = Vector3(0f, 0f, targetZ)
    entity.setComponent(Transform(pose))
  }

  private fun updateSpatialVideoContentQuad(
      videoWidth: Int,
      videoHeight: Int,
      pixelWidthHeightRatio: Float,
  ) {
    val mesh = spatialVideoTriangleMesh ?: return
    // All content geometry is derived from the SCALED stage footprint, so the persisted canvas
    // size is baked into the resulting mesh/shape rather than applied as an entity Scale.
    val stageWidth = scaledStageWidth()
    val stageHeight = scaledStageHeight()
    val diagnostic =
        spatialVideoAspectDiagnostic(
            stageWidth = stageWidth,
            stageHeight = stageHeight,
            videoWidth = videoWidth,
            videoHeight = videoHeight,
            pixelWidthHeightRatio = pixelWidthHeightRatio,
        )
    if (diagnostic == lastAspectDiagnostic) return
    val targetAspectRatio = resolvedSpatialVideoAspectRatio(diagnostic)
    val targetContent =
        spatialVideoContentQuadForAspect(
            stageWidth = stageWidth,
            stageHeight = stageHeight,
            displayAspectRatio = targetAspectRatio,
        )
    val stageHalfWidth = stageWidth / 2f
    val stageHalfHeight = stageHeight / 2f
    val shadowDepth = 0.1f
    // PLAN_1 rewrites the quad vertices in place, which is only valid while the surface really is
    // that four-vertex quad; a curved surface is a subdivided grid and always goes through a reshape.
    val rewriteQuadInPlace =
        spatialVideoAspectProbeState.appliedPlan == SpatialVideoAspectProbePlan.PLAN_1 &&
            appliedVideoCurvature is PlaybackStageCurvature.Flat
    if (rewriteQuadInPlace) {
      mesh.updateGeometry(
        0,
        floatArrayOf(
            // Content remains centered and contained in the applied target aspect ratio.
            -targetContent.halfWidth, -targetContent.halfHeight, 0f,
            targetContent.halfWidth, -targetContent.halfHeight, 0f,
            targetContent.halfWidth, targetContent.halfHeight, 0f,
            -targetContent.halfWidth, targetContent.halfHeight, 0f,
            // The shadow keeps the full stage footprint and existing panel input geometry.
            -stageHalfWidth, -stageHalfHeight, shadowDepth,
            stageHalfWidth, -stageHalfHeight, shadowDepth,
            stageHalfWidth, -stageHalfHeight, -shadowDepth,
            -stageHalfWidth, -stageHalfHeight, -shadowDepth,
        ),
        floatArrayOf(
            0f, 0f, 1f,
            0f, 0f, 1f,
            0f, 0f, 1f,
            0f, 0f, 1f,
            0f, 0f, 1f,
            0f, 0f, 1f,
            0f, 0f, 1f,
            0f, 0f, 1f,
        ),
        floatArrayOf(
            0f, 1f,
            1f, 1f,
            1f, 0f,
            0f, 0f,
            stageHalfWidth - 0.075f, shadowDepth - 0.075f,
            stageHalfWidth - 0.075f, shadowDepth - 0.075f,
            stageHalfWidth - 0.075f, shadowDepth - 0.075f,
            stageHalfWidth - 0.075f, shadowDepth - 0.075f,
        ),
        intArrayOf(
            Color.WHITE, Color.WHITE, Color.WHITE, Color.WHITE,
            Color.WHITE, Color.WHITE, Color.WHITE, Color.WHITE,
        ),
      )
    }
    if (!rewriteQuadInPlace) reshapeSpatialVideoPanel(targetContent)
    lastAspectDiagnostic = diagnostic
    syncSpatialVideoAspectProbeUi()
    if (BuildConfig.DEBUG) {
      Log.i(
          "ViriViriAspect",
          "video=${diagnostic.videoWidth}x${diagnostic.videoHeight} " +
              "pixelRatio=${diagnostic.pixelWidthHeightRatio} " +
              "sourceAspect=${diagnostic.displayAspectRatio} " +
              "targetAspect=$targetAspectRatio " +
              "quadHalf=${targetContent.halfWidth}x${targetContent.halfHeight} " +
                "panelShape=${targetContent.halfWidth * 2f}x${targetContent.halfHeight * 2f} " +
              "target=${spatialVideoAspectProbeState.appliedTarget.label} " +
              "plan=${spatialVideoAspectProbeState.appliedPlan.label}",
      )
    }
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
  private fun selectorPanelRegistration(): PanelRegistration {
    return ActivityPanelRegistration(
        R.id.video_selector_panel,
        classIdCreator = { MoviePanel::class.java },
        settingsCreator = {
          UIPanelSettings(
              // UX: the left Detail rail mirrors the right context rail width so the
              // two are vertically symmetric; its body scrolls within the shorter height.
              shape = QuadShapeOptions(width = layout.railWidth, height = layout.leftRailHeight),
              display = DpDisplayOptions(width = 361f, height = 464f, dpi = 800),
              input =
                  // want to disable left hand pinch so we can drag the panel around with hands
                  PanelInputOptions(
                      ButtonBits.ButtonA or ButtonBits.ButtonTriggerL or ButtonBits.ButtonTriggerR
                  ),
          )
        },
    )
  }

  // Passthrough (MR) panel
  private fun mrPanelRegistration(): PanelRegistration {
    return IntentPanelRegistration(
        registrationId = R.id.mr_panel,
        intentCreator = {
          Intent(spatialContext, MRPanel::class.java).apply {
            putExtra("isMrMode", scene.isSystemPassthroughEnabled().toString())
          }
        },
        settingsCreator = {
          UIPanelSettings(
              // UX: top-stack keeps GlobalNavigation and ContentNavigation in one existing Spatial panel.
              shape = QuadShapeOptions(width = 1.24f, height = 0.30f),
              display = DpDisplayOptions(width = 520f, height = 128f, dpi = 600),
          )
        },
    )
  }

  public fun setMrMode(isMrMode: Boolean) {
    // The panel is bound to the dedicated surface entity, not to the stage root; see
    // [videoSurfaceEntity].
    val videoPanelEntity = videoSurfaceEntity ?: Entity(R.id.spatialized_video_panel)

    if (isMrMode) {
      environmentGLXF?.setComponent(Visible(false))
      skydome?.setComponent(Visible(false))
    } else {
      environmentGLXF?.setComponent(Visible(true))
      skydome?.setComponent(Visible(true))
    }

    val sceneObjectSystem = systemManager.findSystem<SceneObjectSystem>()
    val sysObject = sceneObjectSystem.getSceneObject(videoPanelEntity)?.getNow(null)
    val panel = sysObject as PanelSceneObject?
    panel?.updateIsdkComponentProperties(videoPanelEntity)

    scene.enablePassthrough(isMrMode)
    // Locomotion is never re-enabled on a mode switch; see the scene-ready disable for why.
    avatarSystem.setShowControllers(!isMrMode)
    avatarSystem.setShowHands(!isMrMode)
    inMrMode = isMrMode
  }

  companion object {
    const val TAG = "SpatialVideoSampleActivity"
    const val WORKBENCH_TRACE_TAG = "ViriViriWorkbench"
    const val EXTRA_REATTACH_IMMERSIVE_OUTPUT =
        "com.m0e_n00b.viriviri.extra.REATTACH_IMMERSIVE_OUTPUT"
    lateinit var appContext: Context
    lateinit var appPackageName: String

    const val LIGHTS_UP_SCALE: Float = 1.0f
    const val LIGHTS_DOWN_SCALE: Float = 0.25f
    const val TRANSPORT_FADE_DURATION_MS: Long = 200L
    const val TRANSPORT_TIMELINE_UPDATE_INTERVAL_MS: Long = 500L
    const val OUTER_DISMISS_SUPPRESSION_MS: Long = 500L
    // Workbench spatial tuning lives in WorkbenchLayoutConfig (bind it to future
    // settings presets / preferences instead of hardcoding positions here).
    const val WRIST_DEBUG_PANEL_WIDTH: Float = 0.14f
    const val WRIST_DEBUG_PANEL_HEIGHT: Float = 0.05f

    /**
     * Position offset applied per kind of curved layer. The media panel's pixels come from a compositor
     * cylinder layer, whose surface sits one radius off the scene object's origin, so it must be moved
     * back. A UI-panel overlay's cylinder is ordinary geometry anchored on the entity: it curves in
     * place, and any offset just pushes it out of the layer stack.
     */
    const val MEDIA_PANEL_CURVATURE_OFFSET_SCALE: Float = 1f

    const val UI_PANEL_CURVATURE_OFFSET_SCALE: Float = 1f

    /**
     * Candidates for the danmaku layer's authored local Z, stepped by the debug rail.
     *
     * Device evidence for the sign: curving the danmaku with no offset at all drifted the whole layer by
     * +r (1.5 m at r = 1.5) toward +Z, decreasing with r. That is the same one-radius surface
     * displacement the media panel shows, so the UI-panel overlays DO need the offset — which is why
     * [UI_PANEL_CURVATURE_OFFSET_SCALE] is 1 and not 0. What remains open is only the small authored
     * separation: 1 cm is too little to settle the draw order against a compositor layer.
     */
    val DANMAKU_BASE_LOCAL_Z_CANDIDATES: FloatArray = floatArrayOf(-0.01f, 0.01f, -0.05f, 0.05f)

    /** Depth guard for world-pose resolution over the `TransformParent` chain. */
    const val MAX_PARENT_DEPTH: Int = 8

    /**
     * EXPERIMENT: explicit layer depth for the video surface.
     *
     * The video is the ONLY compositor layer in the scene. `MediaPanelRenderOptions.applyTo` calls
     * `setLayerConfig` unconditionally, while `QuadShapeOptions.applyTo` and
     * `CylinderShapeOptions.applyTo` only set glass / shape type / width / height / cylinder radius,
     * leaving the rails, the transport and the danmaku overlay as ordinary scene geometry. A compositor
     * layer is drawn over scene geometry, so the video covers whatever it overlaps however far in front
     * that content sits: the rail is about 1.05 m nearer and the danmaku about 1 cm nearer, and both
     * still lose.
     *
     * `enableLayer = false` was measured to change nothing (build F9E418AD2), so the layer is not
     * optional. `zIndex` is the remaining knob on the same options object and is what layer ordering is
     * for. If it takes effect, the layer should sort behind the scene content. If nothing changes it is
     * ignored too, and the remaining route is to stop using `MediaPanelSettings` for display altogether
     * and sample the forced `SceneTexture` on our own mesh.
     */
    const val VIDEO_LAYER_Z_INDEX: Int = -1

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
