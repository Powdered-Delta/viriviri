package com.m0e_n00b.viriviri.geometryexperiment

/** Main-thread ownership only. Old callbacks require both generation and Surface identity. */
internal class ExperimentSurfaceLease<S : Any>(
    private val bind: (S) -> Unit,
    private val clear: (S) -> Unit,
) {
  var generation: Long = 0
    private set
  var surface: S? = null
    private set
  private var closed = false

  fun begin(): Long {
    check(!closed)
    surface?.let { detach(generation, it) }
    return ++generation
  }

  fun attach(expectedGeneration: Long, candidate: S): Boolean {
    if (closed || expectedGeneration != generation) return false
    if (surface === candidate) return true
    check(surface == null)
    // Retain ownership before binding so a partially failed bind can still be cleared.
    surface = candidate
    bind(candidate)
    return true
  }

  fun detach(expectedGeneration: Long, candidate: S): Boolean {
    if (expectedGeneration != generation || surface !== candidate) return false
    clear(candidate)
    surface = null
    return true
  }

  fun close() {
    if (closed) return
    surface?.let { detach(generation, it) }
    closed = true
    generation++
  }
}
