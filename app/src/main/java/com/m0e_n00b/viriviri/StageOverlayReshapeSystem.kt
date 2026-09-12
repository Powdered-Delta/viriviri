package com.m0e_n00b.viriviri

import com.meta.spatial.core.Entity
import com.meta.spatial.core.SystemBase

/**
 * Replays a deferred stage-overlay reshape once per frame.
 *
 * Panel scene objects are instantiated by the Spatial SDK's panel-creation system on a frame *after*
 * [Entity.createPanelEntity], so a reshape requested at creation time is dropped. [flush] re-asserts
 * the pending shapes and must clear its own pending flag once they have landed; while nothing is
 * pending it is a cheap no-op.
 */
internal class StageOverlayReshapeSystem(private val flush: () -> Unit) : SystemBase() {
  override fun execute() {
    flush()
  }
}