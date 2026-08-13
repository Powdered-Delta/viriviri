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
import android.os.CountDownTimer
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
  lateinit var controlsFadeOutTimer: CountDownTimer
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
  private var spatialVideoSceneMesh: SceneMesh? = null
  private var spatialVideoAspectProbeMode = SpatialVideoAspectProbeMode.GEOMETRY_ONLY
  private var lastAspectDiagnostic: SpatialVideoAspectDiagnostic? = null
  private var wristDebugPanelEntity: Entity? = null
  private lateinit var spatialPanelVisibilityController: SpatialPanelVisibilityController
  private lateinit var immersivePlaybackCanvasHost: ImmersivePlaybackCanvasHost
  lateinit var audio: SceneAudioAsset
  var seekBar: CompletableFuture<SeekBar> = CompletableFuture<SeekBar>()
  var elapsedTime: CompletableFuture<TextView> = CompletableFuture<TextView>()
  var durationTime: CompletableFuture<TextView> = CompletableFuture<TextView>()
  var currentMediaTitle: CompletableFuture<TextView> = CompletableFuture<TextView>()
  var currentMediaDetail: CompletableFuture<TextView> = CompletableFuture<TextView>()
  var retryMediaButton: CompletableFuture<Button> = CompletableFuture<Button>()
  var debugAspectDetail: CompletableFuture<TextView> = CompletableFuture<TextView>()
  var debugAspectProbeButton: CompletableFuture<Button> = CompletableFuture<Button>()
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
  private var browseSelectionBaselineId: String? = null
  private var awaitingBrowseSelection = false
  private var browseSelectionObserver: Job? = null
  private var shouldReattachImmersiveOutput = false
  private lateinit var immersiveMediaStageHost: ImmersiveMediaStageHost<Surface>
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
    val features = mutableListOf<SpatialFeature>(VRFeature(this))
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
    spatialPanelVisibilityController = SpatialPanelVisibilityController(canvasHandler)
    immersivePlaybackCanvasHost =
        ImmersivePlaybackCanvasHost(applyVisibleSlots = ::applyPlaybackCanvasSlots)
    player.addListener(immersiveStagePlayerListener)
    player.addListener(immersiveControlsPlayerListener)
    browseSelectionObserver =
        activityScope.launch {
          ViriViriApplication.appState.state.collect { appState ->
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
            if (::immersivePlaybackCanvasHost.isInitialized &&
                shouldReturnToPlaybackAfterBrowseSelection(
                    awaitingSelection = awaitingBrowseSelection,
                    canvas = immersivePlaybackCanvasHost.state.canvas,
                    baselineVideoId = browseSelectionBaselineId,
                    selectedVideoId = appState.selected?.videoId,
                )) {
              awaitingBrowseSelection = false
              browseSelectionBaselineId = null
              dispatchPlaybackCanvas(PlaybackCanvasEvent.OpenPlayback)
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

    controlsFadeOutTimer =
        object : CountDownTimer(TRANSPORT_IDLE_TIMEOUT_MS, TRANSPORT_IDLE_TIMEOUT_MS) {
          override fun onTick(millisUntilFinished: Long) {}

          override fun onFinish() {
            animateControllerVisibility(false)
            dispatchPlaybackCanvas(PlaybackCanvasEvent.IdleTimeout)
          }
        }

    loadGLXF { composition ->
      environmentGLXF = composition.getNodeByName("MediaRoom").entity
      environmentGLXF?.let {
        val environmentMesh = it.getComponent<Mesh>()
        it.setComponent(
            environmentMesh.apply { defaultShaderOverride = SceneMaterial.UNLIT_SHADER }
        )
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
                  Transform(Pose(Vector3(0.0f, -0.6f, -0.1f))),
                  TransformParent(Entity(R.id.video_selector_panel)),
              )
          )
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
      immersivePlaybackCanvasHost.applyInitialState()
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
            spatialVideoSceneMesh = sceneMesh
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
                  val canvasBeforeAction =
                      if (::immersivePlaybackCanvasHost.isInitialized) immersivePlaybackCanvasHost.state.canvas else null
                  dispatchPlaybackCanvas(PlaybackCanvasEvent.PrimaryStageAction)
                  if (canvasBeforeAction == PlaybackCanvas.PLAYBACK) {
                    togglePlay()
                  } else {
                    showTransportOverlay()
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
              shape = QuadShapeOptions(width = 0.8f, height = 0.25f),
              display = DpDisplayOptions(width = 275.2f, height = 86f, dpi = 600),
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
          setupHoverAndTouchListeners(controllerView)
          applyTransportOverlayVisibility(transportOverlayState.visible)
        },
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
                      height = if (isDebugPanel) 0.65f else 0.35f,
                  ),
              display =
                  DpDisplayOptions(
                      width = if (isDebugPanel) 420f else 280f,
                      height = if (isDebugPanel) 300f else 140f,
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
          val debugBuildLabel = rootView.findViewById<TextView>(R.id.debug_build_label)
          if (BuildConfig.DEBUG) {
            debugBuildLabel.text = "DEV ${BuildConfig.GIT_SHA}"
            debugBuildLabel.visibility = View.VISIBLE
          }
          val debugAspectDetailLocal = rootView.findViewById<TextView>(R.id.debug_aspect_detail)
          val debugAspectProbeButtonLocal = rootView.findViewById<Button>(R.id.debug_aspect_probe_button)
          debugAspectDetail.complete(debugAspectDetailLocal)
          debugAspectProbeButton.complete(debugAspectProbeButtonLocal)
          if (BuildConfig.DEBUG) {
            debugAspectDetailLocal.visibility = View.VISIBLE
            debugAspectProbeButtonLocal.visibility = View.VISIBLE
            syncSpatialVideoAspectProbeUi()
            debugAspectProbeButtonLocal.setOnClickListener {
              showSpatialVideoAspectProbeMenu(debugAspectProbeButtonLocal)
            }
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
    panel?.let(::attachImmersiveOutput)
    if (::immersivePlaybackCanvasHost.isInitialized) immersivePlaybackCanvasHost.applyCurrentState()
  }

  override fun onDestroy() {
    player.removeListener(immersiveStagePlayerListener)
    player.removeListener(immersiveControlsPlayerListener)
    browseSelectionObserver?.cancel()
    browseSelectionObserver = null
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
    if (ImmersiveTransportOverlayPolicy.shouldScheduleIdleFade(isPlaying)) {
      controlsFadeOutTimer.cancel()
      controlsFadeOutTimer.start()
      if (!transportOverlayState.visible && alphaAnimator?.isRunning != true) {
        animateControllerVisibility(true)
      }
    }
  }

  private fun dispatchPlaybackCanvas(event: PlaybackCanvasEvent) {
    if (::immersivePlaybackCanvasHost.isInitialized) immersivePlaybackCanvasHost.dispatch(event)
  }

  private fun openBrowseCanvas() {
    val appState = ViriViriApplication.appState
    browseSelectionBaselineId = appState.state.value.selected?.videoId
    awaitingBrowseSelection = true
    appState.returnToRecommendations()
    dispatchPlaybackCanvas(PlaybackCanvasEvent.OpenBrowse)
  }

  private fun applyPlaybackCanvasSlots(visibleSlots: Set<PanelSlot>) {
    if (!::spatialPanelVisibilityController.isInitialized) return
    val slotEntities =
        mapOf(
            PanelSlot.TRANSPORT to Entity(R.id.controls_id),
            PanelSlot.SYSTEM_TOOLBAR to Entity(R.id.mode_panel),
            PanelSlot.BROWSE to Entity(R.id.video_selector_panel),
        )
    slotEntities.forEach { (slot, entity) ->
      spatialPanelVisibilityController.setVisible(slot, entity, slot in visibleSlots)
    }
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
    volumeButton.thenAccept { it.text = PlaybackVolumeControl.label(player.volume) }
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

  private fun showSpatialVideoAspectProbeMenu(anchor: View) {
    PopupMenu(this, anchor).apply {
      SpatialVideoAspectProbeMode.entries.forEachIndexed { index, mode ->
        menu.add(0, index, index, mode.label).isChecked = mode == spatialVideoAspectProbeMode
      }
      menu.setGroupCheckable(0, true, true)
      setOnMenuItemClickListener { item: MenuItem ->
        val mode = SpatialVideoAspectProbeMode.entries.getOrNull(item.itemId)
            ?: return@setOnMenuItemClickListener false
        spatialVideoAspectProbeMode = mode
        lastAspectDiagnostic = null
        updateSpatialVideoContentQuad(
            videoWidth = player.videoSize.width,
            videoHeight = player.videoSize.height,
            pixelWidthHeightRatio = player.videoSize.pixelWidthHeightRatio,
        )
        syncSpatialVideoAspectProbeUi()
        true
      }
      show()
    }
  }

  private fun syncSpatialVideoAspectProbeUi() {
    debugAspectProbeButton.thenAccept { it.text = "Aspect probe: ${spatialVideoAspectProbeMode.label}" }
    val diagnostic = lastAspectDiagnostic
    debugAspectDetail.thenAccept { detail ->
      detail.text =
          if (diagnostic == null) {
            "No VideoSize geometry yet"
          } else {
            "${diagnostic.videoWidth}x${diagnostic.videoHeight} " +
                "aspect=${diagnostic.displayAspectRatio} " +
                "quad=${diagnostic.contentHalfWidth}x${diagnostic.contentHalfHeight}"
          }
    }
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
    val stageHalfWidth = MR_SCREEN_WIDTH / 2f
    val stageHalfHeight = MR_SCREEN_HEIGHT / 2f
    val shadowDepth = 0.1f
    mesh.updateGeometry(
        0,
        floatArrayOf(
            // Content remains centered and contained in the fixed stage.
            -diagnostic.contentHalfWidth, -diagnostic.contentHalfHeight, 0f,
            diagnostic.contentHalfWidth, -diagnostic.contentHalfHeight, 0f,
            diagnostic.contentHalfWidth, diagnostic.contentHalfHeight, 0f,
            -diagnostic.contentHalfWidth, diagnostic.contentHalfHeight, 0f,
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
    when (spatialVideoAspectProbeMode) {
      SpatialVideoAspectProbeMode.GEOMETRY_ONLY -> Unit
      SpatialVideoAspectProbeMode.COMMIT_FALSE ->
          spatialVideoSceneMesh?.updateWithTriangleMesh(mesh, false)
      SpatialVideoAspectProbeMode.COMMIT_TRUE ->
          spatialVideoSceneMesh?.updateWithTriangleMesh(mesh, true)
    }
    lastAspectDiagnostic = diagnostic
    syncSpatialVideoAspectProbeUi()
    if (BuildConfig.DEBUG) {
      Log.i(
          "ViriViriAspect",
          "video=${diagnostic.videoWidth}x${diagnostic.videoHeight} " +
              "pixelRatio=${diagnostic.pixelWidthHeightRatio} " +
              "displayAspect=${diagnostic.displayAspectRatio} " +
              "quadHalf=${diagnostic.contentHalfWidth}x${diagnostic.contentHalfHeight} " +
              "probe=${spatialVideoAspectProbeMode.label}",
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
          if (state.showPauseIcon) R.drawable.pause else R.drawable.play,
          0,
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
      controlsFadeOutTimer.cancel()
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
              shape = QuadShapeOptions(width = 0.6f, height = 0.2f),
              display = DpDisplayOptions(width = 165.12f, height = 55.04f, dpi = 400),
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
    const val EXTRA_REATTACH_IMMERSIVE_OUTPUT =
        "com.m0e_n00b.viriviri.extra.REATTACH_IMMERSIVE_OUTPUT"
    lateinit var appContext: Context
    lateinit var appPackageName: String

    const val LIGHTS_UP_SCALE: Float = 1.0f
    const val LIGHTS_DOWN_SCALE: Float = 0.25f
    const val TRANSPORT_IDLE_TIMEOUT_MS: Long = 4_000L
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
