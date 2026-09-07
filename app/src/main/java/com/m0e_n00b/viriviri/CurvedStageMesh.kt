package com.m0e_n00b.viriviri

import kotlin.math.cos
import kotlin.math.sin

/**
 * Geometry of the immersive video stage screen: a flat quad or a cylinder segment that wraps
 * around the viewer.
 *
 * Pure math with no Spatial/Android dependencies so it is JVM unit-testable. It produces
 * position / normal / UV / index arrays ready to feed a Spatial SDK `TriangleMesh`; the host
 * (SpatialVideoSampleActivity) owns the TriangleMesh/material/shadow footprint.
 *
 * Local frame: screen centred at the origin, width along X, height along Y; the front face
 * points toward +Z (matching the existing flat video quad whose normals are (0,0,1)). A
 * cylinder screen curves its edges toward the viewer (+Z). The mapping follows the cylinder
 * projection found in YouTube VR's compositor/lullaby DeformSystem (see
 * docs/research/youtube-vr/01-curved-stage.md): for a vertex at local x, theta = x / radius,
 *   px = radius * sin(theta)
 *   pz = radius * (1 - cos(theta))   (edges bend toward the viewer)
 *   normal = (-sin(theta), 0, cos(theta))
 * As radius -> infinity this collapses to the flat quad (px -> x, pz -> 0, normal -> +Z).
 *
 * UVs stay a linear 0..1 mapping over the (curved) surface so video, attached danmaku and
 * subtitles all sample the same surface without stretching. The V orientation matches the
 * existing flat quad (bottom row V=1, top row V=0).
 */
sealed interface StageGeometry {
  /** Flat quad; behaviour identical to the current hand-written video quad. */
  data object Flat : StageGeometry

  /**
   * Screen is a segment of a cylinder. [radiusMeters] is the cylinder radius in metres;
   * larger = flatter (the screen width spans a smaller arc). Edges bend toward the viewer.
   */
  data class Cylinder(val radiusMeters: Float) : StageGeometry {
    init {
      require(radiusMeters > 0f) { "cylinder radius must be positive, was $radiusMeters" }
    }
  }
}

/** Position/normal/UV/index arrays for the video surface (no shadow footprint). */
data class CurvedStageMesh(
    val positions: FloatArray,
    val normals: FloatArray,
    val uvs: FloatArray,
    val indices: IntArray,
) {
  val vertexCount: Int
    get() = positions.size / 3
}

object CurvedStageMeshBuilder {
  /** Default subdivision density for the curved grid (per axis). Tunable for smoothness/overhead. */
  const val DEFAULT_COLUMNS = 48
  const val DEFAULT_ROWS = 24

  /**
   * Builds the video surface mesh of [widthMeters] x [heightMeters] for the given [geometry].
   * Flat returns exactly the 4-corner quad (same winding/UVs as today); Cylinder subdivides into
   * a [columns] x [rows] grid and bends the vertices onto the cylinder.
   */
  fun build(
      widthMeters: Float,
      heightMeters: Float,
      geometry: StageGeometry,
      columns: Int = DEFAULT_COLUMNS,
      rows: Int = DEFAULT_ROWS,
  ): CurvedStageMesh {
    require(widthMeters > 0f) { "width must be positive" }
    require(heightMeters > 0f) { "height must be positive" }
    return when (geometry) {
      StageGeometry.Flat -> buildFlat(widthMeters, heightMeters)
      is StageGeometry.Cylinder -> {
        require(columns >= 1) { "columns must be >= 1" }
        require(rows >= 1) { "rows must be >= 1" }
        buildCylinder(widthMeters, heightMeters, geometry.radiusMeters, columns, rows)
      }
    }
  }

  private fun buildFlat(width: Float, height: Float): CurvedStageMesh {
    val halfW = width / 2f
    val halfH = height / 2f
    val positions =
        floatArrayOf(
            -halfW, -halfH, 0f,
            halfW, -halfH, 0f,
            halfW, halfH, 0f,
            -halfW, halfH, 0f,
        )
    val normals = FloatArray(12) { if (it % 3 == 2) 1f else 0f }
    val uvs =
        floatArrayOf(
            0f, 1f,
            1f, 1f,
            1f, 0f,
            0f, 0f,
        )
    val indices = intArrayOf(0, 1, 2, 0, 2, 3, 0, 2, 1, 0, 3, 2)
    return CurvedStageMesh(positions, normals, uvs, indices)
  }

  private fun buildCylinder(
      width: Float,
      height: Float,
      radius: Float,
      columns: Int,
      rows: Int,
  ): CurvedStageMesh {
    val halfW = width / 2f
    val halfH = height / 2f
    val cols1 = columns + 1
    val rows1 = rows + 1
    val vertexCount = cols1 * rows1
    val positions = FloatArray(vertexCount * 3)
    val normals = FloatArray(vertexCount * 3)
    val uvs = FloatArray(vertexCount * 2)
    var vi = 0
    var ui = 0
    // Rows run bottom (row 0) to top; columns left (col 0) to right.
    for (row in 0..rows) {
      val v = row.toFloat() / rows
      val y = -halfH + height * v
      for (col in 0..columns) {
        val u = col.toFloat() / columns
        val x = -halfW + width * u
        val theta = x / radius
        val sinT = sin(theta)
        val cosT = cos(theta)
        positions[vi] = radius * sinT
        positions[vi + 1] = y
        positions[vi + 2] = radius * (1f - cosT)
        normals[vi] = -sinT
        normals[vi + 1] = 0f
        normals[vi + 2] = cosT
        uvs[ui] = u
        uvs[ui + 1] = 1f - v
        vi += 3
        ui += 2
      }
    }
    val indices = IntArray(columns * rows * 6)
    var ii = 0
    for (row in 0 until rows) {
      for (col in 0 until columns) {
        val a = row * cols1 + col
        val b = a + 1
        val d = a + cols1
        val c = d + 1
        // Match flat winding (BL, BR, TR, BL, TR, TL).
        indices[ii++] = a
        indices[ii++] = b
        indices[ii++] = c
        indices[ii++] = a
        indices[ii++] = c
        indices[ii++] = d
      }
    }
    return CurvedStageMesh(positions, normals, uvs, indices)
  }
}
