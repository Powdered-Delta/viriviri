package com.m0e_n00b.viriviri

internal enum class ImmersiveTransportPrimaryAction {
  REVEAL_TRANSPORT,
}

internal data class ImmersiveTransportOverlayState(
    val visible: Boolean = true,
)

/** Pure visibility and stage-primary-click policy for the existing transport panel. */
internal object ImmersiveTransportOverlayPolicy {
  fun shouldScheduleIdleFade(isActuallyPlaying: Boolean): Boolean = isActuallyPlaying

  fun primaryAction(): ImmersiveTransportPrimaryAction =
      ImmersiveTransportPrimaryAction.REVEAL_TRANSPORT
}
