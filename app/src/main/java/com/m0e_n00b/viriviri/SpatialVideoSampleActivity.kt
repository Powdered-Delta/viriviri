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
import com.m0e_n00b.spatialworkbench.core.PanelSlot
import com.m0e_n00b.spatialworkbench.core.PlaybackCanvas
import com.m0e_n00b.spatialworkbench.core.PlaybackCanvasEvent
import android.util.Log
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
import com.meta.spatial.isdk.IsdkGrabbable
import com.meta.spatial.isdk.IsdkPanelDimensions
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
import com.meta.spatial.toolkit.DpDisplayOptions
import com.meta.spatial.toolkit.GLXFInfo
import com.meta.spatial.toolkit.Grabbable
import com.meta.spatial.toolkit.GrabbableType
import com.meta.spatial.toolkit.Hittable
import com.meta.spatial.toolkit.IntentPanelRegistration
import com.meta.spatial.toolkit.LayoutXMLPanelRegistration
import com.meta.spatial.toolkit.Material
import com.meta.spatial.toolkit.MediaPanelRenderOptions
import com.meta.spatial.toolkit.MediaPanelSettings
import com.meta.spatial.toolkit.Mesh
import com.meta.spatial.toolkit.MeshCollision
import com.meta.spatial.toolkit.Panel
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
  private var spatialVideoAspectProbeState = SpatialVideoAspectProbeState()
  private var lastAspectDiagnostic: SpatialVideoAspectDiagnostic? = null
  private var wristDebugPanelEntity: Entity? = null
  private var danmakuOverlayEntity: Entity? = null
  private var stageBackdropEntity: Entity? = null
  private var centerContentEntity: Entity? = null
  private var inputMethodPanelEntity: Entity? = null
  private var outerDismissEntity: Entity? = null
  private var outerDismissInputAttached = false
  private var hasWorkbenchDataSource = false
  private var appliedCanvasSize: PlaybackCanvasSize? = null
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
            applyPlaybackCanvasSize(appState.playbackCanvasSize)
            syncInputMethodPanelVisibility(appState)
            val transition =
                ImmersiveBrowseSessionReducer.onAppState(
                    session = immersiveBrowseSession,
                    canvas = immersivePlaybackCanvasHost.state.canvas,
                    destination = appState.destination,
                )
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

    // locomotion system interferes sometimes with scrolling on panels, disabling for now
    // not needed in MR mode anyways
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
    )
        .apply {
          if (BuildConfig.DEBUG) add(wristDebugPanelRegistration())
          if (DEBUG) add(debugPanelRegistration())
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

    scene.isSystemPassthroughEnabled().let { isMrMode ->
      scene.enablePassthrough(isMrMode)
      locomotionSystem.enableLocomotion(!isMrMode)
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
  }

  private fun attachOuterDismissInput() {
    if (outerDismissInputAttached) return
    val entity = outerDismissEntity ?: return
    entity.setComponent(Hittable())
    // UX: this is input-only scene geometry; never render its MSE primitive over the Workbench.
    entity.setComponent(Visible(false))
    systemManager.findSystem<SceneObjectSystem>().getSceneObject(entity)?.thenAccept { sceneObject ->
      sceneObject.addInputListener(
          object : InputListener {
            override fun onClick(
                receiver: SceneObject,
                hitInfo: HitInfo,
                sourceOfInput: Entity,
            ) {
              if (::immersiveWorkbenchHost.isInitialized && immersiveWorkbenchHost.state.visible) {
                dismissWorkbenchFromCenterContent()
              }
            }
          }
      )
      outerDismissInputAttached = true
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
      Entity(R.id.spatialized_video_panel)
          .setComponents(
              listOf(
                  Grabbable(type = GrabbableType.PIVOT_Y, minHeight = 0.75f, maxHeight = 2.5f),
                  SpatializedAudioPanel(),
                  Transform(initialPose * Pose(Vector3(0f, 1.25f, 2f), Quaternion(0f, 0f, 0f))),
              )
          )
      Entity(R.id.video_selector_panel)
          .setComponents(
              listOf(
                  Grabbable(),
                  Panel(R.id.video_selector_panel),
                  Transform(
                      initialPose *
                          Pose(
                              Vector3(-1.212f, 1.25f, 0.988f),
                              Quaternion(0f, -45f, 0f),
                          )
                  ),
              )
          )
      Entity(R.id.controls_id)
          .setComponents(
              listOf(
                  Panel(R.id.controls_id),
                  Transform(Pose(Vector3(0.0f, -0.6f, -0.15f), Quaternion(20f, 0f, 0f))),
                  TransformParent(Entity(R.id.spatialized_video_panel)),
              )
          )
      Entity(R.id.mr_panel)
          .setComponents(
              listOf(
                  Panel(R.id.mr_panel),
                  Transform(Pose(Vector3(0.0f, 0.72f, -0.12f), Quaternion(8f, 0f, 0f))),
                  TransformParent(Entity(R.id.spatialized_video_panel)),
              )
          )
      bindCenterContentPanel()
      Entity(R.id.mode_panel)
          .setComponents(
              listOf(
                  Grabbable(),
                  Panel(R.id.mode_panel),
                  Transform(
                      initialPose * Pose(Vector3(1.0f, 1.25f, 1.0f), Quaternion(0f, 45f, 0f))
                  ),
              )
          )
      environmentGLXF?.setComponents(listOf(Visible(false), Transform(initialPose)))
      if (DEBUG) {
        Entity(R.id.debug_panel)
            .setComponents(
                listOf(
                    Grabbable(),
                    Panel(R.id.debug_panel),
                    Transform(initialPose * Pose(Vector3(1f, 1.25f, 1f), Quaternion(0f, 45f, 0f))),
                )
            )
      }
      mrPanelPose = Entity(R.id.spatialized_video_panel).getComponent<Transform>().transform
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

  // Video Panel
  @androidx.annotation.OptIn(UnstableApi::class)
  private fun createVideoPanel() {
    val videoPanelEntity = Entity(R.id.spatialized_video_panel)
    val settings = MediaPanelSettings(
        shape = QuadShapeOptions(width = MR_SCREEN_WIDTH, height = MR_SCREEN_HEIGHT),
        display =
            PixelDisplayOptions(
                width = IMMERSIVE_VIDEO_OUTPUT_WIDTH,
                height = IMMERSIVE_VIDEO_OUTPUT_HEIGHT,
            ),
        rendering = MediaPanelRenderOptions(stereoMode = StereoMode.None),
    )
    val panelSceneObject = PanelSceneObject(
        scene,
        videoPanelEntity,
        settings.toPanelConfigOptions().apply {
          sceneMeshCreator = { texture: SceneTexture ->
            val halfHeight = height / 2f
            val halfWidth = width / 2f
            val halfDepth = 0.1f
            val rounding = 0.075f
            val triMesh = TriangleMesh(
                8,
                18,
                intArrayOf(6, 6, 12, 6, 0, 6),
                arrayOf(
                    SceneMaterial(
                        texture,
                        AlphaMode.TRANSLUCENT,
                        "data/shaders/spatial/reflect",
                    )
                        .apply {
                          setStereoMode(stereoMode)
                          setUnlit(true)
                        },
                    SceneMaterial(
                        texture,
                        AlphaMode.TRANSLUCENT,
                        "data/shaders/spatial/shadow",
                    )
                        .apply { setUnlit(true) },
                    SceneMaterial(
                        texture,
                        AlphaMode.HOLE_PUNCH,
                        SceneMaterial.HOLE_PUNCH_SHADER,
                    )
                        .apply {
                          setStereoMode(stereoMode)
                          setUnlit(true)
                        },
                ),
            )
            triMesh.updateGeometry(
                0,
                floatArrayOf(
                    // Video quad; dimensions are updated from Media3 VideoSize.
                    -halfWidth, -halfHeight, 0f,
                    halfWidth, -halfHeight, 0f,
                    halfWidth, halfHeight, 0f,
                    -halfWidth, halfHeight, 0f,
                    // Existing shadow footprint.
                    -halfWidth, -halfHeight, halfDepth,
                    halfWidth, -halfHeight, halfDepth,
                    halfWidth, -halfHeight, -halfDepth,
                    -halfWidth, -halfHeight, -halfDepth,
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
                    // front
                    0f, 1f,
                    1f, 1f,
                    1f, 0f,
                    0f, 0f,
                    // shadow
                    halfWidth - rounding, halfDepth - rounding,
                    halfWidth - rounding, halfDepth - rounding,
                    halfWidth - rounding, halfDepth - rounding,
                    halfWidth - rounding, halfDepth - rounding,
                ),
                intArrayOf(
                    Color.WHITE, Color.WHITE, Color.WHITE, Color.WHITE,
                    Color.WHITE, Color.WHITE, Color.WHITE, Color.WHITE,
                ),
            )
            triMesh.updatePrimitives(
                0,
                intArrayOf(0, 1, 2, 0, 2, 3, 0, 2, 1, 0, 3, 2, 4, 6, 5, 4, 7, 6),
            )
            val sceneMesh = SceneMesh.fromTriangleMesh(triMesh, false)
            spatialVideoTriangleMesh = triMesh
            updateSpatialVideoContentQuad(
                videoWidth = player.videoSize.width,
                videoHeight = player.videoSize.height,
                pixelWidthHeightRatio = player.videoSize.pixelWidthHeightRatio,
            )
            sceneMesh
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
                ) {
                  openPlaybackCanvasIfQuiet()
                  showTransportOverlay()
                }

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
                  if (::immersiveWorkbenchHost.isInitialized && immersiveWorkbenchHost.state.visible) {
                    dispatchPlaybackCanvas(PlaybackCanvasEvent.Dismiss)
                    animateControllerVisibility(false)
                  } else {
                    dispatchPlaybackCanvas(PlaybackCanvasEvent.PrimaryStageAction)
                    when (ImmersiveTransportOverlayPolicy.primaryAction()) {
                      ImmersiveTransportPrimaryAction.REVEAL_TRANSPORT -> showTransportOverlay()
                    }
                  }
                }

                override fun onInput(
                    receiver: SceneObject,
                    hitInfo: HitInfo,
                    sourceOfInput: Entity,
                    changed: Int,
                    clicked: Int,
                    downTime: Long,
                ): Boolean {
                  resetControllerFadeOutTimer()
                  return false
                }
              }
          )

        }

    spatialVideoPanelSceneObject = panelSceneObject
    attachImmersiveOutput(panelSceneObject)

    systemManager
        .findSystem<SceneObjectSystem>()
        .addSceneObject(
            videoPanelEntity,
            CompletableFuture<SceneObject>().apply { complete(panelSceneObject) },
        )
    // mark the mesh as explicitly able to catch input
    videoPanelEntity.setComponent(Hittable())

    // Usually, ISDK is able to create panel dimensions from a Panel component. Since the video
    // player manually constructs the PanelSceneObject, we need to manually set the panel
    // dimensions & keep them up to date when switching MR modes.
    videoPanelEntity.setComponent(IsdkPanelDimensions())
    videoPanelEntity.setComponent(IsdkPanelGrabHandle())
    videoPanelEntity.setComponent(IsdkGrabbable())
    panelSceneObject.updateIsdkComponentProperties(videoPanelEntity)

    // The mesh creator can run before the PanelSceneObject reference is available for reshape.
    lastAspectDiagnostic = null
    updateSpatialVideoContentQuad(
        videoWidth = player.videoSize.width,
        videoHeight = player.videoSize.height,
        pixelWidthHeightRatio = player.videoSize.pixelWidthHeightRatio,
    )
  }

  private fun debugPanelRegistration(): PanelRegistration {
    return LayoutXMLPanelRegistration(
        R.id.debug_panel,
        layoutIdCreator = { R.layout.debug },
        settingsCreator = {
          UIPanelSettings(
              shape = QuadShapeOptions(width = 0.8f, height = 0.45f),
              display = DpDisplayOptions(width = 275.2f, height = 155.2f, dpi = 600),
              style = PanelStyleOptions(themeResourceId = R.style.PanelAppThemeTransparent),
          )
        },
        panelSetupWithRootView = { rootView, _, _ ->
          val scaleText = rootView.findViewById<TextView>(R.id.scale_text)
          val scaleBar = rootView.findViewById<SeekBar>(R.id.scale_bar)
          val scaleMax = scaleBar?.max ?: 1
          scaleBar?.setOnSeekBarChangeListener(
              object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seek: SeekBar, progress: Int, fromUser: Boolean) {
                  if (fromUser) {
                    // in range [0, 1]
                    val normalized = progress.toDouble() / scaleMax
                    // in range [-1.5, 1.5]
                    val expRange = normalized * 3 - 1.5
                    val newScale = Math.pow(10.0, expRange).toFloat()
                    scaleText?.text = "Scale: %.2f".format(newScale)
                    Entity(R.id.spatialized_video_panel).setComponent(Scale(newScale))
                  }
                }

                override fun onStartTrackingTouch(seekBar: SeekBar) = Unit

                override fun onStopTrackingTouch(seekBar: SeekBar) = Unit
              }
          )
        },
    )
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
                ImmersiveCenterContentPanel(
                    onVideoSelected = ::returnToPlaybackFromCenterContent,
                    onDismissWorkbench = ::dismissWorkbenchFromCenterContent,
                )
              }
            }
          },
          settingsCreator = {
            UIPanelSettings(
                shape = QuadShapeOptions(width = 1.5f, height = 0.84f),
                // UX: center Search/List stays readable at three columns without retaining a high-resolution panel buffer.
                display = DpDisplayOptions(width = 768f, height = 430f, dpi = 512),
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
          settingsCreator = { stageOverlayPanelSettings(MR_SCREEN_WIDTH, MR_SCREEN_HEIGHT) },
      )

  private fun danmakuOverlayPanelRegistration(): PanelRegistration =
      ComposeViewPanelRegistration(
          R.id.danmaku_overlay_panel,
          composeViewCreator = { _, context -> ComposeView(context).apply { setContent { DanmakuOverlay() } } },
          settingsCreator = {
            stageOverlayPanelSettings(MR_SCREEN_WIDTH, MR_SCREEN_HEIGHT)
          },
      )

  private fun stageOverlayPanelSettings(width: Float, height: Float) =
      UIPanelSettings(
          shape = QuadShapeOptions(width = width, height = height),
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
                Transform(Pose(Vector3(0f, -0.48f, -0.42f), Quaternion(20f, 0f, 0f))),
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
                Transform(Pose(Vector3(0f, 0f, 0.01f))),
                TransformParent(Entity(R.id.spatialized_video_panel)),
                // Disabled until a uniform Spatial material replaces compositor-dithered UI alpha.
                Visible(false),
            )
            // PanelInputOptions disables buttons but does not disable the panel's raycast collider.
            .also { it.setComponent(Panel(R.id.stage_backdrop_panel, MeshCollision.NoCollision)) }
  }

  private fun createDanmakuOverlayPanel() {
    if (danmakuOverlayEntity != null) return
    danmakuOverlayEntity =
        Entity.createPanelEntity(
                R.id.danmaku_overlay_panel,
                Transform(Pose(Vector3(0f, 0f, -0.01f))),
                TransformParent(Entity(R.id.spatialized_video_panel)),
                Visible(true),
            )
            // The overlay must render but never block the stage's controller/hand raycasts.
            .also { it.setComponent(Panel(R.id.danmaku_overlay_panel, MeshCollision.NoCollision)) }
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
          val isDebugPanel = BuildConfig.DEBUG
          UIPanelSettings(
              shape =
                  QuadShapeOptions(
                      width = if (isDebugPanel) 1.0f else 0.7f,
                      height = if (isDebugPanel) 0.9f else 0.58f,
                  ),
              display =
                  DpDisplayOptions(
                      width = if (isDebugPanel) 420f else 280f,
                      height = if (isDebugPanel) 430f else 230f,
                      dpi = 600,
                  ),
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
        systemManager.findSystem<SceneObjectSystem>()
            .getSceneObject(Entity(R.id.spatialized_video_panel))
            ?.getNow(null) as? PanelSceneObject
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

  private fun dispatchPlaybackCanvas(event: PlaybackCanvasEvent) {
    if (::immersivePlaybackCanvasHost.isInitialized) immersivePlaybackCanvasHost.dispatch(event)
  }

  fun openHomeCanvas() {
    CenterContentSession.show(CenterContentMode.VIDEO_LIST)
    val appState = ViriViriApplication.appState
    val wasShowingSearchResults = appState.state.value.isShowingSearchResults
    immersiveBrowseSession = ImmersiveBrowseSessionReducer.open(appState.state.value.selected?.videoId)
    appState.closeSearchWorkspace()
    if (wasShowingSearchResults) appState.returnToRecommendationsFeed()
    else appState.returnToRecommendations()
    dispatchPlaybackCanvas(PlaybackCanvasEvent.OpenBrowse)
  }

  fun openBrowseCanvas() {
    CenterContentSession.show(CenterContentMode.VIDEO_LIST)
    val appState = ViriViriApplication.appState
    immersiveBrowseSession = ImmersiveBrowseSessionReducer.open(appState.state.value.selected?.videoId)
    appState.closeSearchWorkspace()
    appState.returnToRecommendations()
    dispatchPlaybackCanvas(PlaybackCanvasEvent.OpenBrowse)
  }

  fun openSearchCanvas() {
    CenterContentSession.show(CenterContentMode.SEARCH)
    val appState = ViriViriApplication.appState
    immersiveBrowseSession = ImmersiveBrowseSessionReducer.open(appState.state.value.selected?.videoId)
    appState.openSearchWorkspace()
    dispatchPlaybackCanvas(PlaybackCanvasEvent.OpenBrowse)
  }

  private fun returnToPlaybackFromCenterContent() {
    // UX: selection hides the center layer immediately; the existing app state continues sole-player playback resolution.
    CenterContentSession.show(CenterContentMode.PLAYBACK)
    immersiveBrowseSession = ImmersiveBrowseSessionReducer.cancel(immersiveBrowseSession).session
    dispatchPlaybackCanvas(PlaybackCanvasEvent.OpenPlayback)
  }

  fun closeCenterContentFromNavigation() {
    // UX: ContentNavigation Back closes the current center route before touching the wider Workbench canvas.
    returnToPlaybackFromCenterContent()
  }

  private fun dismissWorkbenchFromCenterContent() {
    // UX: non-action center content clicks share the established canvas dismissal behavior.
    CenterContentSession.show(CenterContentMode.PLAYBACK)
    immersiveBrowseSession = ImmersiveBrowseSessionReducer.cancel(immersiveBrowseSession).session
    dispatchPlaybackCanvas(PlaybackCanvasEvent.Dismiss)
    animateControllerVisibility(false)
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

  private fun applyPlaybackCanvasSize(canvasSize: PlaybackCanvasSize) {
    if (appliedCanvasSize == canvasSize) return
    appliedCanvasSize = canvasSize
    // UX: canvas size scales the one existing MediaStage; a future curved canvas replaces only this presentation adapter.
    Entity(R.id.spatialized_video_panel).setComponent(Scale(canvasSize.scale))
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

  private fun showPlaybackDisplayRatioMenu(anchor: View) {
    PopupMenu(this, anchor).apply {
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
    PopupMenu(this, anchor).apply {
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
    PopupMenu(this, anchor).apply {
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
    PopupMenu(this, anchor).apply {
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
    PopupMenu(this, anchor).apply {
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
    PopupMenu(this, anchor).apply {
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
    val shapeWidth = content.halfWidth * 2f
    val shapeHeight = content.halfHeight * 2f
    panel.reshape(
        MediaPanelSettings(
                shape = QuadShapeOptions(width = shapeWidth, height = shapeHeight),
                display =
                    PixelDisplayOptions(
                        width = IMMERSIVE_VIDEO_OUTPUT_WIDTH,
                        height = IMMERSIVE_VIDEO_OUTPUT_HEIGHT,
                    ),
                rendering = MediaPanelRenderOptions(stereoMode = StereoMode.None),
            )
            .toPanelConfigOptions()
    )
    panel.updateIsdkComponentProperties(Entity(R.id.spatialized_video_panel))
    reshapeStageOverlay(Entity(R.id.stage_backdrop_panel), shapeWidth, shapeHeight)
    reshapeStageOverlay(Entity(R.id.danmaku_overlay_panel), shapeWidth, shapeHeight)
    if (BuildConfig.DEBUG) {
      Log.i("ViriViriAspect", "isdkPanelDimensions=$shapeWidth x $shapeHeight")
    }
  }

  private fun reshapeStageOverlay(entity: Entity, width: Float, height: Float) {
    val overlay =
        systemManager.findSystem<SceneObjectSystem>()
            .getSceneObject(entity)
            ?.getNow(null) as? PanelSceneObject
        ?: return
    overlay.reshape(
        stageOverlayPanelSettings(width, height).toPanelConfigOptions()
    )
  }

  private fun updateSpatialVideoContentQuad(
      videoWidth: Int,
      videoHeight: Int,
      pixelWidthHeightRatio: Float,
  ) {
    val mesh = spatialVideoTriangleMesh ?: return
    val diagnostic =
        spatialVideoAspectDiagnostic(
            stageWidth = MR_SCREEN_WIDTH,
            stageHeight = MR_SCREEN_HEIGHT,
            videoWidth = videoWidth,
            videoHeight = videoHeight,
            pixelWidthHeightRatio = pixelWidthHeightRatio,
        )
    if (diagnostic == lastAspectDiagnostic) return
    val targetAspectRatio = resolvedSpatialVideoAspectRatio(diagnostic)
    val targetContent =
        spatialVideoContentQuadForAspect(
            stageWidth = MR_SCREEN_WIDTH,
            stageHeight = MR_SCREEN_HEIGHT,
            displayAspectRatio = targetAspectRatio,
        )
    val stageHalfWidth = MR_SCREEN_WIDTH / 2f
    val stageHalfHeight = MR_SCREEN_HEIGHT / 2f
    val shadowDepth = 0.1f
    if (spatialVideoAspectProbeState.appliedPlan == SpatialVideoAspectProbePlan.PLAN_1) {
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
    when (spatialVideoAspectProbeState.appliedPlan) {
      SpatialVideoAspectProbePlan.PLAN_1 -> Unit
      SpatialVideoAspectProbePlan.PANEL_RESHAPE -> reshapeSpatialVideoPanel(targetContent)
    }
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
    PopupMenu(this, anchor).apply {
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
              shape = QuadShapeOptions(width = 1.2f, height = 2.0f),
              display = DpDisplayOptions(width = 619.2f, height = 1032f, dpi = 800),
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
    val videoPanelEntity = Entity(R.id.spatialized_video_panel)
    val grabbable = videoPanelEntity.tryGetComponent<Grabbable>() ?: Grabbable()
    grabbable.enabled = isMrMode
    videoPanelEntity.setComponent(grabbable)

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
    locomotionSystem.enableLocomotion(!isMrMode)
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
    const val WRIST_DEBUG_PANEL_WIDTH: Float = 0.14f
    const val WRIST_DEBUG_PANEL_HEIGHT: Float = 0.05f

    const val MR_SCREEN_WIDTH: Float = 16.0f / 10.0f
    const val MR_SCREEN_HEIGHT: Float = 9.0f / 10.0f
    const val VR_SCREEN_RATIO: Float = 2.5f

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
