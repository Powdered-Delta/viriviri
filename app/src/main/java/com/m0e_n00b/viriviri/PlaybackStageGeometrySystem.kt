package com.m0e_n00b.viriviri

import com.meta.spatial.core.SystemBase

/** Commits the latest complete stage layout once panels and their parent are ready. */
internal class PlaybackStageGeometrySystem(private val flush: () -> Unit) : SystemBase() {
  override fun execute() = flush()
}
