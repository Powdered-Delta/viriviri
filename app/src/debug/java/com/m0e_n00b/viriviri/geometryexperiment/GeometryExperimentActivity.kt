package com.m0e_n00b.viriviri.geometryexperiment

import android.content.Context
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import com.m0e_n00b.viriviri.R
import com.meta.spatial.core.Entity
import com.meta.spatial.core.Pose
import com.meta.spatial.core.SpatialFeature
import com.meta.spatial.core.Vector3
import com.meta.spatial.runtime.PanelSceneObject
import com.meta.spatial.runtime.DepthWrite
import com.meta.spatial.runtime.PanelShapeLayerBlendType
import com.meta.spatial.runtime.ReferenceSpace
import com.meta.spatial.runtime.SceneMaterial
import com.meta.spatial.runtime.SceneObject
import com.meta.spatial.runtime.StereoMode
import com.meta.spatial.toolkit.*
import com.meta.spatial.vr.VRFeature
import java.util.concurrent.CompletableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/** Deliberately direct SDK experiment: no production playback imports or geometry updates. */
@androidx.annotation.OptIn(UnstableApi::class)
class GeometryExperimentActivity : AppSystemActivity() {
  private enum class Preset(val title: String, val geometry: ExperimentGeometry) {
    FLAT("平面", ExperimentGeometry(160, null)),
    CURVED("曲面", ExperimentGeometry(160, 100)),
    LARGE("放大曲面", ExperimentGeometry(240, 100));

    fun describe() = "$title  ${geometry.describe()}"
  }

  private data class Display(
      val generation: Long,
      val entity: Entity,
      val panel: PanelSceneObject,
      val geometry: ExperimentGeometry,
  )
  private class Overlay {
    var entity: Entity? = null
    var view: ExperimentOverlayView? = null
    var panel: PanelSceneObject? = null
    var registered = false
  }
  private val overlays = ExperimentRetainedOverlays(
      allocate = { _: ExperimentLayer -> Overlay() },
      update = ::updateOverlay,
      dispose = ::destroyOverlay,
  )
  private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
  private var player: ExoPlayer? = null
  private val output: ExperimentSurfaceLease<android.view.Surface> by lazy {
    ExperimentSurfaceLease(
        bind = { surface ->
          check(surface.isValid) { "Invalid SDK Surface" }
          checkNotNull(player).setVideoSurface(surface)
          event("surface attach generation=${output.generation} id=${System.identityHashCode(surface)}")
        },
        clear = { surface ->
          val consumer = player
          if (consumer != null) consumer.clearVideoSurface(surface)
          else check(closed) { "Missing live Surface consumer" }
          event("surface detach generation=${output.generation} id=${System.identityHashCode(surface)}")
        },
    )
  }
  private var display: Display? = null
  private var composition: GLXFInfo? = null
  private var anchor: Entity? = null
  private var closed = false
  private var prepareCalls = 0
  private var status: TextView? = null
  private var details = "正在加载独立场景；视频尚未创建"
  private var failure = ""
  private var selected = "未选择"
  private val buttons = mutableListOf<Button>()
  private val maskButtons = mutableMapOf<ExperimentMaskMode, Button>()
  private var maskMode = ExperimentMaskMode.ORIGINAL
  private var maskHint: TextView? = null
  private var desiredGeometry: ExperimentGeometry? = null
  private var widthSlider: SeekBar? = null
  private var radiusSlider: SeekBar? = null
  private var widthLabel: TextView? = null
  private var radiusLabel: TextView? = null
  private val sliderHandler = Handler(Looper.getMainLooper())
  private var scheduledSliderUpdate: Runnable? = null
  private val geometryUpdates = ExperimentGeometryUpdates(
      schedule = { callback ->
        Runnable { callback() }.also {
          scheduledSliderUpdate = it
          sliderHandler.postDelayed(it, SLIDER_UPDATE_INTERVAL_MS)
        }
      },
      cancel = {
        scheduledSliderUpdate?.let(sliderHandler::removeCallbacks)
        scheduledSliderUpdate = null
      },
      apply = ::applyGeometry,
  )

  override fun registerFeatures(): List<SpatialFeature> = listOf(VRFeature(this))

  override fun registerPanels(): List<PanelRegistration> = listOf(
      ViewPanelRegistration(
          R.id.geometry_experiment_controls,
          dynamicViewCreator = { _, context -> controls(context) },
          settingsCreator = {
            UIPanelSettings(
                shape = QuadShapeOptions(width = 1.5f, height = 0.7f),
                display = DpDisplayOptions(width = 1500f, height = 700f, dpi = 160),
                style = PanelStyleOptions(themeResourceId = R.style.PanelAppThemeTransparent),
            )
          },
      ).config { layerConfig?.zIndex = 3 }
  )

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    val createdPlayer = ExoPlayer.Builder(this).build()
    player = createdPlayer
    createdPlayer.videoScalingMode = C.VIDEO_SCALING_MODE_SCALE_TO_FIT
    createdPlayer.repeatMode = Player.REPEAT_MODE_ONE
    createdPlayer.addListener(object : Player.Listener {
      override fun onPlayerError(error: PlaybackException) = failed("播放器 ${error.errorCodeName}", error)
      override fun onPlaybackStateChanged(playbackState: Int) = event("player state=$playbackState prepare=$prepareCalls")
      override fun onVideoSizeChanged(videoSize: VideoSize) = event("decoded video=${videoSize.width}x${videoSize.height}")
      override fun onRenderedFirstFrame() {
        if (!closed) event("first-frame currentGeneration=${output.generation} surface=${output.surface?.let(System::identityHashCode)} (player event; not visual proof)")
      }
    })
    createdPlayer.setMediaItem(MediaItem.fromUri(Uri.parse("android.resource://$packageName/${R.raw.geometry_experiment_video}")))
    prepareCalls++
    createdPlayer.prepare()
    createdPlayer.playWhenReady = true
    event("initial media prepare=$prepareCalls source=local-demo-video 1280x720 scaling=FIT stereo=None")
  }

  override fun onSceneReady() {
    super.onSceneReady()
    scene.setReferenceSpace(ReferenceSpace.LOCAL_FLOOR)
    scene.setViewOrigin(0f, 0f, 0f, 0f)
    // The root has no authored placement; all static placement is inside the MSE scene.
    scope.launch {
      try {
        val loaded = checkNotNull(glXFManager.inflateGLXF(Uri.parse("apk:///geometry-experiment/Composition%201.glxf")))
        if (closed) {
          loaded.destroy()
          return@launch
        }
        composition = loaded
        val room = checkNotNull(loaded.getNodeByName("MediaRoom").entity)
        room.setComponent(room.getComponent<Mesh>().apply {
          defaultShaderOverride = SceneMaterial.UNLIT_SHADER
        })
        event("environment ready node=MediaRoom shader=UNLIT (demo/product room)")
        anchor = checkNotNull(loaded.getNodeByName("GeometryExperimentAnchor").entity)
        details = "Cozy / MediaRoom 场景就绪；请选择预设\n锚点 (0, 1.6, 2.5)，曲面 Z=-r"
        event("scene ready anchor=${anchor?.id} pose=${anchor?.getComponent<Transform>()?.transform}")
        refresh()
      } catch (error: Exception) {
        failed("场景加载失败", error)
      }
    }
  }

  private fun controls(context: Context): View = LinearLayout(context).apply {
    orientation = LinearLayout.VERTICAL
    setPadding(24, 12, 24, 12)
    setBackgroundColor(Color.rgb(24, 28, 35))
    addView(TextView(context).apply {
      text = "视频重建 / 文字层保留 · Cozy · 伪弹幕 + 伪字幕\n固定中央表面 (0, 1.6, 2.5) · 曲面 Z=-r · 弹幕前移 1cm / 字幕 2cm"
      textSize = 18f
      setTextColor(Color.WHITE)
    })
    addView(LinearLayout(context).apply {
      orientation = LinearLayout.HORIZONTAL
      Preset.entries.forEach { preset ->
        addView(Button(context).apply {
          text = preset.describe()
          textSize = 17f
          isAllCaps = false
          isEnabled = anchor != null && !closed
          setOnClickListener { runOnUiThread { select(preset) } }
          buttons.add(this)
        }, LinearLayout.LayoutParams(0, 88, 1f))
      }
    })
    addView(LinearLayout(context).apply {
      orientation = LinearLayout.HORIZONTAL
      ExperimentMaskMode.entries.forEach { mode ->
        addView(Button(context).apply {
          textSize = 15f
          isAllCaps = false
          setOnClickListener { runOnUiThread { selectMaskMode(mode) } }
          maskButtons[mode] = this
        }, LinearLayout.LayoutParams(0, 64, 1f))
      }
    })
    maskHint = TextView(context).also {
      it.textSize = 14f
      it.setTextColor(Color.LTGRAY)
      addView(it)
    }
    addView(sliderRow(context, isRadius = false))
    addView(sliderRow(context, isRadius = true))
    addView(TextView(context).apply {
      text = "拖动每 100 ms 取最新值，松手应用最终值；半径越小越弯。先选预设。"
      textSize = 14f
      setTextColor(Color.LTGRAY)
    })
    status = TextView(context).also {
      it.textSize = 14f
      it.setTextColor(Color.WHITE)
      addView(it)
    }
    refresh()
  }

  private fun sliderRow(context: Context, isRadius: Boolean): View = LinearLayout(context).apply {
    orientation = LinearLayout.HORIZONTAL
    gravity = android.view.Gravity.CENTER_VERTICAL
    val label = TextView(context).apply {
      textSize = 16f
      setTextColor(Color.WHITE)
    }
    addView(label, LinearLayout.LayoutParams(460, LinearLayout.LayoutParams.WRAP_CONTENT))
    val slider = SeekBar(context).apply {
      max = if (isRadius) ExperimentGeometry.RADIUS_STEPS else ExperimentGeometry.WIDTH_STEPS
      contentDescription = if (isRadius) "曲率半径，越小越弯" else "视频宽度，保持十六比九"
      setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
        override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
          if (!fromUser || closed || anchor == null || display == null) return
          val current = desiredGeometry ?: return
          desiredGeometry = if (isRadius) current.withRadiusProgress(progress)
              else current.withWidthProgress(progress)
          selected = "自定义 ${checkNotNull(desiredGeometry).describe()}"
          geometryUpdates.request(checkNotNull(desiredGeometry))
          refresh()
        }
        override fun onStartTrackingTouch(seekBar: SeekBar) = Unit
        override fun onStopTrackingTouch(seekBar: SeekBar) = geometryUpdates.flush()
      })
    }
    addView(slider, LinearLayout.LayoutParams(0, 72, 1f))
    if (isRadius) {
      radiusLabel = label
      radiusSlider = slider
    } else {
      widthLabel = label
      widthSlider = slider
    }
  }

  private fun select(preset: Preset) {
    if (closed || anchor == null) return
    desiredGeometry = preset.geometry
    selected = preset.describe()
    widthSlider?.progress = preset.geometry.widthProgress
    radiusSlider?.progress = preset.geometry.radiusProgress
    geometryUpdates.replace(preset.geometry)
    refresh()
  }

  private fun selectMaskMode(mode: ExperimentMaskMode) {
    if (closed || anchor == null || mode == maskMode && failure.isEmpty()) return
    maskMode = mode
    try {
      // Replay the last committed geometry; pending slider input will use this mode on its own tick.
      overlays.invalidate()
      display?.geometry?.let {
        val videoGeneration = output.generation
        val videoSurface = output.surface
        overlays.apply(it)
        setOverlayVisibility(true)
        check(output.generation == videoGeneration && output.surface === videoSurface) { "Mask mode changed video output" }
        failure = ""
      }
      event("mask mode=$maskMode videoGeneration=${output.generation} prepare=$prepareCalls ${overlayIdentitySummary()}")
      refresh()
    } catch (error: Exception) {
      runCatching { setOverlayVisibility(false) }.onFailure { error.addSuppressed(it) }
      failed("遮罩模式切换失败 $maskMode", error)
    }
  }

  private fun applyGeometry(geometry: ExperimentGeometry) {
    if (closed || anchor == null) return
    if (display?.geometry == geometry && failure.isEmpty()) {
      refresh()
      return
    }
    failure = ""
    details = "正在更换显示对象"
    var candidate: Display? = null
    var entity: Entity? = null
    try {
      val generation = output.begin() // Detach old output before destroying its SDK Surface.
      destroyDisplay()
      event("create begin generation=$generation ${geometry.describe()} prepare=$prepareCalls")
      val settings = MediaPanelSettings(
          shape = geometry.radius?.let { CylinderShapeOptions(radius = it, width = geometry.width, height = geometry.height) }
              ?: QuadShapeOptions(width = geometry.width, height = geometry.height),
          display = PixelDisplayOptions(width = 1280, height = 720),
          rendering = MediaPanelRenderOptions(stereoMode = StereoMode.None, zIndex = 0),
          input = PanelInputOptions(0),
      ).toPanelConfigOptions()
      // Neither init override, custom mesh, reshape, crop nor UV adjustment is installed.
      event("requested generation=$generation config=$settings")
      val createdEntity = Entity.create(listOf(
          Transform(Pose(Vector3(0f, 0f, geometry.carrierLocalZ(ExperimentLayer.VIDEO)))),
          TransformParent(checkNotNull(anchor)),
      ))
      entity = createdEntity
      val panel = PanelSceneObject(scene, createdEntity, settings)
      candidate = Display(generation, createdEntity, panel, geometry)
      systemManager.findSystem<SceneObjectSystem>().addSceneObject(
          createdEntity, CompletableFuture.completedFuture<SceneObject>(panel))
      createdEntity.setComponent(Hittable(MeshCollision.NoCollision))
      check(output.attach(generation, panel.surface)) { "Stale generation $generation" }
      display = candidate
      overlays.apply(geometry)
      setOverlayVisibility(true)
      val actual = checkNotNull(panel.panelShapeConfig)
      val crop = panel.crop
      details = "已创建 generation=$generation，prepare=$prepareCalls\n" +
          "实际 ${actual.panelShapeType} ${actual.width}×${actual.height} m r=${actual.radiusForCylinderOrSphere}\n" +
          "layer=${actual.layerConfig != null} blend=${actual.layerBlendType} alpha=${actual.alphaMode}\n" +
          "crop=$crop；1280×720；FIT；stereo=None\n" +
          "载体 Z：视频 ${geometry.carrierLocalZ(ExperimentLayer.VIDEO)} / " +
          "弹幕 ${geometry.carrierLocalZ(ExperimentLayer.DANMAKU)} / 字幕 ${geometry.carrierLocalZ(ExperimentLayer.SUBTITLE)}\n" +
          overlayIdentitySummary()
      event("created generation=$generation entity=${createdEntity.id} surface=${System.identityHashCode(panel.surface)} " +
          "shape=${actual.panelShapeType} width=${actual.width} height=${actual.height} radius=${actual.radiusForCylinderOrSphere} " +
          "pivot=${actual.pivotOffsetWidth},${actual.pivotOffsetHeight} layer=${actual.layerConfig} blend=${actual.layerBlendType} " +
          "shader=${actual.panelShader} alpha=${actual.alphaMode} transparent=${actual.enableTransparent} " +
          "customMesh=${actual.sceneMeshCreator != null} crop=$crop prepare=$prepareCalls " +
          "videoZ=${geometry.carrierLocalZ(ExperimentLayer.VIDEO)} overlays=${overlays.size}")
      refresh()
    } catch (error: Exception) {
      // Retain overlay UI resources for retry; hide partial geometry when video creation/update fails.
      runCatching { setOverlayVisibility(false) }.onFailure { error.addSuppressed(it) }
      // Never destroy a bound video Surface.
      if (candidate != null) {
        display = candidate
        try {
          output.surface?.let { output.detach(candidate.generation, it) }
          destroyDisplay()
        } catch (cleanupError: Exception) {
          error.addSuppressed(cleanupError)
        }
      } else {
        entity?.destroy()
      }
      failed("几何创建失败 ${geometry.describe()} generation=${output.generation}", error)
    }
  }

  private fun overlaySettings(geometry: ExperimentGeometry, layer: ExperimentLayer) = UIPanelSettings(
      shape = geometry.radius?.let { CylinderShapeOptions(it, geometry.width, geometry.height) }
          ?: QuadShapeOptions(geometry.width, geometry.height),
      display = DpDisplayOptions(1280f, 720f, 160),
      rendering = UIPanelRenderOptions(PanelRenderMode.Layer(layerBlendType = PanelShapeLayerBlendType.ALPHA_BLEND)),
      style = PanelStyleOptions(themeResourceId = R.style.PanelAppThemeTransparent),
      input = PanelInputOptions(0),
  ).toPanelConfigOptions().apply {
    checkNotNull(layerConfig).zIndex = layer.zIndex
    includeGlass = false
    enableTransparent = true
    // SDK initialization selects the alpha-punch material before a custom mesh factory short-circuits it.
    if (maskMode == ExperimentMaskMode.ALIGNED) {
      init()
      sceneMeshCreator = ::createExperimentMaskSceneMesh
    }
  }

  private fun updateOverlay(layer: ExperimentLayer, overlay: Overlay, geometry: ExperimentGeometry) {
    val entity = overlay.entity ?: Entity.create(listOf(
        Transform(Pose(Vector3(0f, 0f, geometry.carrierLocalZ(layer)))),
        TransformParent(checkNotNull(anchor)),
        Visible(false),
        Hittable(MeshCollision.NoCollision),
    )).also { overlay.entity = it }
    val view = overlay.view ?: ExperimentOverlayView(this, layer) {
      // UI ownership and its clock are independent of transient video generations.
      if (closed) null else player?.currentPosition
    }.also { overlay.view = it }
    val settings = overlaySettings(geometry, layer)
    val existing = overlay.panel
    val panel = if (existing == null) {
      PanelSceneObject(scene, spatialContext, entity, settings, { view }).also { overlay.panel = it }
    } else {
      val surface = existing.surface
      val uiDisplay = existing.display
      existing.reshape(settings) // Fixed pixel budget: no resize, detachDisplay or new View.
      check(existing.surface === surface && existing.display === uiDisplay) { "Overlay UI output changed during reshape" }
      existing
    }
    if (!overlay.registered) {
      systemManager.findSystem<SceneObjectSystem>().addSceneObject(
          entity, CompletableFuture.completedFuture<SceneObject>(panel))
      overlay.registered = true
    }
    if (maskMode == ExperimentMaskMode.HIDDEN) {
      val materials = checkNotNull(panel.materials) { "Overlay scene materials unavailable" }
      check(materials.isNotEmpty()) { "No overlay scene material to suppress" }
      materials.forEach {
        it.setColorWrite(0) // Includes alpha: disable this scene footprint, not the compositor layer.
        it.setDepthWrite(DepthWrite.DISABLE)
      }
    }
    entity.setComponent(Transform(Pose(Vector3(0f, 0f, geometry.carrierLocalZ(layer)))))
    entity.setComponent(Hittable(MeshCollision.NoCollision))
    event("overlay ${if (existing == null) "created" else "reshaped"} role=$layer entity=${entity.id} " +
        "${geometry.describe()} carrierZ=${geometry.carrierLocalZ(layer)} zIndex=${layer.zIndex} mode=$maskMode " +
        "alpha=${panel.panelShapeConfig?.alphaMode} customMesh=${panel.panelShapeConfig?.sceneMeshCreator != null} " +
        "panel=${System.identityHashCode(panel)} view=${System.identityHashCode(view)} surface=${System.identityHashCode(panel.surface)}")
  }

  private fun setOverlayVisibility(visible: Boolean) {
    overlays.forEach { _, overlay -> overlay.entity?.setComponent(Visible(visible)) }
  }

  private fun overlayIdentitySummary() = buildString {
    overlays.forEach { layer, overlay ->
      if (isNotEmpty()) append(" / ")
      append("$layer UI=${overlay.panel?.surface?.let(System::identityHashCode)}")
    }
  }

  private fun destroyOverlay(overlay: Overlay) {
    overlay.view?.stop()
    val entity = overlay.entity
    if (overlay.registered && entity != null) {
      systemManager.findSystem<SceneObjectSystem>().removeSceneObject(entity)
      overlay.registered = false
    }
    overlay.panel?.destroy()
    overlay.panel = null
    entity?.destroy()
    overlay.entity = null
  }

  private fun destroyDisplay() {
    val old = display ?: return
    check(output.surface == null) { "Cannot destroy a bound SDK Surface" }
    systemManager.findSystem<SceneObjectSystem>().removeSceneObject(old.entity)
    old.panel.destroy() // Video only; the Activity-owned overlay UI remains attached and animating.
    old.entity.destroy()
    display = null
    event("destroyed generation=${old.generation} entity=${old.entity.id}")
  }

  private fun refresh() = runOnUiThread {
    status?.text = "模式：${maskMode.title}\n请求：$selected\n$details\ngeneration=${output.generation} prepare=$prepareCalls\n$failure"
    maskHint?.text = maskMode.hint
    maskButtons.forEach { (mode, button) ->
      button.isEnabled = anchor != null && !closed
      button.text = (if (mode == maskMode) "● " else "") + mode.title
    }
    val sliderEnabled = anchor != null && !closed && display != null
    widthSlider?.isEnabled = sliderEnabled
    radiusSlider?.isEnabled = sliderEnabled && desiredGeometry?.radiusCm != null
    widthLabel?.text = "尺寸：宽 ${desiredGeometry?.width ?: "—"} m（1.00–2.40）"
    radiusLabel?.text = if (desiredGeometry?.radiusCm == null) "曲率：平面（请选择曲面预设）"
        else "曲率：半径 ${desiredGeometry?.radius} m（0.80–3.00）"
    buttons.forEach { it.isEnabled = anchor != null && !closed }
  }

  private fun event(message: String) { Log.i(TAG, message) }
  private fun failed(message: String, error: Exception) {
    Log.e(TAG, message, error)
    failure = "$message\n${error.javaClass.simpleName}: ${error.message}"
    refresh()
  }

  private fun shutdown() {
    if (closed) return
    closed = true
    geometryUpdates.close()
    // UI has no media consumer: close it even if video detach/player release later fails.
    runCatching { overlays.close() }.onFailure { Log.e(TAG, "overlay shutdown cleanup failed", it) }
    scope.cancel()
    runCatching { output.close() }.onFailure { Log.e(TAG, "initial shutdown detach failed", it) }
    // A failed clear must not strand the SDK Surface or destroy it under a live decoder.
    // Release is the terminal consumer barrier; retrying close afterwards only drops ownership.
    try {
      player?.release()
    } catch (error: Exception) {
      Log.e(TAG, "player release failed; retaining bound panel until SDK shutdown", error)
      return
    }
    player = null
    event("player released prepare=$prepareCalls")
    runCatching { output.close(); destroyDisplay() }
        .onFailure { Log.e(TAG, "video shutdown cleanup failed", it) }
    runCatching { composition?.destroy() }
        .onFailure { Log.e(TAG, "scene shutdown cleanup failed", it) }
    composition = null
    anchor = null
  }

  override fun onSpatialShutdown() {
    shutdown()
    super.onSpatialShutdown()
  }

  override fun onDestroy() {
    shutdown()
    super.onDestroy()
  }

  companion object {
    private const val TAG = "GeometryExperiment"
    private const val SLIDER_UPDATE_INTERVAL_MS = 100L
  }
}
