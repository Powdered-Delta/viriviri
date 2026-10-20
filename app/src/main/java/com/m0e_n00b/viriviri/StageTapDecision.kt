package com.m0e_n00b.viriviri

/**
 * One logcat tag for everything the stage tap decides, shared by the tuning system and the host.
 *
 * Kept as a single constant rather than a literal in each file: the whole point of the diagnostic is
 * that one `adb logcat -s` invocation shows the press, the facts behind the verdict, and what the host
 * did with it, in order.
 */
internal const val STAGE_TAP_TAG = "ViriViriStageTap"

/**
 * What a single trigger press on the media stage should do.
 *
 * Split out of [AnalogMediaStageTuningSystem] so the precedence — which is the whole behaviour — is a
 * pure function a JVM test can pin down, instead of something only a headset can observe.
 */
internal enum class StageTapOutcome {
  /** Nothing this system owns: a panel (or nothing at all) is the business of someone else. */
  NONE,

  /** Open or close the workbench — the reducer decides which, this only says "run the stage action". */
  TOGGLE_WORKBENCH,

  /** Close the workbench because the ray pointed at no object at all. */
  VOID_DISMISS,
}

/**
 * The precedence, and why it is this order:
 *
 * 1. `actionEnabled` false → [StageTapOutcome.NONE]. The host owns this gate.
 * 2. Workbench HIDDEN → toggle on a stage-plane hit only. Void must never swallow the OPEN gesture,
 *    so this branch cannot produce [StageTapOutcome.VOID_DISMISS] at all.
 * 3. Workbench VISIBLE and the ray hit **no object** (`hitEntityPresent == false`) →
 *    [StageTapOutcome.VOID_DISMISS]. This is the user's reported gesture: "click any void with no
 *    entity". It is checked BEFORE the stage-plane verdict on purpose — a void ray can still cross the
 *    stage plane, and void is the more specific intent.
 * 4. Workbench VISIBLE, ray on the stage plane, and no interactive panel in front of it →
 *    [StageTapOutcome.TOGGLE_WORKBENCH]. This is the close half of the stage toggle.
 * 5. Otherwise → [StageTapOutcome.NONE]. In particular, hitting **any** object — clickable or not —
 *    must NOT void-dismiss. That is deliberate: the panel being pointed at owns the press.
 *
 * [occludedByPanel] only ever vetoes a TOGGLE. It must not veto a VOID_DISMISS, because by definition
 * there is no panel under the ray in that case.
 */
internal fun stageTapOutcome(
    actionEnabled: Boolean,
    workbenchVisible: Boolean,
    hitEntityPresent: Boolean,
    targetsStage: Boolean,
    occludedByPanel: Boolean,
): StageTapOutcome {
  if (!actionEnabled) return StageTapOutcome.NONE
  if (!workbenchVisible) {
    return if (targetsStage && !occludedByPanel) {
      StageTapOutcome.TOGGLE_WORKBENCH
    } else {
      StageTapOutcome.NONE
    }
  }
  if (!hitEntityPresent) return StageTapOutcome.VOID_DISMISS
  if (targetsStage && !occludedByPanel) return StageTapOutcome.TOGGLE_WORKBENCH
  return StageTapOutcome.NONE
}
