package com.m0e_n00b.viriviri

import com.meta.spatial.core.Pose
import com.meta.spatial.core.Quaternion
import com.meta.spatial.core.Vector3
import kotlin.math.cos
import kotlin.math.sin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackStageApertureMeshTest {
  private fun point(mesh: PlaybackStageApertureMesh, index: Int) = Vector3(
      mesh.positions[index * 3], mesh.positions[index * 3 + 1], mesh.positions[index * 3 + 2])

  @Test fun flatApertureTracksPhysicalScaleWithoutChangingFullFrameUvs() {
    for (scale in listOf(0.7f, 1f, 1.5f, 2f)) {
      val mesh = playbackStageApertureMesh(1.6f * scale, 0.9f * scale, PlaybackStageCurvature.Flat)
      assertEquals(4, mesh.positions.size / 3)
      assertEquals(-0.8f * scale, point(mesh, 0).x, 0.0001f)
      assertEquals(0.8f * scale, point(mesh, 3).x, 0.0001f)
      assertEquals(-0.45f * scale, point(mesh, 0).y, 0.0001f)
      assertEquals(0.45f * scale, point(mesh, 3).y, 0.0001f)
      for (index in 0..3) assertEquals(0f, point(mesh, index).z, 0f)
      assertEquals(0f, mesh.uvs[0], 0f)
      assertEquals(1f, mesh.uvs[1], 0f)
      assertEquals(1f, mesh.uvs[6], 0f)
      assertEquals(0f, mesh.uvs[7], 0f)
    }
  }

  @Test fun videoAxisBoundsKeepTwentyAndFifteenWhileVisibleApertureCenterStaysZero() {
    for (radius in listOf(20f, 15f)) {
      val curvature = PlaybackStageCurvature.Cylinder(radius)
      val mesh = playbackStageApertureMesh(1.6f, 0.9f, curvature, PlaybackStageMeshOrigin.CYLINDER_AXIS)
      val center = point(mesh, (mesh.positions.size / 3) / 2)
      assertEquals(0f, center.x, 0f)
      assertEquals(0f, center.y, 0f)
      assertEquals(radius, center.z, 0f)
      assertEquals(0f, curvature.entityLocalZForSurface(0f) + center.z, 0f)
      assertTrue(point(mesh, 0).z < center.z)
      val opening = playbackStageApertureMesh(1.6f, 0.9f, curvature)
      assertEquals(0f, point(opening, (opening.positions.size / 3) / 2).z, 0f)
      assertTrue(point(opening, 0).z < 0f)
    }
  }

  @Test fun generatedVerticesFollowScaledSurfaceUnderRotatedParentForAllVideoAspects() {
    val stage = Pose(Vector3(0.7f, 1.2f, 2.1f), Quaternion(10f, 35f, 0f))
    val baseZ = -0.012f
    for (aspect in listOf(16f / 9f, 4f / 3f, 9f / 16f)) {
      for (radius in listOf(1.5f, 2.7f, 7.9f, 15f, 20f)) {
        for (scale in listOf(0.7f, 1f, 1.5f, 2f)) {
          val curvature = PlaybackStageCurvature.Cylinder(radius).scaledBy(scale)
          val geometry = playbackStageGeometry(1.6f * scale, 0.9f * scale, aspect, null, curvature)
          val shape = geometry.videoShape
          val mesh = playbackStageApertureMesh(shape.widthMeters, shape.heightMeters, curvature)
          val carrier = stage * playbackStageApertureLocalPose(baseZ * scale)
          // Compare actual generated vertices, including edges and center, with a centered surface.
          for (index in 0 until mesh.positions.size / 3) {
            val u = mesh.uvs[index * 2]
            val v = 1f - mesh.uvs[index * 2 + 1]
            val theta = (shape.widthMeters / scale) * (u - 0.5f) / radius
            val expected = stage * Vector3(radius * sin(theta) * scale,
                shape.heightMeters * (v - 0.5f),
                (baseZ + radius * (cos(theta) - 1f)) * scale)
            val actual = carrier * point(mesh, index)
            assertEquals(expected.x, actual.x, 0.0001f)
            assertEquals(expected.y, actual.y, 0.0001f)
            assertEquals(expected.z, actual.z, 0.0001f)
          }
          assertEquals(0f, geometry.videoTextureCrop.left, 0f)
          assertEquals(1f, geometry.videoTextureCrop.right, 0f)
          assertEquals(1.6f * scale, geometry.danmakuContent.widthMeters, 0.0001f)
        }
      }
    }
  }

  @Test fun switchingFlatAndCurvedKeepsOpeningCenterAtStageSurfaceWithoutRadiusCancellation() {
    val stage = Pose(Vector3(0.7f, 1.2f, 2.1f), Quaternion(10f, 35f, 0f))
    for (scale in listOf(0.7f, 1f, 1.5f, 2f)) {
      val base = -0.012f * scale
      val carrier = stage * playbackStageApertureLocalPose(base)
      val expected = stage * Vector3(0f, 0f, base)
      for (raw in listOf(PlaybackStageCurvature.Flat, PlaybackStageCurvature.Cylinder(20f),
          PlaybackStageCurvature.Cylinder(15f), PlaybackStageCurvature.Cylinder(1.5f),
          PlaybackStageCurvature.Flat)) {
        val mesh = playbackStageApertureMesh(1.6f * scale, 0.9f * scale, raw.scaledBy(scale))
        // The flat grid has no center vertex; average its corners. Curved grid has an exact center.
        val center = if (raw == PlaybackStageCurvature.Flat) Vector3(
            (point(mesh, 0).x + point(mesh, 3).x) / 2,
            (point(mesh, 0).y + point(mesh, 3).y) / 2, 0f)
            else point(mesh, (mesh.positions.size / 3) / 2)
        assertEquals(0f, center.x, 0f)
        assertEquals(0f, center.y, 0f)
        assertEquals(0f, center.z, 0f)
        val actual = carrier * center
        assertEquals(expected.x, actual.x, 0.0001f)
        assertEquals(expected.y, actual.y, 0.0001f)
        assertEquals(expected.z, actual.z, 0.0001f)
      }
    }
  }

  @Test fun apertureTrianglesCoverBothSidesAndReferenceOnlyExistingVertices() {
    for (curvature in listOf(PlaybackStageCurvature.Flat, PlaybackStageCurvature.Cylinder(2.7f))) {
      val mesh = playbackStageApertureMesh(1.6f, 0.9f, curvature)
      val count = mesh.positions.size / 3
      for (index in mesh.indices) assertTrue(index in 0 until count)
      for (offset in mesh.indices.indices step 12) {
        assertEquals(mesh.indices[offset], mesh.indices[offset + 6])
        assertEquals(mesh.indices[offset + 1], mesh.indices[offset + 8])
        assertEquals(mesh.indices[offset + 2], mesh.indices[offset + 7])
        val a = point(mesh, mesh.indices[offset])
        val b = point(mesh, mesh.indices[offset + 1])
        val c = point(mesh, mesh.indices[offset + 2])
        assertTrue((b.x - a.x) * (c.y - a.y) > 0f)
      }
    }
  }
}
