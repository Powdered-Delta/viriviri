package com.m0e_n00b.viriviri

import com.meta.spatial.core.Entity
import com.meta.spatial.core.Query
import com.meta.spatial.core.SystemBase
import com.meta.spatial.runtime.ButtonBits
import com.meta.spatial.toolkit.Controller

/** Applies official Spatial SDK thumbstick direction bits to the existing MediaStage scale state. */
internal class AnalogMediaStageScaleSystem(
    private val pointerInfo: PointerInfoSystem,
    private val mediaStageEntity: Entity,
    private val onScaleDelta: (Float) -> Unit,
    private val onInteractionFinished: () -> Unit,
    private val scaleSpeed: Float = 0.42f,
) : SystemBase() {
  private var lastTimeMs = System.currentTimeMillis()
  private var wasAdjusting = false

  override fun execute() {
    val now = System.currentTimeMillis()
    val deltaSeconds = ((now - lastTimeMs).coerceIn(0L, 100L)) / 1_000f
    lastTimeMs = now

    val isTargeted = pointerInfo.rightEntity == mediaStageEntity
    var adjusted = false
    if (isTargeted) {
      Query.where { has(Controller.id) }.eval().filter { it.isLocal() }.forEach { entity ->
        val controller = entity.getComponent<Controller>()
        if (!controller.isActive || !PointerInfoSystem.isRightControllerOrRightHand(entity)) return@forEach
        val delta = when {
          controller.isDown(ButtonBits.ButtonThumbRU) -> deltaSeconds * scaleSpeed
          controller.isDown(ButtonBits.ButtonThumbRD) -> -deltaSeconds * scaleSpeed
          else -> 0f
        }
        if (delta != 0f) {
          onScaleDelta(delta)
          adjusted = true
        }
      }
    }

    if (wasAdjusting && !adjusted) onInteractionFinished()
    wasAdjusting = adjusted
  }
}
