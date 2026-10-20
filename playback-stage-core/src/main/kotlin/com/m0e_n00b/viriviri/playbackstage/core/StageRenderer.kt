package com.m0e_n00b.viriviri.playbackstage.core

import java.util.Collections

/** Immutable request. Only requested roles may reshape; peers retain their already applied layout. */
class StageFrame internal constructor(
    val revision: Long,
    val layout: StageLayout,
    generations: Map<StageRole, Long>,
) {
  val generations: Map<StageRole, Long> = Collections.unmodifiableMap(LinkedHashMap(generations))
}

sealed interface StageRoleResult {
  val generation: Long
  data class Applied(override val generation: Long) : StageRoleResult
  data class NotReady(override val generation: Long) : StageRoleResult
  data class Failed(override val generation: Long, val reason: String) : StageRoleResult
}

class StageApplyResult(results: Map<StageRole, StageRoleResult>) {
  val results: Map<StageRole, StageRoleResult> = Collections.unmodifiableMap(LinkedHashMap(results))
}

/**
 * Resource-applied evidence only, never visual acceptance.
 * Apply geometry before making a role visible. Missing overlays must not block video.
 * An overlay may be published only against the same revision's successfully applied video.
 * hide is an idempotent, synchronous failure barrier; it must not create/reshape resources.
 * All native work, including hide/close, runs on the caller's serialized frame/lifecycle owner.
 */
interface StageRenderer {
  fun apply(frame: StageFrame): StageApplyResult
  fun hide(roles: Set<StageRole>)
  fun close()
}

data class StageApplied(val revision: Long, val generation: Long, val layout: StageLayout)

data class StageFailure(val revision: Long, val generation: Long, val reason: String)
