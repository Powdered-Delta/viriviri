package com.m0e_n00b.viriviri

import com.meta.spatial.core.Entity
import com.meta.spatial.core.Pose
import com.meta.spatial.core.Vector3
import com.meta.spatial.runtime.Scene
import com.meta.spatial.runtime.SceneObject
import com.meta.spatial.runtime.SceneTexture
import com.meta.spatial.toolkit.Hittable
import com.meta.spatial.toolkit.MeshCollision
import com.meta.spatial.toolkit.Named
import com.meta.spatial.toolkit.SceneObjectSystem
import com.meta.spatial.toolkit.Transform
import com.meta.spatial.toolkit.TransformParent
import com.meta.spatial.toolkit.Visible
import java.util.concurrent.CompletableFuture

/** Dynamic stage opening: no panel, media Surface, input listener or radius-dependent translation. */
internal class PlaybackStageAperture(
    scene: Scene,
    private val objects: SceneObjectSystem,
    stage: Entity,
) {
  // Geometry changes at runtime; this is not a static scene-authored object.
  private val entity = Entity.create(listOf(
      Named("video_aperture"), Transform(Pose()), TransformParent(stage),
      Visible(true), Hittable(MeshCollision.NoCollision)))
  private val surface = SceneObject(scene, entity)
  private var registered = false
  private var visible = true

  fun reshape(texture: SceneTexture, width: Float, height: Float, curvature: PlaybackStageCurvature) {
    val next = createPlaybackStageApertureMesh(texture, width, height, curvature)
    val previous = surface.mesh
    surface.setSceneMesh(next, "video_aperture")
    previous?.destroy()
    if (!registered) {
      objects.addSceneObject(entity, CompletableFuture.completedFuture(surface))
      registered = true
    }
  }

  fun applyPose(baseLocalZ: Float, stageWorld: Pose) {
    val local = playbackStageApertureLocalPose(baseLocalZ)
    entity.setComponent(Transform(local))
    val world = stageWorld * local
    surface.setScale(Vector3(1f, 1f, 1f))
    surface.setPosition(world.t)
    surface.setRotationQuat(world.q)
  }

  /** Follow existing video visibility; this object introduces no independent show/hide state. */
  fun syncVisibility(source: Entity?) {
    val next = source?.tryGetComponent<Visible>()?.isVisible ?: true
    if (next == visible) return
    visible = next
    entity.setComponent(Visible(next))
    surface.setIsVisible(next)
  }

  fun close() {
    if (registered) objects.removeSceneObject(entity)
    if (surface.mesh != null) {
      val mesh = surface.mesh
      surface.destroy()
      mesh?.destroy()
    }
    entity.destroy()
  }
}

/** baseLocalZ already contains whole-stage scale. Radius does not enter this carrier's pose. */
internal fun playbackStageApertureLocalPose(baseLocalZ: Float): Pose =
    Pose(Vector3(0f, 0f, baseLocalZ))
