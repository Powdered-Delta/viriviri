package com.m0e_n00b.viriviri

import android.graphics.Color
import com.meta.spatial.runtime.AlphaMode
import com.meta.spatial.runtime.DepthWrite
import com.meta.spatial.runtime.SceneMaterial
import com.meta.spatial.runtime.SceneMesh
import com.meta.spatial.runtime.SceneTexture
import com.meta.spatial.runtime.TriangleMesh
import kotlin.math.cos
import kotlin.math.sin

internal data class PlaybackStageApertureMesh(
    val positions: FloatArray,
    val normals: FloatArray,
    val uvs: FloatArray,
    val indices: IntArray,
)

internal enum class PlaybackStageMeshOrigin { SURFACE, CYLINDER_AXIS }

/** Visible aperture is surface-centered; the non-writing video carrier retains cylinder-axis bounds. */
internal fun playbackStageApertureMesh(
    width: Float,
    height: Float,
    curvature: PlaybackStageCurvature,
    origin: PlaybackStageMeshOrigin = PlaybackStageMeshOrigin.SURFACE,
): PlaybackStageApertureMesh {
  require(width.isFinite() && width > 0f && height.isFinite() && height > 0f)
  val radius = curvature.radiusOrNull()
  val columns = if (radius == null) 1 else 48
  val rows = if (radius == null) 1 else 2
  val vertices = (columns + 1) * (rows + 1)
  val positions = FloatArray(vertices * 3)
  val normals = FloatArray(vertices * 3)
  val uvs = FloatArray(vertices * 2)
  val inverseCarrierOffset = if (origin == PlaybackStageMeshOrigin.CYLINDER_AXIS)
      -curvature.surfaceAnchorOffsetMeters() else 0f
  for (row in 0..rows) {
    for (column in 0..columns) {
      val vertex = row * (columns + 1) + column
      val u = column.toFloat() / columns
      val v = row.toFloat() / rows
      val arcX = width * (u - 0.5f)
      val theta = if (radius == null) 0f else arcX / radius
      positions[vertex * 3] = if (radius == null) arcX else radius * sin(theta)
      positions[vertex * 3 + 1] = height * (v - 0.5f)
      val surfaceZ = if (radius == null) 0f else radius * (cos(theta) - 1f)
      positions[vertex * 3 + 2] = surfaceZ + inverseCarrierOffset
      normals[vertex * 3] = sin(theta)
      normals[vertex * 3 + 2] = -cos(theta)
      uvs[vertex * 2] = u
      uvs[vertex * 2 + 1] = 1f - v
    }
  }
  // Both windings are intentional: the aperture must not vanish because of back-face culling.
  val indices = IntArray(columns * rows * 12)
  var next = 0
  for (row in 0 until rows) {
    for (column in 0 until columns) {
      val a = row * (columns + 1) + column
      val b = a + 1
      val d = a + columns + 1
      val c = d + 1
      for (index in intArrayOf(a, b, c, a, c, d, a, c, b, a, d, c)) indices[next++] = index
    }
  }
  return PlaybackStageApertureMesh(positions, normals, uvs, indices)
}

/** Hole-punch only, at the stable stage surface origin. Never owns decoded video pixels. */
internal fun createPlaybackStageApertureMesh(
    texture: SceneTexture,
    width: Float,
    height: Float,
    curvature: PlaybackStageCurvature,
): SceneMesh = createStageMesh(texture, playbackStageApertureMesh(width, height, curvature), true)

/** Retain the compositor carrier's bounds without a second hole or scene-depth occlusion. */
internal fun createPlaybackStageLayerCarrierMesh(
    texture: SceneTexture,
    width: Float,
    height: Float,
    curvature: PlaybackStageCurvature,
): SceneMesh = createStageMesh(texture,
    playbackStageApertureMesh(width, height, curvature, PlaybackStageMeshOrigin.CYLINDER_AXIS), false)

private fun createStageMesh(
    texture: SceneTexture,
    mesh: PlaybackStageApertureMesh,
    writesAperture: Boolean,
): SceneMesh {
  val material = SceneMaterial(texture,
      if (writesAperture) AlphaMode.HOLE_PUNCH else AlphaMode.OPAQUE,
      if (writesAperture) SceneMaterial.HOLE_PUNCH_SHADER else SceneMaterial.UNLIT_SHADER)
      .apply {
        setUnlit(true)
        if (!writesAperture) {
          setColorWrite(0)
          setDepthWrite(DepthWrite.DISABLE)
        }
      }
  val vertexCount = mesh.positions.size / 3
  val triangles = TriangleMesh(vertexCount, mesh.indices.size,
      intArrayOf(0, mesh.indices.size), arrayOf(material))
  triangles.updateGeometry(0, mesh.positions, mesh.normals, mesh.uvs,
      IntArray(vertexCount) { Color.WHITE })
  triangles.updatePrimitives(0, mesh.indices)
  return SceneMesh.fromTriangleMesh(triangles, false)
}
