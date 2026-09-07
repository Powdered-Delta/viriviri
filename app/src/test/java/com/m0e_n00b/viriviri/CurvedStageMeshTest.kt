package com.m0e_n00b.viriviri

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.sqrt

class CurvedStageMeshTest {
  private fun epsilon(a: Float, b: Float, eps: Float = 1e-4f) = abs(a - b) < eps

  @Test
  fun flatMatchesExistingFourCornerQuad() {
    val mesh = CurvedStageMeshBuilder.build(4f, 2f, StageGeometry.Flat)
    assertEquals(4, mesh.vertexCount)
    // Positions: BL, BR, TR, TL at z=0.
    val expected = floatArrayOf(
        -2f, -1f, 0f,  2f, -1f, 0f,  2f, 1f, 0f,  -2f, 1f, 0f,
    )
    for (i in expected.indices) assertTrue("pos[$i]", epsilon(mesh.positions[i], expected[i]))
    // All normals point +Z (matches existing flat video quad).
    for (v in 0 until 4) {
      assertEquals(0f, mesh.normals[v * 3], 1e-6f)
      assertEquals(0f, mesh.normals[v * 3 + 1], 1e-6f)
      assertEquals(1f, mesh.normals[v * 3 + 2], 1e-6f)
    }
    // UVs: BL(0,1), BR(1,1), TR(1,0), TL(0,0).
    val expectedUv = floatArrayOf(0f, 1f, 1f, 1f, 1f, 0f, 0f, 0f)
    for (i in expectedUv.indices) assertEquals(expectedUv[i], mesh.uvs[i], 1e-6f)
    // Winding identical to the current hand-written indices.
    val expectedIdx = intArrayOf(0, 1, 2, 0, 2, 3, 0, 2, 1, 0, 3, 2)
    assertTrue(expectedIdx.contentEquals(mesh.indices))
  }

  @Test
  fun cylinderCurvesEdgesTowardViewerAndKeepsCenterFlat() {
    val radius = 3f
    val width = 4f
    val mesh = CurvedStageMeshBuilder.build(width, 2f, StageGeometry.Cylinder(radius), columns = 16, rows = 8)
    val cols1 = 17
    val rows1 = 9
    assertEquals(cols1 * rows1, mesh.vertexCount)
    assertEquals(16 * 8 * 6, mesh.indices.size)

    // Centre column (col 8): x≈0 -> theta=0 -> (px=0, pz=0) for every row.
    val centerCol = cols1 / 2
    for (row in 0..8) {
      val idx = (row * cols1 + centerCol) * 3
      assertTrue("center px", epsilon(mesh.positions[idx], 0f))
      assertTrue("center pz should be 0, was ${mesh.positions[idx + 2]}", epsilon(mesh.positions[idx + 2], 0f))
      // centre normal points straight at viewer (+Z).
      assertTrue("center normal z", epsilon(mesh.normals[idx + 2], 1f))
    }

    // Edge columns curve toward the viewer (+Z) and are symmetric.
    val leftEdgeIdx = 0 * 3
    val rightEdgeIdx = (0 * cols1 + 16) * 3
    assertTrue("edge should bend toward viewer +Z, was ${mesh.positions[leftEdgeIdx + 2]}", mesh.positions[leftEdgeIdx + 2] > 0.01f)
    assertTrue(epsilon(mesh.positions[leftEdgeIdx + 2], mesh.positions[rightEdgeIdx + 2], 1e-5f))
    // Arc projection pulls the edges slightly toward centre (|px| < half-width) while
    // keeping left negative / right positive.
    val leftPx = mesh.positions[leftEdgeIdx]
    val rightPx = mesh.positions[rightEdgeIdx]
    assertTrue("left edge negative, was $leftPx", leftPx < -1.5f)
    assertTrue("right edge positive, was $rightPx", rightPx > 1.5f)
    assertTrue("left edge |px| < half width (arcs inward)", abs(leftPx) < 2f)
    assertTrue(epsilon(leftPx, -rightPx, 1e-5f))
  }

  @Test
  fun cylinderNormalsAreUnitLengthAndUvsLinear() {
    val mesh = CurvedStageMeshBuilder.build(3f, 2f, StageGeometry.Cylinder(2.5f), columns = 10, rows = 6)
    for (v in 0 until mesh.vertexCount) {
      val nx = mesh.normals[v * 3]; val ny = mesh.normals[v * 3 + 1]; val nz = mesh.normals[v * 3 + 2]
      val len = sqrt(nx * nx + ny * ny + nz * nz)
      assertTrue("normal length $len", epsilon(len, 1f, 1e-5f))
    }
    // First vertex UV = (0,1) (bottom-left), last = (1,0) (top-right).
    assertEquals(0f, mesh.uvs[0], 1e-6f)
    assertEquals(1f, mesh.uvs[1], 1e-6f)
    assertEquals(1f, mesh.uvs[mesh.uvs.size - 2], 1e-6f)
    assertEquals(0f, mesh.uvs[mesh.uvs.size - 1], 1e-6f)
  }

  @Test
  fun largeRadiusCylinderApproachesFlat() {
    val nearly = CurvedStageMeshBuilder.build(4f, 2f, StageGeometry.Cylinder(10_000f), columns = 1, rows = 1)
    // With a huge radius the 2x2 grid corners collapse to the flat plane (z~0): corner |x|=2,
    // |y|=1, normals point at the viewer. (Grid corner order differs from the dedicated flat
    // quad, so compare by property rather than by index.)
    for (v in 0 until nearly.vertexCount) {
      val px = nearly.positions[v * 3]
      val py = nearly.positions[v * 3 + 1]
      val pz = nearly.positions[v * 3 + 2]
      assertTrue("corner |x|=2, was $px", epsilon(abs(px), 2f, 1e-2f))
      assertTrue("corner |y|=1, was $py", epsilon(abs(py), 1f, 1e-2f))
      assertTrue("corner z~0, was $pz", epsilon(pz, 0f, 1e-2f))
      assertTrue("normal z~1", epsilon(nearly.normals[v * 3 + 2], 1f, 1e-3f))
    }
  }

  @Test(expected = IllegalArgumentException::class)
  fun nonPositiveRadiusRejected() {
    CurvedStageMeshBuilder.build(1f, 1f, StageGeometry.Cylinder(0f))
  }
}
