package com.m0e_n00b.viriviri.geometryexperiment

import android.graphics.Color
import com.meta.spatial.runtime.MaterialSidedness
import com.meta.spatial.runtime.PanelShapeConfig
import com.meta.spatial.runtime.PanelShapeType
import com.meta.spatial.runtime.SceneMaterial
import com.meta.spatial.runtime.SceneMesh
import com.meta.spatial.runtime.SceneTexture
import com.meta.spatial.runtime.TriangleMesh

/** Same live UI texture and initialized alpha-punch policy, explicit analytic triangle geometry. */
internal fun createExperimentMaskSceneMesh(config: PanelShapeConfig, texture: SceneTexture): SceneMesh {
  require(config.panelShapeType == PanelShapeType.QUAD || config.panelShapeType == PanelShapeType.CYLINDER)
  val data = experimentMaskMesh(config.width, config.height,
      if (config.panelShapeType == PanelShapeType.CYLINDER) config.radiusForCylinderOrSphere else null)
  val material = SceneMaterial(texture, config.alphaMode, config.panelShader).apply {
    setUnlit(true)
    setStereoMode(config.stereoMode)
    setSidedness(MaterialSidedness.DOUBLE_SIDED)
  }
  val vertices = data.positions.size / 3
  val triangles = TriangleMesh(vertices, data.indices.size,
      intArrayOf(0, data.indices.size), arrayOf(material))
  triangles.updateGeometry(0, data.positions, data.normals, data.uvs, IntArray(vertices) { Color.WHITE })
  triangles.updatePrimitives(0, data.indices)
  return SceneMesh.fromTriangleMesh(triangles, false)
}
