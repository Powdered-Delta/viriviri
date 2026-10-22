package com.m0e_n00b.viriviri.geometryexperiment

import org.junit.Assert.*
import org.junit.Test

class ExperimentRetainedOverlaysTest {
  private class Resource {
    val view = Any()
    val surface = Any()
    var applied: ExperimentGeometry? = null
  }

  @Test fun sizeRadiusAndShapeChangesKeepTheSameResourcesWithoutDisposal() {
    val allocated = mutableMapOf<ExperimentLayer, Resource>()
    var disposed = 0
    val retained = ExperimentRetainedOverlays(
        allocate = { layer -> Resource().also { assertNull(allocated.put(layer, it)) } },
        update = { _, resource, geometry -> resource.applied = geometry },
        dispose = { _: Resource -> disposed++ },
    )
    retained.apply(ExperimentGeometry(160, null))
    val original = allocated.mapValues { it.value.view to it.value.surface }
    for (geometry in listOf(ExperimentGeometry(160, 100), ExperimentGeometry(240, 80),
        ExperimentGeometry(100, 300), ExperimentGeometry(160, null))) {
      retained.apply(geometry)
      retained.forEach { layer, resource ->
        assertSame(original.getValue(layer).first, resource.view)
        assertSame(original.getValue(layer).second, resource.surface)
        assertEquals(geometry, resource.applied)
      }
    }
    assertEquals(2, retained.size)
    assertEquals(0, disposed)
    assertFalse(allocated.containsKey(ExperimentLayer.VIDEO))
  }

  @Test fun unchangedGeometryDoesNotReshapeAgain() {
    var updates = 0
    val retained = ExperimentRetainedOverlays({ Any() }, { _, _, _ -> updates++ }, {})
    repeat(4) { retained.apply(ExperimentGeometry(160, 100)) }
    assertEquals(2, updates)
  }

  @Test fun modeInvalidationReplaysBothRolesAtSameGeometryWithoutReallocating() {
    var allocations = 0
    var updates = 0
    var disposed = 0
    val retained = ExperimentRetainedOverlays(
        allocate = { _: ExperimentLayer -> allocations++; Any() },
        update = { _, _, _ -> updates++ },
        dispose = { _: Any -> disposed++ },
    )
    val geometry = ExperimentGeometry(160, 100)
    retained.apply(geometry)
    retained.invalidate()
    retained.apply(geometry)
    retained.apply(geometry)
    assertEquals(2, allocations)
    assertEquals(4, updates)
    assertEquals(0, disposed)
    retained.close()
    assertThrows(IllegalStateException::class.java) { retained.invalidate() }
  }

  @Test fun partialInitializationFailureKeepsOwnershipAndRetriesWithoutReallocation() {
    var allocations = 0
    var failSubtitle = true
    val applied = mutableListOf<ExperimentLayer>()
    val released = mutableListOf<Any>()
    val retained = ExperimentRetainedOverlays(
        allocate = { _: ExperimentLayer -> allocations++; Any() },
        update = { layer, _, _ ->
          if (layer == ExperimentLayer.SUBTITLE && failSubtitle) error("UI init failed")
          applied += layer
        },
        dispose = { released += it },
    )
    val geometry = ExperimentGeometry(160, 100)
    assertThrows(IllegalStateException::class.java) { retained.apply(geometry) }
    assertEquals(2, retained.size)
    failSubtitle = false
    retained.apply(geometry)
    assertEquals(2, allocations)
    assertEquals(listOf(ExperimentLayer.DANMAKU, ExperimentLayer.SUBTITLE), applied)
    retained.close()
    retained.close()
    assertEquals(2, released.size)
    assertEquals(0, retained.size)
  }

  @Test fun failedGeometryUpdateIsNotAcknowledgedAndSuccessfulRoleIsNotRepeated() {
    var fail = false
    var allocations = 0
    val updates = mutableListOf<ExperimentLayer>()
    val retained = ExperimentRetainedOverlays(
        allocate = { _: ExperimentLayer -> allocations++; Any() },
        update = { layer, _, _ ->
          if (fail && layer == ExperimentLayer.SUBTITLE) error("reshape failed")
          updates += layer
        },
        dispose = {},
    )
    retained.apply(ExperimentGeometry(160, null))
    updates.clear()
    fail = true
    val changed = ExperimentGeometry(240, 100)
    assertThrows(IllegalStateException::class.java) { retained.apply(changed) }
    fail = false
    retained.apply(changed)
    assertEquals(listOf(ExperimentLayer.DANMAKU, ExperimentLayer.SUBTITLE), updates)
    assertEquals(2, allocations)
  }

  @Test fun closeAttemptsEveryRoleRetainsFailedCleanupAndBlocksLateUpdates() {
    var failDanmaku = true
    val disposed = mutableListOf<ExperimentLayer>()
    val retained = ExperimentRetainedOverlays(
        allocate = { layer: ExperimentLayer -> layer },
        update = { _, _, _ -> },
        dispose = { layer ->
          disposed += layer
          if (layer == ExperimentLayer.DANMAKU && failDanmaku) error("destroy failed")
        },
    )
    retained.apply(ExperimentGeometry(160, 100))
    assertThrows(IllegalStateException::class.java) { retained.close() }
    assertEquals(listOf(ExperimentLayer.DANMAKU, ExperimentLayer.SUBTITLE), disposed)
    assertEquals(1, retained.size)
    assertThrows(IllegalStateException::class.java) { retained.apply(ExperimentGeometry(240, 100)) }
    failDanmaku = false
    retained.close()
    retained.close()
    assertEquals(listOf(ExperimentLayer.DANMAKU, ExperimentLayer.SUBTITLE, ExperimentLayer.DANMAKU), disposed)
    assertEquals(0, retained.size)
  }
}
