package com.m0e_n00b.viriviri.playbackstage.core

import org.junit.Assert.*
import org.junit.Test

class StageCoordinatorTest {
  private fun ready(renderer: RecordingStageRenderer, coordinator: StageCoordinator, role: StageRole, generation: Long = 1) {
    renderer.ready(role, generation)
    coordinator.resourceChanged(role, generation, true)
  }
  private fun allReady(renderer: RecordingStageRenderer, coordinator: StageCoordinator) {
    StageRole.entries.forEach { ready(renderer, coordinator, it) }
  }

  @Test fun videoDoesNotWaitForOverlaysAndLateLayersApplyBeforeShowing() {
    val backend = RecordingStageRenderer()
    val core = StageCoordinator(backend)
    ready(backend, core, StageRole.VIDEO)
    core.renderFrame()
    assertNotNull(core.inputTarget())
    assertNull(core.applied(StageRole.DANMAKU))
    val revision = core.inputTarget()!!.revision
    core.submit(StageInput(StageParameters(2f, 9f / 16f, StageCurvature.Cylinder(7.9f))))
    core.renderFrame()
    val videoCommandCount = backend.commands.count { it.startsWith("geometry:VIDEO") }
    ready(backend, core, StageRole.DANMAKU)
    core.renderFrame()
    assertEquals(videoCommandCount, backend.commands.count { it.startsWith("geometry:VIDEO") })
    assertEquals(core.inputTarget()!!.revision, core.applied(StageRole.DANMAKU)!!.revision)
    assertTrue(core.inputTarget()!!.revision > revision)
    assertEquals(StageSize(3.2f, 1.8f), backend.resources.getValue(StageRole.DANMAKU).geometry!!.size)
    val geometryIndex = backend.commands.indexOfLast { it.startsWith("geometry:DANMAKU") }
    assertEquals("visible:DANMAKU:true", backend.commands[geometryIndex + 1])
  }

  @Test fun missingResourcesRetryButIdleReadyFramesNeverReshape() {
    val backend = RecordingStageRenderer()
    val core = StageCoordinator(backend)
    repeat(3) { core.renderFrame() }
    assertEquals(3, backend.frames.size)
    assertNull(core.inputTarget())
    allReady(backend, core)
    core.renderFrame()
    val count = backend.frames.size
    repeat(20) { assertNull(core.renderFrame()) }
    assertEquals(count, backend.frames.size)
    assertFalse(core.submit(StageInput()))
  }

  @Test fun replacementReplaysSameRevisionOnlyForChangedRoleAndIgnoresStaleNotices() {
    val backend = RecordingStageRenderer()
    val core = StageCoordinator(backend)
    allReady(backend, core)
    core.renderFrame()
    val revision = core.applied(StageRole.DANMAKU)!!.revision
    ready(backend, core, StageRole.DANMAKU, 2)
    assertFalse(core.resourceChanged(StageRole.DANMAKU, 1, true))
    core.renderFrame()
    assertEquals(mapOf(StageRole.DANMAKU to 2L), backend.frames.last().generations)
    assertEquals(revision, core.applied(StageRole.DANMAKU)!!.revision)
    assertEquals(2L, core.applied(StageRole.DANMAKU)!!.generation)
    assertNull(core.renderFrame())
  }

  @Test fun latestInputCoalescesAndInputDuringApplyWaitsForNextFrame() {
    val backend = RecordingStageRenderer()
    val core = StageCoordinator(backend)
    allReady(backend, core)
    core.renderFrame()
    val old = core.inputTarget()!!
    core.submit(StageInput(StageParameters(0.7f)))
    core.submit(StageInput(StageParameters(1.2f)))
    core.submit(StageInput(StageParameters(2f)))
    assertEquals(old, core.inputTarget())
    backend.duringApply = {
      core.submit(StageInput(StageParameters(0.7f)))
      assertEquals(old, core.inputTarget())
      assertNull(core.renderFrame()) // no nested backend mutation
    }
    core.renderFrame()
    assertEquals(3.2f, core.inputTarget()!!.layout.viewport.width, 0.0001f)
    assertEquals(1.12f, core.desiredLayout().viewport.width, 0.0001f)
    core.renderFrame()
    assertEquals(1.12f, core.inputTarget()!!.layout.viewport.width, 0.0001f)
    assertNull(core.renderFrame())
  }

  @Test fun failedOverlayHidesStaleOutputLatchesAndExplicitRetryRestoresIt() {
    val backend = RecordingStageRenderer()
    val core = StageCoordinator(backend)
    allReady(backend, core)
    core.renderFrame()
    backend.fail.add(StageRole.DANMAKU)
    core.submit(StageInput(StageParameters(2f)))
    core.renderFrame()
    assertFalse(backend.resources.getValue(StageRole.DANMAKU).visible)
    assertTrue(backend.resources.getValue(StageRole.VIDEO).visible)
    assertEquals(3.2f, core.inputTarget()!!.layout.viewport.width, 0.0001f)
    repeat(10) { assertNull(core.renderFrame()) }
    backend.fail.clear()
    assertTrue(core.retry(StageRole.DANMAKU))
    core.renderFrame()
    assertEquals(setOf(StageRole.DANMAKU), backend.frames.last().generations.keys)
    assertTrue(backend.resources.getValue(StageRole.DANMAKU).visible)
  }

  @Test fun videoFailureHidesGroupPreservesOnlyHistoricalStampAndBlocksInput() {
    val backend = RecordingStageRenderer()
    val core = StageCoordinator(backend)
    allReady(backend, core)
    core.renderFrame()
    val historical = core.applied(StageRole.VIDEO)
    backend.fail.add(StageRole.VIDEO)
    core.submit(StageInput(StageParameters(2f)))
    core.renderFrame()
    assertEquals(historical, core.applied(StageRole.VIDEO))
    assertNull(core.inputTarget())
    assertTrue(backend.resources.values.none { it.visible })
    assertNull(core.applied(StageRole.DANMAKU))
    repeat(10) { assertNull(core.renderFrame()) }
    backend.fail.clear()
    assertTrue(core.retry(StageRole.VIDEO))
    core.renderFrame()
    assertNotNull(core.inputTarget())
    assertEquals(core.inputTarget()!!.revision, core.applied(StageRole.DANMAKU)!!.revision)
  }

  @Test fun exceptionAfterPartialMutationIsHiddenAndLatchedUntilNewRevision() {
    val backend = RecordingStageRenderer()
    val core = StageCoordinator(backend)
    allReady(backend, core)
    backend.throwOnApply = true
    val result = core.renderFrame()!!
    assertTrue(result.results.values.all { it is StageRoleResult.Failed })
    assertNull(core.inputTarget())
    assertTrue(backend.resources.values.none { it.visible })
    repeat(3) { assertNull(core.renderFrame()) }
    backend.throwOnApply = false
    core.submit(StageInput(StageParameters(2f)))
    core.renderFrame()
    assertNotNull(core.inputTarget())
  }

  @Test fun failedResourceCanRetryOnGenerationWithoutNewInput() {
    val backend = RecordingStageRenderer()
    val core = StageCoordinator(backend)
    allReady(backend, core)
    backend.fail.add(StageRole.VIDEO)
    core.renderFrame()
    backend.fail.clear()
    ready(backend, core, StageRole.VIDEO, 2)
    core.renderFrame()
    assertEquals(2L, core.inputTarget()!!.generation)
  }

  @Test fun replacementDuringApplyCannotAcknowledgeNewResource() {
    val backend = RecordingStageRenderer()
    val core = StageCoordinator(backend)
    allReady(backend, core)
    backend.duringApply = { ready(backend, core, StageRole.VIDEO, 2) }
    core.renderFrame()
    assertNull(core.inputTarget())
    assertNull(core.applied(StageRole.VIDEO))
    core.renderFrame()
    assertEquals(2L, core.inputTarget()!!.generation)
    assertEquals(core.inputTarget()!!.revision, core.applied(StageRole.DANMAKU)!!.revision)
  }

  @Test fun readinessChangeAtSameGenerationAndLateCallbacksAfterCloseAreHandled() {
    val backend = RecordingStageRenderer()
    val core = StageCoordinator(backend)
    allReady(backend, core)
    core.renderFrame()
    core.resourceChanged(StageRole.VIDEO, 1, false)
    backend.resources.remove(StageRole.VIDEO)
    assertNull(core.inputTarget())
    core.renderFrame()
    ready(backend, core, StageRole.VIDEO)
    core.renderFrame()
    assertNotNull(core.inputTarget())
    core.close()
    core.close()
    assertFalse(core.submit(StageInput(StageParameters(2f))))
    assertFalse(core.resourceChanged(StageRole.VIDEO, 2, true))
    assertFalse(core.retry(StageRole.VIDEO))
    assertNull(core.renderFrame())
    assertNull(core.inputTarget())
    assertEquals(1, backend.closeCalls)
  }

  @Test fun closeDuringApplyRejectsEveryLateAcknowledgement() {
    val backend = RecordingStageRenderer()
    val core = StageCoordinator(backend)
    allReady(backend, core)
    backend.duringApply = { core.close() }
    assertNull(core.renderFrame())
    assertNull(core.inputTarget())
    StageRole.entries.forEach { assertNull(core.applied(it)) }
    assertEquals(1, backend.closeCalls)
  }

  @Test fun missingAndWrongGenerationResultsFailClosedRatherThanAcknowledging() {
    val hides = mutableListOf<Set<StageRole>>()
    val core = StageCoordinator(object : StageRenderer {
      override fun apply(frame: StageFrame) = StageApplyResult(mapOf(StageRole.VIDEO to StageRoleResult.Applied(99)))
      override fun hide(roles: Set<StageRole>) { hides.add(roles) }
      override fun close() = Unit
    })
    val result = core.renderFrame()!!
    assertTrue(result.results.values.all { it is StageRoleResult.Failed })
    assertTrue(core.failure(StageRole.VIDEO)!!.reason.contains("mismatched"))
    assertNull(core.inputTarget())
    assertEquals(StageRole.entries.toSet(), hides.single())
    assertNull(core.renderFrame())
  }

  @Test fun hideFailureClosesCoordinatorAndCannotRestartIt() {
    val backend = RecordingStageRenderer()
    val core = StageCoordinator(backend)
    backend.throwOnHide = true
    try { core.renderFrame(); fail("hide failure must be terminal") } catch (_: IllegalStateException) { }
    assertTrue(core.isClosed())
    assertEquals(1, backend.closeCalls)
    assertNull(core.renderFrame())
  }
  @Test fun hiddenVideoAppliesGeometryAndDoesNotBlockBackdropOrIdle() {
    val backend = RecordingStageRenderer()
    val core = StageCoordinator(backend)
    allReady(backend, core)
    core.submit(StageInput(presentation = StagePresentation(false, true, false)))
    core.renderFrame()
    assertNull(core.inputTarget())
    assertNotNull(core.applied(StageRole.VIDEO))
    assertTrue(backend.resources.getValue(StageRole.BACKDROP).visible)
    assertFalse(backend.resources.getValue(StageRole.VIDEO).visible)
    assertNull(core.renderFrame())
    core.submit(StageInput(presentation = StagePresentation(false, false, false)))
    core.renderFrame()
    assertTrue(backend.resources.values.none { it.visible })
    assertNull(core.renderFrame())
  }

  @Test fun explicitVideoRetryRecoversOverlayFailuresFromThrownGroupApply() {
    val backend = RecordingStageRenderer()
    val core = StageCoordinator(backend)
    allReady(backend, core)
    backend.throwOnApply = true
    core.renderFrame()
    backend.throwOnApply = false
    assertTrue(core.retry(StageRole.VIDEO))
    core.renderFrame()
    StageRole.entries.forEach { assertNotNull(core.applied(it)) }
    assertNull(core.renderFrame())
  }

  @Test fun closeThrowStillStopsCallbacksAndLateAcknowledgements() {
    val core = StageCoordinator(object : StageRenderer {
      override fun apply(frame: StageFrame) = StageApplyResult(emptyMap())
      override fun hide(roles: Set<StageRole>) = Unit
      override fun close() { error("native cleanup failure") }
    })
    try { core.close(); fail("expected cleanup failure") } catch (_: IllegalStateException) { }
    assertTrue(core.isClosed())
    assertFalse(core.resourceChanged(StageRole.VIDEO, 2, true))
    assertFalse(core.submit(StageInput(StageParameters(2f))))
    assertNull(core.renderFrame())
    core.close()
  }

  @Test fun failureReasonRemainsObservableOnIdleFramesAndClearsOnSuccess() {
    val backend = RecordingStageRenderer()
    val core = StageCoordinator(backend)
    allReady(backend, core)
    backend.fail.add(StageRole.VIDEO)
    core.renderFrame()
    val failure = core.failure(StageRole.VIDEO)!!
    assertEquals("resource rejected geometry", failure.reason)
    assertNull(core.renderFrame())
    assertEquals(failure, core.failure(StageRole.VIDEO))
    backend.fail.clear()
    core.retry(StageRole.VIDEO)
    core.renderFrame()
    assertNull(core.failure(StageRole.VIDEO))
  }

  @Test fun overlayOnlyExceptionHidesAndLatchesEntireGroupUntilVideoRetry() {
    val backend = RecordingStageRenderer()
    val core = StageCoordinator(backend)
    allReady(backend, core)
    core.renderFrame()
    val revision = core.inputTarget()!!.revision
    ready(backend, core, StageRole.DANMAKU, 2)
    backend.throwOnApply = true
    val result = core.renderFrame()!!
    assertEquals(setOf(StageRole.DANMAKU), backend.frames.last().generations.keys)
    assertEquals(StageRole.entries.toSet(), result.results.keys)
    assertTrue(result.results.values.all { it is StageRoleResult.Failed })
    assertNull(core.inputTarget())
    StageRole.entries.forEach { assertNull(core.applied(it)); assertNotNull(core.failure(it)) }
    assertTrue(backend.resources.values.none { it.visible })
    repeat(3) { assertNull(core.renderFrame()) }
    backend.throwOnApply = false
    assertTrue(core.retry(StageRole.VIDEO))
    core.renderFrame()
    assertEquals(StageRole.entries.toSet(), backend.frames.last().generations.keys)
    assertEquals(revision, core.inputTarget()!!.revision)
    assertTrue(backend.resources.getValue(StageRole.VIDEO).visible)
    assertTrue(backend.resources.getValue(StageRole.DANMAKU).visible)
    StageRole.entries.forEach { assertNotNull(core.applied(it)); assertNull(core.failure(it)) }
    assertNull(core.renderFrame())
  }

}
