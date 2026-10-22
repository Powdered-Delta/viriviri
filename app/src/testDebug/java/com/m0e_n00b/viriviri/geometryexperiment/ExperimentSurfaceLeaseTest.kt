package com.m0e_n00b.viriviri.geometryexperiment

import org.junit.Assert.*
import org.junit.Test

class ExperimentSurfaceLeaseTest {
  @Test fun oldCallbacksCannotDetachOrAttachOverReplacement() {
    val events = mutableListOf<String>()
    val old = Any()
    val replacement = Any()
    val lease = ExperimentSurfaceLease<Any>(
        bind = { events += if (it === old) "bind old" else "bind new" },
        clear = { events += if (it === old) "clear old" else "clear new" },
    )
    val first = lease.begin()
    assertTrue(lease.attach(first, old))
    val second = lease.begin()
    events += "destroy old"
    assertTrue(lease.attach(second, replacement))
    assertFalse(lease.detach(first, old))
    assertFalse(lease.detach(second, old))
    assertFalse(lease.attach(first, old))
    assertSame(replacement, lease.surface)
    assertEquals(listOf("bind old", "clear old", "destroy old", "bind new"), events)
  }

  @Test fun shutdownClearsOnceAndRejectsLateCallbacks() {
    var clears = 0
    val lease = ExperimentSurfaceLease<Any>({}, { clears++ })
    val generation = lease.begin()
    val surface = Any()
    lease.attach(generation, surface)
    lease.close()
    lease.close()
    assertEquals(1, clears)
    assertNull(lease.surface)
    assertFalse(lease.attach(generation, surface))
    assertFalse(lease.detach(generation, surface))
    assertThrows(IllegalStateException::class.java) { lease.begin() }
  }

  @Test fun partialBindFailureRetainsSurfaceForCleanup() {
    val surface = Any()
    var cleared: Any? = null
    val lease = ExperimentSurfaceLease<Any>({ error("bind failed after accepting output") }, { cleared = it })
    val generation = lease.begin()
    assertThrows(IllegalStateException::class.java) { lease.attach(generation, surface) }
    assertSame(surface, lease.surface)
    assertTrue(lease.detach(generation, surface))
    assertSame(surface, cleared)
    assertNull(lease.surface)
  }

  @Test fun failedDetachBlocksReplacementAndRetainsOwnership() {
    val surface = Any()
    var failClear = true
    val lease = ExperimentSurfaceLease<Any>({}, { if (failClear) error("detach failed") })
    val generation = lease.begin()
    lease.attach(generation, surface)
    assertThrows(IllegalStateException::class.java) { lease.begin() }
    assertEquals(generation, lease.generation)
    assertSame(surface, lease.surface)
    failClear = false
    lease.close()
    assertNull(lease.surface)
  }

  @Test fun failedShutdownCanRetryAfterConsumerRelease() {
    var consumerReleased = false
    val lease = ExperimentSurfaceLease<Any>({}, { check(consumerReleased) })
    val generation = lease.begin()
    val surface = Any()
    lease.attach(generation, surface)
    assertThrows(IllegalStateException::class.java) { lease.close() }
    assertSame(surface, lease.surface)
    consumerReleased = true
    lease.close()
    assertNull(lease.surface)
    assertFalse(lease.attach(generation, surface))
  }

  @Test fun repeatedSameSurfaceDoesNotRebind() {
    var binds = 0
    val lease = ExperimentSurfaceLease<Any>({ binds++ }, {})
    val generation = lease.begin()
    val surface = Any()
    assertTrue(lease.attach(generation, surface))
    assertTrue(lease.attach(generation, surface))
    assertEquals(1, binds)
  }
}
