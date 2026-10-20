package com.m0e_n00b.viriviri

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Pins down the stage-tap precedence, which is the whole behaviour of the gesture.
 *
 * These are the cases a headset can only confirm slowly; the point of [stageTapOutcome] being pure is that
 * the ordering — especially "void must never swallow the OPEN gesture" and "hitting any object must not
 * void-dismiss" — is a JVM test rather than a device session.
 */
class StageTapDecisionTest {
  @Test
  fun voidWhileTheWorkbenchIsVisibleDismisses() {
    assertEquals(
        StageTapOutcome.VOID_DISMISS,
        stageTapOutcome(
            actionEnabled = true,
            workbenchVisible = true,
            hitEntityPresent = false,
            targetsStage = false,
            occludedByPanel = false,
        ),
    )
  }

  @Test
  fun voidWinsOverAStagePlaneHitWhileVisible() {
    // A void ray can still cross the stage plane. The user's intent ("click any void") is the more
    // specific one, and both outcomes close the workbench, so the ordering only matters to the trace —
    // but it must be stable.
    assertEquals(
        StageTapOutcome.VOID_DISMISS,
        stageTapOutcome(
            actionEnabled = true,
            workbenchVisible = true,
            hitEntityPresent = false,
            targetsStage = true,
            occludedByPanel = false,
        ),
    )
  }

  @Test
  fun hittingAnyObjectOverAwayFromTheStageDoesNothing() {
    // The reported requirement: hitting a non-clickable object is NOT void, so it must not dismiss.
    assertEquals(
        StageTapOutcome.NONE,
        stageTapOutcome(
            actionEnabled = true,
            workbenchVisible = true,
            hitEntityPresent = true,
            targetsStage = false,
            occludedByPanel = false,
        ),
    )
  }

  @Test
  fun hittingAnObjectOverTheStagePlaneUnoccludedToggles() {
    assertEquals(
        StageTapOutcome.TOGGLE_WORKBENCH,
        stageTapOutcome(
            actionEnabled = true,
            workbenchVisible = true,
            hitEntityPresent = true,
            targetsStage = true,
            occludedByPanel = false,
        ),
    )
  }

  @Test
  fun aPanelInFrontOfTheStagePlaneVetoesTheToggle() {
    assertEquals(
        StageTapOutcome.NONE,
        stageTapOutcome(
            actionEnabled = true,
            workbenchVisible = true,
            hitEntityPresent = true,
            targetsStage = true,
            occludedByPanel = true,
        ),
    )
  }

  @Test
  fun voidMustNotSwallowTheOpenGesture() {
    // Workbench hidden: the only legitimate action is opening it. A void ray while hidden means the user
    // is pointing at the hit-less stage, which is precisely how the workbench is opened.
    assertEquals(
        StageTapOutcome.TOGGLE_WORKBENCH,
        stageTapOutcome(
            actionEnabled = true,
            workbenchVisible = false,
            hitEntityPresent = false,
            targetsStage = true,
            occludedByPanel = false,
        ),
    )
    assertEquals(
        StageTapOutcome.NONE,
        stageTapOutcome(
            actionEnabled = true,
            workbenchVisible = false,
            hitEntityPresent = false,
            targetsStage = false,
            occludedByPanel = false,
        ),
    )
  }

  @Test
  fun theOcclusionGuardOnlyEverVetoesAToggle() {
    // Contradictory input on purpose: nothing is hit, yet the guard "says" occluded. Void must still win,
    // because by definition there is no panel under the ray in that case.
    assertEquals(
        StageTapOutcome.VOID_DISMISS,
        stageTapOutcome(
            actionEnabled = true,
            workbenchVisible = true,
            hitEntityPresent = false,
            targetsStage = false,
            occludedByPanel = true,
        ),
    )
  }

  @Test
  fun aDisabledActionProducesNothingInEveryCombination() {
    val booleans = listOf(false, true)
    for (workbenchVisible in booleans) {
      for (hitEntityPresent in booleans) {
        for (targetsStage in booleans) {
          for (occludedByPanel in booleans) {
            assertEquals(
                "actionEnabled=false must never act " +
                    "(visible=$workbenchVisible hit=$hitEntityPresent stage=$targetsStage occ=$occludedByPanel)",
                StageTapOutcome.NONE,
                stageTapOutcome(
                    actionEnabled = false,
                    workbenchVisible = workbenchVisible,
                    hitEntityPresent = hitEntityPresent,
                    targetsStage = targetsStage,
                    occludedByPanel = occludedByPanel,
                ),
            )
          }
        }
      }
    }
  }
}
