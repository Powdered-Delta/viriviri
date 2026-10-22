package com.m0e_n00b.viriviri.geometryexperiment

/** Main-thread latest-value coalescing; the Activity owns the delayed callback and display. */
internal class ExperimentGeometryUpdates(
    private val schedule: (() -> Unit) -> Unit,
    private val cancel: () -> Unit,
    private val apply: (ExperimentGeometry) -> Unit,
) {
  private var pending: ExperimentGeometry? = null
  private var scheduled = false
  private var epoch = 0L
  private var closed = false

  fun request(geometry: ExperimentGeometry) {
    if (closed) return
    pending = geometry
    if (scheduled) return
    scheduled = true
    val expectedEpoch = epoch
    schedule {
      if (!closed && expectedEpoch == epoch) flush()
    }
  }

  fun flush() {
    val next = pending
    discard()
    if (!closed && next != null) apply(next)
  }

  fun replace(geometry: ExperimentGeometry) {
    discard()
    if (!closed) apply(geometry)
  }

  fun close() {
    closed = true
    discard()
  }

  private fun discard() {
    epoch++
    cancel()
    scheduled = false
    pending = null
  }
}
