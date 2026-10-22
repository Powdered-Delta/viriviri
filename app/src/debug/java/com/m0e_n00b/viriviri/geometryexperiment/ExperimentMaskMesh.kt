package com.m0e_n00b.viriviri.geometryexperiment

import kotlin.math.cos
import kotlin.math.sin

internal enum class ExperimentMaskMode(val title: String, val hint: String) {
  ORIGINAL("原始：SDK 遮罩", "对照：保留当前 SDK 生成的场景遮罩"),
  ALIGNED("A：显式曲面遮罩", "候选 A：显式计算曲面遮罩；检查轮廓是否与文字重合"),
  HIDDEN("B：关闭遮罩绘制", "候选 B：只关闭覆盖层网格的颜色/深度写入；检查文字是否仍正常"),
}

internal data class ExperimentMaskMesh(
    val positions: FloatArray,
    val normals: FloatArray,
    val uvs: FloatArray,
    val indices: IntArray,
)

/** Independent debug candidate. API axis origin matches the existing base-radius carrier pose. */
internal fun experimentMaskMesh(width: Float, height: Float, radius: Float?): ExperimentMaskMesh {
  require(width.isFinite() && width > 0f && height.isFinite() && height > 0f)
  require(radius == null || radius.isFinite() && radius > 0f)
  val columns = if (radius == null) 1 else 64
  val count = (columns + 1) * 2
  val positions = FloatArray(count * 3)
  val normals = FloatArray(count * 3)
  val uvs = FloatArray(count * 2)
  for (row in 0..1) {
    for (column in 0..columns) {
      val vertex = row * (columns + 1) + column
      val u = column.toFloat() / columns
      val arcX = width * (u - 0.5f)
      val theta = if (radius == null) 0f else arcX / radius
      positions[vertex * 3] = if (radius == null) arcX else radius * sin(theta)
      positions[vertex * 3 + 1] = height * (row - 0.5f)
      positions[vertex * 3 + 2] = if (radius == null) 0f else radius * cos(theta)
      normals[vertex * 3] = -sin(theta)
      normals[vertex * 3 + 2] = -cos(theta)
      uvs[vertex * 2] = u
      uvs[vertex * 2 + 1] = 1f - row
    }
  }
  val indices = IntArray(columns * 6)
  for (column in 0 until columns) {
    val a = column
    val b = a + 1
    val d = a + columns + 1
    val c = d + 1
    intArrayOf(a, d, b, b, d, c).copyInto(indices, column * 6)
  }
  return ExperimentMaskMesh(positions, normals, uvs, indices)
}
