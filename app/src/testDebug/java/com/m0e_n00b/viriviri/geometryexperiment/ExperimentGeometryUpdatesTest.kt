package com.m0e_n00b.viriviri.geometryexperiment

import org.junit.Assert.*
import org.junit.Test

class ExperimentGeometryUpdatesTest {
  private class Harness {
    // Keep cancelled callbacks to exercise late delivery, rather than assume cancellation succeeds.
    val callbacks = mutableListOf<() -> Unit>()
    val applied = mutableListOf<ExperimentGeometry>()
    val updates = ExperimentGeometryUpdates(
        schedule = { callbacks += it },
        cancel = {},
        apply = { applied += it },
    )
  }

  @Test fun draggingCoalescesToLatestCompleteGeometry() {
    val h = Harness()
    h.updates.request(ExperimentGeometry(160, 100))
    h.updates.request(ExperimentGeometry(180, 100))
    h.updates.request(ExperimentGeometry(180, 150))
    assertEquals(1, h.callbacks.size)
    assertTrue(h.applied.isEmpty())
    h.callbacks[0]()
    assertEquals(listOf(ExperimentGeometry(180, 150)), h.applied)
    h.updates.request(ExperimentGeometry(200, 150))
    assertEquals(2, h.callbacks.size)
    h.callbacks[1]()
    assertEquals(ExperimentGeometry(200, 150), h.applied.last())
  }

  @Test fun releaseFlushesFinalValueAndLateTimerCannotApplyItTwice() {
    val h = Harness()
    val final = ExperimentGeometry(240, 80)
    h.updates.request(final)
    h.updates.flush()
    h.callbacks[0]()
    h.updates.flush()
    assertEquals(listOf(final), h.applied)
  }

  @Test fun presetPreventsOldDragFromOverwritingItOrStealingNewDrag() {
    val h = Harness()
    h.updates.request(ExperimentGeometry(200, 80))
    val preset = ExperimentGeometry(160, null)
    h.updates.replace(preset)
    val newDrag = ExperimentGeometry(210, null)
    h.updates.request(newDrag)
    h.callbacks[0]()
    assertEquals(listOf(preset), h.applied)
    h.callbacks[1]()
    assertEquals(listOf(preset, newDrag), h.applied)
  }

  @Test fun shutdownDiscardsPendingAndRejectsAllLateInputs() {
    val h = Harness()
    h.updates.request(ExperimentGeometry(180, 100))
    h.updates.close()
    h.updates.close()
    h.callbacks[0]()
    h.updates.flush()
    h.updates.replace(ExperimentGeometry(160, null))
    h.updates.request(ExperimentGeometry(240, 100))
    assertTrue(h.applied.isEmpty())
    assertEquals(1, h.callbacks.size)
  }

  @Test fun failedApplyDoesNotReplayOldRequestAndAllowsExplicitRetry() {
    val callbacks = mutableListOf<() -> Unit>()
    var attempts = 0
    val updates = ExperimentGeometryUpdates({ callbacks += it }, {}, { attempts++; error("create failed") })
    val geometry = ExperimentGeometry(200, 100)
    updates.request(geometry)
    assertThrows(IllegalStateException::class.java) { callbacks[0]() }
    callbacks[0]()
    updates.request(geometry)
    assertThrows(IllegalStateException::class.java) { callbacks[1]() }
    assertEquals(2, attempts)
  }
}
