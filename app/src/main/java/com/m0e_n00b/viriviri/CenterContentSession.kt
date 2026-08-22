package com.m0e_n00b.viriviri

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

internal enum class CenterContentMode { PLAYBACK, VIDEO_LIST, SEARCH }

/** UI-only center content state. It owns no Spatial entity, player, Surface, or network operation. */
internal object CenterContentSession {
  private val mutableMode = MutableStateFlow(CenterContentMode.PLAYBACK)
  val mode: StateFlow<CenterContentMode> = mutableMode.asStateFlow()

  fun show(mode: CenterContentMode) {
    mutableMode.value = mode
  }
}
