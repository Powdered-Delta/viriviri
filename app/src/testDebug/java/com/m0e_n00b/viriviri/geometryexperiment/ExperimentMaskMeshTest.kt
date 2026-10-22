package com.m0e_n00b.viriviri.geometryexperiment

import kotlin.math.atan2
import org.junit.Assert.*
import org.junit.Test

class ExperimentMaskMeshTest {
  @Test fun flatHasExpectedExtentUvsAndNoRadiusOffset() {
    val mesh = experimentMaskMesh(1.6f, 0.9f, null)
    assertArrayEquals(floatArrayOf(-0.8f, -0.45f, 0f, 0.8f, -0.45f, 0f,
        -0.8f, 0.45f, 0f, 0.8f, 0.45f, 0f), mesh.positions, 0.000001f)
    assertArrayEquals(floatArrayOf(0f, 1f, 1f, 1f, 0f, 0f, 1f, 0f), mesh.uvs, 0f)
    assertArrayEquals(intArrayOf(0, 2, 1, 1, 2, 3), mesh.indices)
  }

  @Test fun allSupportedRadiiShareTheCompositorAxisAndFixedSurfaceCentre() {
    for (widthCm in listOf(100, 160, 240)) {
      for (radiusCm in 80..300) {
        val geometry = ExperimentGeometry(widthCm, radiusCm)
        val mesh = experimentMaskMesh(geometry.width, geometry.height, geometry.radius)
        val radius = geometry.radius!!
        for (vertex in 0 until mesh.positions.size / 3) {
          val x = mesh.positions[vertex * 3]
          val z = mesh.positions[vertex * 3 + 2]
          assertEquals(radius * radius, x * x + z * z, 0.00001f)
        }
        // 64 subdivisions put the exact surface centre in column 32, on each row.
        assertEquals(0f, mesh.positions[32 * 3], 0.000001f)
        for (layer in ExperimentLayer.entries) {
          assertEquals(layer.baseLocalZ,
              geometry.carrierLocalZ(layer) + mesh.positions[32 * 3 + 2], 0.000001f)
        }
      }
    }
  }

  @Test fun curvedEdgeArcMatchesWidthAndTextureIsNotUvScaled() {
    val radius = 0.8f
    val width = 2.4f
    val mesh = experimentMaskMesh(width, 1.35f, radius)
    assertEquals(-width / (2f * radius), atan2(mesh.positions[0], mesh.positions[2]), 0.000001f)
    assertEquals(width / (2f * radius), atan2(mesh.positions[64 * 3], mesh.positions[64 * 3 + 2]), 0.000001f)
    assertEquals(0f, mesh.uvs[0], 0f)
    assertEquals(1f, mesh.uvs[64 * 2], 0f)
    assertEquals(0.5f, mesh.uvs[32 * 2], 0f)
    assertEquals(1f, mesh.uvs[1], 0f)
    assertEquals(0f, mesh.uvs[65 * 2 + 1], 0f)
  }

  @Test fun trianglesAreValidFiniteAndFaceTheSameWayAsNormals() {
    for (radius in listOf(null, 0.8f, 1f, 3f)) {
      val mesh = experimentMaskMesh(2.4f, 1.35f, radius)
      assertTrue(mesh.positions.all { it.isFinite() })
      val count = mesh.positions.size / 3
      assertTrue(mesh.indices.all { it in 0 until count })
      for (i in mesh.indices.indices step 3) {
        val a = mesh.indices[i] * 3
        val b = mesh.indices[i + 1] * 3
        val c = mesh.indices[i + 2] * 3
        val ab = FloatArray(3) { mesh.positions[b + it] - mesh.positions[a + it] }
        val ac = FloatArray(3) { mesh.positions[c + it] - mesh.positions[a + it] }
        val cross = floatArrayOf(ab[1] * ac[2] - ab[2] * ac[1],
            ab[2] * ac[0] - ab[0] * ac[2], ab[0] * ac[1] - ab[1] * ac[0])
        val dot = (0..2).sumOf { (cross[it] * mesh.normals[a + it]).toDouble() }
        assertTrue("Triangle must face the inward normal", dot > 0.0)
      }
    }
  }

  @Test fun invalidDimensionsAreRejected() {
    assertThrows(IllegalArgumentException::class.java) { experimentMaskMesh(0f, 1f, null) }
    assertThrows(IllegalArgumentException::class.java) { experimentMaskMesh(1f, Float.NaN, null) }
    assertThrows(IllegalArgumentException::class.java) { experimentMaskMesh(1f, 1f, 0f) }
    assertThrows(IllegalArgumentException::class.java) { experimentMaskMesh(1f, 1f, Float.POSITIVE_INFINITY) }
  }
}
