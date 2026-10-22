package com.m0e_n00b.viriviri.geometryexperiment

/** Activity-owned UI resources outlive individual video generations. Main-thread only. */
internal class ExperimentRetainedOverlays<T>(
    private val allocate: (ExperimentLayer) -> T,
    private val update: (ExperimentLayer, T, ExperimentGeometry) -> Unit,
    private val dispose: (T) -> Unit,
) {
  private class Entry<T>(val resource: T, var applied: ExperimentGeometry? = null)
  private val entries = linkedMapOf<ExperimentLayer, Entry<T>>()
  private var closed = false
  val size: Int get() = entries.size

  /** A render-policy change must replay at identical geometry without reallocating UI. */
  fun invalidate() {
    check(!closed) { "Overlay ownership is closed" }
    entries.values.forEach { it.applied = null }
  }

  fun apply(geometry: ExperimentGeometry) {
    check(!closed) { "Overlay ownership is closed" }
    for (layer in listOf(ExperimentLayer.DANMAKU, ExperimentLayer.SUBTITLE)) {
      // Own the resource before SDK initialization/update can fail.
      val entry = entries.getOrPut(layer) { Entry(allocate(layer)) }
      if (entry.applied == geometry) continue
      update(layer, entry.resource, geometry)
      entry.applied = geometry
    }
  }

  fun forEach(block: (ExperimentLayer, T) -> Unit) {
    entries.forEach { (layer, entry) -> block(layer, entry.resource) }
  }

  fun close() {
    closed = true
    var failure: Exception? = null
    for ((layer, entry) in entries.toMap()) {
      try {
        dispose(entry.resource)
        entries.remove(layer)
      } catch (error: Exception) {
        if (failure == null) failure = error else failure.addSuppressed(error)
      }
    }
    // Successful parts are gone; only failed cleanup is retried on a later close.
    failure?.let { throw it }
  }
}
