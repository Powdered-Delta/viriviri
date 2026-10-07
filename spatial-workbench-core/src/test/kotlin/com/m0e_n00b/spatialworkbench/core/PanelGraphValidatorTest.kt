package com.m0e_n00b.spatialworkbench.core

import com.m0e_n00b.spatialworkbench.core.ThemeTestKit.panel
import com.m0e_n00b.spatialworkbench.core.ThemeTestKit.panelId
import com.m0e_n00b.spatialworkbench.core.ThemeTestKit.placement
import com.m0e_n00b.spatialworkbench.core.ThemeTestKit.sceneAnchor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** R7.1/R7.2/R7.4: panel graph, slot bindings and the conditional slot-ownership rule (A6, A8). */
class PanelGraphValidatorTest {

  @Test
  fun existingOneSlotTwoRailsLayoutIsExpressedAsMirroredAndValidates() {
    val theme = ThemeTestKit.cinema()

    val contextBindings = theme.bindingsFor(PanelSlot.CONTEXT)
    assertEquals(2, contextBindings.size)
    assertTrue(contextBindings.all { it.sharePolicy == SlotSharePolicy.MIRRORED })
    assertEquals(setOf("rails"), contextBindings.mapNotNull { it.mirrorGroup }.toSet())
    assertTrue(ThemeValidator.validate(theme).isEmpty())
  }

  @Test
  fun undeclaredDuplicateSlotOwnerIsRejected() {
    val theme = ThemeTestKit.cinema()
    val exclusive =
        theme.withBindings(
            theme.slotBindings.map { binding ->
              if (binding.slot == PanelSlot.CONTEXT) binding.copy(sharePolicy = SlotSharePolicy.EXCLUSIVE) else binding
            }
        )

    val issues = ThemeValidator.validate(exclusive)

    val duplicate = issues.filterIsInstance<ThemeValidationIssue.DuplicateSlotOwner>().single()
    assertEquals(PanelSlot.CONTEXT, duplicate.slot)
    assertEquals(listOf("leftDetailRail", "rightContextRail"), duplicate.panelIds)
  }

  @Test
  fun independentBindingsAndMixedPoliciesAreHandled() {
    val theme = ThemeTestKit.cinema()
    val independent =
        theme.withBindings(
            theme.slotBindings.map { binding ->
              if (binding.slot == PanelSlot.CONTEXT) {
                binding.copy(sharePolicy = SlotSharePolicy.INDEPENDENT, mirrorGroup = null)
              } else {
                binding
              }
            }
        )
    assertTrue(ThemeValidator.validate(independent).none { it is ThemeValidationIssue.DuplicateSlotOwner })

    val mixed =
        theme.withBindings(
            theme.slotBindings.map { binding ->
              if (binding.panelId == panelId("rightContextRail")) {
                binding.copy(sharePolicy = SlotSharePolicy.INDEPENDENT)
              } else {
                binding
              }
            }
        )
    assertTrue(ThemeValidator.validate(mixed).any { it is ThemeValidationIssue.DuplicateSlotOwner })
  }

  @Test
  fun aSlotMayNotBeClaimedTwiceByTheSamePanel() {
    val theme = ThemeTestKit.cinema()
    val left = panelId("leftDetailRail")
    val right = panelId("rightContextRail")

    // A repeated self-claim must not be usable to satisfy the `MIRRORED` pairing rule: the slot is
    // bound by *panels*, so `MIRRORED` needs two distinct panels (R7.2/A8).
    val duplicated =
        theme.withBindingsFor(
            PanelSlot.CONTEXT,
            SlotBinding(PanelSlot.CONTEXT, left, SlotSharePolicy.MIRRORED, "rails"),
            SlotBinding(PanelSlot.CONTEXT, left, SlotSharePolicy.MIRRORED, "rails"),
        )
    val duplicatedIssues = ThemeValidator.validate(duplicated)
    assertTrue(duplicatedIssues.any { it is ThemeValidationIssue.DuplicateSlotOwner })
    assertTrue(
        duplicatedIssues.any {
          it is ThemeValidationIssue.IncompleteMirrorGroup &&
              it.reason == "mirrored-requires-at-least-two-bindings"
        }
    )

    // Same panel twice under `INDEPENDENT` is a duplicate claim too, not an independent extra state.
    val samePanelIndependent =
        theme.withBindingsFor(
            PanelSlot.CONTEXT,
            SlotBinding(PanelSlot.CONTEXT, left, SlotSharePolicy.INDEPENDENT),
            SlotBinding(PanelSlot.CONTEXT, left, SlotSharePolicy.INDEPENDENT),
        )
    assertTrue(
        ThemeValidator.validate(samePanelIndependent).any { it is ThemeValidationIssue.DuplicateSlotOwner }
    )

    // A genuine pair plus a redundant repeat of one of its panels is still a duplicate claim, while
    // the pair itself stays complete.
    val pairPlusRepeat =
        theme.withBindingsFor(
            PanelSlot.CONTEXT,
            SlotBinding(PanelSlot.CONTEXT, left, SlotSharePolicy.MIRRORED, "rails"),
            SlotBinding(PanelSlot.CONTEXT, right, SlotSharePolicy.MIRRORED, "rails"),
            SlotBinding(PanelSlot.CONTEXT, right, SlotSharePolicy.MIRRORED, "rails"),
        )
    val pairIssues = ThemeValidator.validate(pairPlusRepeat)
    assertTrue(pairIssues.any { it is ThemeValidationIssue.DuplicateSlotOwner })
    assertTrue(pairIssues.none { it is ThemeValidationIssue.IncompleteMirrorGroup })
  }

  @Test
  fun mirroredBindingsRequireAGroupAndAtLeastTwoMembers() {
    val theme = ThemeTestKit.cinema()

    val missingGroup =
        theme.withBindings(
            theme.slotBindings.map { binding ->
              if (binding.slot == PanelSlot.CONTEXT) binding.copy(mirrorGroup = null) else binding
            }
        )
    assertTrue(
        ThemeValidator.validate(missingGroup).any {
          it is ThemeValidationIssue.IncompleteMirrorGroup && it.reason == "mirrored-requires-mirror-group"
        }
    )

    val splitGroups =
        theme.withBindings(
            theme.slotBindings.map { binding ->
              if (binding.panelId == panelId("rightContextRail")) binding.copy(mirrorGroup = "other") else binding
            }
        )
    assertTrue(
        ThemeValidator.validate(splitGroups).any {
          it is ThemeValidationIssue.IncompleteMirrorGroup &&
              it.reason == "mirrored-bindings-must-share-one-mirror-group"
        }
    )

    val singleMirrored =
        theme.withBindings(
            listOf(
                SlotBinding(PanelSlot.CONTEXT, panelId("leftDetailRail"), SlotSharePolicy.MIRRORED, "rails")
            )
        )
    assertTrue(
        ThemeValidator.validate(singleMirrored).any {
          it is ThemeValidationIssue.IncompleteMirrorGroup &&
              it.reason == "mirrored-requires-at-least-two-bindings"
        }
    )
  }

  @Test
  fun bindingToAnUndeclaredPanelIsRejected() {
    val theme = ThemeTestKit.cinema()
    val dangling =
        theme.withBindings(
            theme.slotBindings.map { binding ->
              if (binding.panelId == panelId("rightContextRail")) binding.copy(panelId = panelId("ghost")) else binding
            }
        )

    val issues = ThemeValidator.validate(dangling)

    val unknown = issues.filterIsInstance<ThemeValidationIssue.UnknownPanelRef>().single()
    assertEquals(PanelSlot.CONTEXT, unknown.slot)
    assertEquals("ghost", unknown.panelId)
    assertTrue(unknown.path.startsWith("slotBindings[CONTEXT->ghost]"))
    // The two CONTEXT claims are still mirrored, so the only complaint is the dangling reference.
    assertTrue(issues.none { it is ThemeValidationIssue.DuplicateSlotOwner })
  }

  @Test
  fun panelParentCyclesAreRejected() {
    val theme = ThemeTestKit.cinema()
    val cyclic =
        theme
            .withPanel(panel("mediaStage").copy(parentPanelId = panelId("transport")))
            .withPanel(panel("transport").copy(parentPanelId = panelId("mediaStage")))

    val issues = ThemeValidator.validate(cyclic)

    val cycle = issues.filterIsInstance<ThemeValidationIssue.ParentCycle>().first()
    assertEquals(setOf("mediaStage", "transport"), cycle.panelIds.toSet())
  }

  @Test
  fun anchorAndPanelParentTogetherAreAmbiguous() {
    val theme = ThemeTestKit.cinema()
    val ambiguous =
        theme.withPanel(
            panel("transport").copy(
                placement =
                    panel("transport").placement.copy(anchor = sceneAnchor("transport_anchor"))
            )
        )

    val issue =
        ThemeValidator.validate(ambiguous).filterIsInstance<ThemeValidationIssue.AmbiguousPlacement>().single()
    assertEquals("transport", issue.panelId)
    assertEquals("panels.transport.parent", issue.path)
  }

  @Test
  fun depthRelationWithoutAnyParentIsRejected() {
    val theme = ThemeTestKit.cinema()
    val detached =
        theme.withPanel(
            panel("transport").copy(
                parentPanelId = null,
                placement =
                    panel("transport").placement.copy(
                        anchor = sceneAnchor("transport_anchor"),
                        depthRelation = SpatialDepthRelation.FRONT_OF_PARENT,
                    ),
            )
        )

    val issue =
        ThemeValidator.validate(detached)
            .filterIsInstance<ThemeValidationIssue.DepthRelationWithoutParent>()
            .single()
    assertEquals("transport", issue.panelId)
    assertEquals("panels.transport.depthRelation", issue.path)
  }

  @Test
  fun placementWithoutAnchorOrParentIsRejected() {
    val theme = ThemeTestKit.cinema()
    val dangling =
        theme.withPanel(
            panel("transport").copy(
                parentPanelId = null,
                placement = placement(anchor = null, width = 0.9f, height = 0.24f),
            )
        )

    assertTrue(
        ThemeValidator.validate(dangling).any {
          it is ThemeValidationIssue.InvalidAnchor && it.path == "panels.transport.anchor"
        }
    )
  }

  @Test
  fun parentPanelMustExist() {
    val theme = ThemeTestKit.cinema()
    val orphan = theme.withPanel(panel("transport").copy(parentPanelId = panelId("ghost")))

    val issue =
        ThemeValidator.validate(orphan).filterIsInstance<ThemeValidationIssue.UnknownPanelRef>().single()
    assertEquals(null, issue.slot)
    assertEquals("ghost", issue.panelId)
    assertEquals("panels.transport.parent.panel", issue.path)
  }

  @Test
  fun panelRegistryKeyMustMatchThePanelId() {
    val theme = ThemeTestKit.cinema()
    val mismatched =
        theme.withBindings(theme.slotBindings).copy(panels = theme.panels - panelId("transport") + (panelId("t") to panel("transport")))

    assertTrue(
        ThemeValidator.validate(mismatched).any {
          it is ThemeValidationIssue.InvalidThemeIdentity && it.field == "panels.t.id"
        }
    )
  }

  @Test
  fun removingAPanelBreaksItsBindingsOnly() {
    val theme = ThemeTestKit.cinema().withoutPanel("systemToolbar")

    val issues = ThemeValidator.validate(theme)

    assertTrue(
        issues.any {
          it is ThemeValidationIssue.UnknownPanelRef && it.panelId == "systemToolbar"
        }
    )
    assertFalse(issues.any { it is ThemeValidationIssue.DuplicateSlotOwner })
  }

  @Test
  fun panelClassesExposeTheirThemeableGeometrySubset() {
    assertEquals(
        setOf(ThemeableGeometry.SIZE_METERS, ThemeableGeometry.SHAPE),
        PanelClass.MEDIA_SURFACE.allowedGeometry,
    )
    assertTrue(ThemeableGeometry.Z_ORDER in PanelClass.SCENE_PANEL.allowedGeometry)
    assertEquals(ThemeableGeometry.entries.toSet(), PanelClass.UI_PANEL.allowedGeometry)
    assertFalse(ThemeableGeometry.ROTATION_DEGREES in PanelClass.MEDIA_SURFACE.allowedGeometry)
  }
}
