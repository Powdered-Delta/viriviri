package com.m0e_n00b.viriviri.playbackstage.core

/** Stateful resource backend: records actual geometry-before-show commands, not unconditional true. */
internal class RecordingStageRenderer : StageRenderer {
  data class Resource(val generation: Long, var geometry: StageLayer? = null, var visible: Boolean = false)
  val resources = mutableMapOf<StageRole, Resource>()
  val commands = mutableListOf<String>()
  val frames = mutableListOf<StageFrame>()
  val fail = mutableSetOf<StageRole>()
  var duringApply: (() -> Unit)? = null
  var throwOnApply = false
  var throwOnHide = false
  var closeCalls = 0
  private var videoRevision: Long? = null

  fun ready(role: StageRole, generation: Long = 1) { resources[role] = Resource(generation) }

  override fun apply(frame: StageFrame): StageApplyResult {
    frames.add(frame)
    duringApply?.also { duringApply = null }?.invoke()
    check(closeCalls == 0) { "closed during apply" }
    if (throwOnApply) {
      resources[StageRole.VIDEO]?.visible = true // model a partial native mutation before failure
      error("native reshape failed")
    }
    val results = linkedMapOf<StageRole, StageRoleResult>()
    for ((role, generation) in frame.generations) {
      val resource = resources[role]
      results[role] = when {
        resource == null || resource.generation != generation -> StageRoleResult.NotReady(generation)
        role in fail -> StageRoleResult.Failed(generation, "resource rejected geometry")
        role != StageRole.VIDEO && videoRevision != frame.revision -> StageRoleResult.NotReady(generation)
        else -> {
          val layer = frame.layout.layer(role)
          require(layer.size.width > 0f && layer.size.height > 0f)
          require(layer.effectiveRadiusMeters == null || layer.effectiveRadiusMeters > 0f)
          resource.geometry = layer
          commands.add("geometry:$role:$generation:${frame.revision}")
          resource.visible = layer.visible
          commands.add("visible:$role:${layer.visible}")
          if (role == StageRole.VIDEO) videoRevision = frame.revision
          StageRoleResult.Applied(generation)
        }
      }
    }
    return StageApplyResult(results)
  }

  override fun hide(roles: Set<StageRole>) {
    if (throwOnHide) error("hide failed")
    for (role in roles) {
      resources[role]?.visible = false
      commands.add("hide:$role")
      if (role == StageRole.VIDEO) videoRevision = null
    }
  }

  override fun close() { closeCalls++; resources.clear(); commands.add("close") }
}
