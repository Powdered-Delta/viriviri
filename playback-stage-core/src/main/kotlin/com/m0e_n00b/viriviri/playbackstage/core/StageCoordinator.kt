package com.m0e_n00b.viriviri.playbackstage.core

/**
 * Call renderFrame and close on the frame/lifecycle owner. Inputs/resource notices are synchronized;
 * they never call native code. Reentrant input during apply is retained for the next frame.
 */
class StageCoordinator(private val renderer: StageRenderer) {
  private data class Resource(val generation: Long = 0, val ready: Boolean = false, val epoch: Long = 0)
  private data class Attempt(val revision: Long, val resource: Resource)
  private var input = StageInput().normalized()
  private var revision = 0L
  private var layout = solveStageLayout(input)
  private val resources = StageRole.entries.associateWith { Resource() }.toMutableMap()
  private val applied = mutableMapOf<StageRole, StageApplied>()
  private val failures = mutableMapOf<StageRole, Attempt>()
  private val failureReports = mutableMapOf<StageRole, StageFailure>()
  private var rendering = false
  private var closed = false
  private var videoTarget: StageApplied? = null

  @Synchronized fun desiredLayout(): StageLayout = layout
  @Synchronized fun applied(role: StageRole): StageApplied? = applied[role]
  /** Hit testing consumes only a successfully published, currently valid video layout. */
  @Synchronized fun inputTarget(): StageApplied? = videoTarget
  @Synchronized fun isClosed(): Boolean = closed
  /** Last explicit failure, retained across idle frames until that role succeeds or closes. */
  @Synchronized fun failure(role: StageRole): StageFailure? = failureReports[role]

  @Synchronized fun submit(value: StageInput): Boolean {
    if (closed) return false
    val next = value.normalized()
    if (next == input) return false
    val nextLayout = solveStageLayout(next)
    input = next
    layout = nextLayout
    revision++
    return true
  }

  /** Generation increases on resource replacement; an older callback can never resurrect it. */
  @Synchronized fun resourceChanged(role: StageRole, generation: Long, ready: Boolean): Boolean {
    if (closed) return false
    require(generation >= 0)
    val old = resources.getValue(role)
    if (generation < old.generation || (generation == old.generation && ready == old.ready)) return false
    resources[role] = Resource(generation, ready, old.epoch + 1)
    applied.remove(role)
    failures.remove(role)
    if (role == StageRole.VIDEO) {
      videoTarget = null
      // A video replacement can recover a previously failed group at the same revision.
      failures.remove(StageRole.BACKDROP)
      failures.remove(StageRole.DANMAKU)
    }
    return true
  }

  @Synchronized fun retry(role: StageRole): Boolean {
    if (closed || failures.remove(role) == null) return false
    if (role == StageRole.VIDEO) {
      failures.remove(StageRole.BACKDROP)
      failures.remove(StageRole.DANMAKU)
    }
    return true
  }

  @Synchronized fun renderFrame(): StageApplyResult? {
    if (closed || rendering) return null
    val videoAttempt = Attempt(revision, resources.getValue(StageRole.VIDEO))
    if (failures[StageRole.VIDEO] == videoAttempt) return null
    val requested = StageRole.entries.filter { role ->
      val resource = resources.getValue(role)
      val previous = applied[role]
      (previous?.revision != revision || previous.generation != resource.generation) &&
          failures[role] != Attempt(revision, resource)
    }
    if (requested.isEmpty()) return null
    val capturedRevision = revision
    val capturedLayout = layout
    val allResources = resources.toMap()
    val capturedResources = requested.associateWith { allResources.getValue(it) }
    val frame = StageFrame(capturedRevision, capturedLayout, capturedResources.mapValues { it.value.generation })
    rendering = true
    try {
      var applyError: String? = null
      val result = try {
        renderer.apply(frame)
      } catch (error: Exception) {
        applyError = error.message ?: error.javaClass.simpleName
        StageApplyResult(frame.generations.mapValues { (_, generation) ->
          StageRoleResult.Failed(generation, error.message ?: error.javaClass.simpleName)
        })
      }
      if (closed) return null
      val hidden = mutableSetOf<StageRole>()
      val validated = linkedMapOf<StageRole, StageRoleResult>()
      // VIDEO is intentionally first: overlays cannot acknowledge a newer layout without it.
      for (role in requested) {
        val resource = capturedResources.getValue(role)
        if (resources.getValue(role) != resource) {
          hidden.add(role)
          validated[role] = StageRoleResult.NotReady(resource.generation)
          continue
        }
        val outcome = result.results[role]
        val valid = outcome?.generation == resource.generation
        val effective = if (valid) outcome!! else
          StageRoleResult.Failed(resource.generation, "Missing or mismatched role result")
        validated[role] = effective
        when (effective) {
          is StageRoleResult.Applied -> {
            val currentVideo = applied[StageRole.VIDEO]
            val videoReady = currentVideo?.revision == capturedRevision &&
                currentVideo.generation == resources.getValue(StageRole.VIDEO).generation &&
                failures[StageRole.VIDEO] != Attempt(capturedRevision, resources.getValue(StageRole.VIDEO)) &&
                StageRole.VIDEO !in hidden
            if (role != StageRole.VIDEO && !videoReady) {
              applied.remove(role)
              hidden.add(role)
              validated[role] = StageRoleResult.NotReady(resource.generation)
            } else {
              val stamp = StageApplied(capturedRevision, resource.generation, capturedLayout)
              applied[role] = stamp
              failures.remove(role)
              failureReports.remove(role)
              if (role == StageRole.VIDEO) videoTarget = stamp.takeIf { capturedLayout.video.visible }
            }
          }
          is StageRoleResult.NotReady -> {
            applied.remove(role)
            hidden.add(role)
            if (role == StageRole.VIDEO) videoTarget = null
          }
          is StageRoleResult.Failed -> {
            failures[role] = Attempt(capturedRevision, resource)
            failureReports[role] = StageFailure(capturedRevision, resource.generation, effective.reason)
            hidden.add(role)
            if (role == StageRole.VIDEO) videoTarget = null
          }
        }
      }
      if (applyError != null) {
        // An exception has no trustworthy partial-application boundary, even for an overlay-only
        // request. Invalidate all current stamps so same-revision retry must restore the group.
        videoTarget = null
        applied.clear()
        hidden.addAll(StageRole.entries)
        for ((role, resource) in allResources) {
          if (resources.getValue(role) != resource) continue
          failures[role] = Attempt(capturedRevision, resource)
          failureReports[role] = StageFailure(capturedRevision, resource.generation, applyError)
          validated[role] = StageRoleResult.Failed(resource.generation, applyError)
        }
      }
      if (StageRole.VIDEO in hidden) {
        hidden.addAll(StageRole.entries)
        applied.remove(StageRole.BACKDROP)
        applied.remove(StageRole.DANMAKU)
      }
      if (hidden.isNotEmpty()) {
        try { renderer.hide(hidden.toSet()) } catch (error: Exception) {
          close()
          throw IllegalStateException("Renderer could not hide failed stage resources; coordinator closed", error)
        }
      }
      return StageApplyResult(validated)
    } finally {
      rendering = false
    }
  }

  @Synchronized fun close() {
    if (closed) return
    closed = true
    videoTarget = null
    applied.clear()
    failures.clear()
    failureReports.clear()
    renderer.close()
  }
}
