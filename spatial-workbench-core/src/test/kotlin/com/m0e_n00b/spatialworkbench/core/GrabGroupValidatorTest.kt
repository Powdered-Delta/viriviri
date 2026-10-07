package com.m0e_n00b.spatialworkbench.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** R4.2/R4.5: grab-group correctness rules and the unverified-mechanism warning (A5, A9). */
class GrabGroupValidatorTest {

  @Test
  fun defaultGrabGroupCombinesTheCurrentParentChildStructure() {
    val group = ThemeTestKit.cinema().grabGroups.single()

    assertEquals(DefaultWorkbenchGrabGroup.ID, group.id)
    assertEquals(EntityRef("workbenchRoot"), group.handleRef)
    assertEquals(GrabMechanism.NATIVE_PARENT, group.mechanism)
    assertEquals(setOf(EntityRef("grabBar"), EntityRef("mediaStage")), group.memberRefs)
    assertEquals(GrabGroupLifecycle.ALWAYS, group.lifecycle)
    assertEquals(GrabLostTrackingPolicy.FREEZE_AT_LAST_POSE, group.lostTrackingPolicy)
  }

  @Test
  fun parentAndChildGroupSharingAMemberIsRejected() {
    val child =
        GrabGroupSpec(
            id = "grabBarAlone",
            handleRef = EntityRef("grabBar"),
            parentGroupId = DefaultWorkbenchGrabGroup.ID,
            members = listOf(GrabMemberSpec(EntityRef("mediaStage"))),
        )
    val theme = ThemeTestKit.cinema().withGrabGroup(child)

    val overlap = ThemeValidator.validate(theme).filterIsInstance<ThemeValidationIssue.GrabMemberOverlap>().single()
    assertEquals("grabBarAlone", overlap.childGroupId)
    assertEquals(DefaultWorkbenchGrabGroup.ID, overlap.parentGroupId)
    assertEquals("mediaStage", overlap.ref)
    assertEquals("grabGroups.grabBarAlone.members[mediaStage]", overlap.path)
  }

  @Test
  fun handleMayNotBeItsOwnMember() {
    val selfReferential =
        GrabGroupSpec(
            id = "self",
            handleRef = EntityRef("workbenchRoot"),
            members = listOf(GrabMemberSpec(EntityRef("workbenchRoot"))),
        )
    val theme = ThemeTestKit.cinema().withGrabGroup(selfReferential)

    val issue =
        ThemeValidator.validate(theme).filterIsInstance<ThemeValidationIssue.GrabHandleSelfReference>().single()
    assertEquals("self", issue.groupId)
    assertEquals("workbenchRoot", issue.ref)
  }

  @Test
  fun exclusionMustBeSymmetric() {
    val asymmetric =
        GrabGroupSpec(
            id = "left",
            handleRef = EntityRef("grabBar"),
            members = listOf(GrabMemberSpec(EntityRef("mediaStage"))),
            exclusiveWith = setOf("right"),
        )
    val theme = ThemeTestKit.cinema().withGrabGroup(asymmetric)

    val issue =
        ThemeValidator.validate(theme).filterIsInstance<ThemeValidationIssue.AsymmetricExclusion>().single()
    assertEquals("left", issue.groupId)
    assertEquals("right", issue.otherGroupId)

    val symmetric =
        theme
            .withGrabGroup(asymmetric.copy(id = "left", exclusiveWith = setOf("right")))
            .withGrabGroup(
                GrabGroupSpec(
                    id = "right",
                    handleRef = EntityRef("grabBar"),
                    members = listOf(GrabMemberSpec(EntityRef("grabBar"))),
                    exclusiveWith = setOf("left"),
                )
            )
    assertTrue(ThemeValidator.validate(symmetric).none { it is ThemeValidationIssue.AsymmetricExclusion })
  }

  @Test
  fun entityReferencesAreCheckedAgainstTheInjectedCatalog() {
    val theme = ThemeTestKit.cinema()

    assertTrue(ThemeValidator.validate(theme).none { it is ThemeValidationIssue.UnknownEntityRef })

    val issues =
        ThemeValidator.validate(
            theme,
            ThemeCatalogs(entities = EntityCatalog.ofNames(listOf("workbenchRoot"))),
        )

    assertTrue(
        issues.any {
          it is ThemeValidationIssue.UnknownEntityRef &&
              it.ref == "grabBar" &&
              it.path == "grabGroups.workbench.members[grabBar]"
        }
    )
    assertTrue(
        issues.any {
          it is ThemeValidationIssue.UnknownEntityRef &&
              it.ref == "mediaStage" &&
              it.path == "grabGroups.workbench.members[mediaStage]"
        }
    )
  }

  @Test
  fun nativeParentWithoutAVerificationRecordIsAWarningNotASilentPass() {
    val theme = ThemeTestKit.cinema()

    // A missing verification record means "unverified", not "verified". Both an absent catalog and an
    // explicitly empty one must report the warning, otherwise `NATIVE_PARENT` silently passes (A9).
    listOf(ThemeCatalogs.NONE, ThemeTestKit.fullCatalogs(GrabMechanismVerificationCatalog.NONE))
        .forEach { catalogs ->
          val report = ThemeValidator.inspect(theme, catalogs)
          val warning = report.warnings.filterIsInstance<ThemeValidationIssue.UnverifiedGrabMechanism>().single()
          assertEquals(DefaultWorkbenchGrabGroup.ID, warning.groupId)
          assertEquals(GrabMechanism.NATIVE_PARENT, warning.mechanism)
          assertEquals("grabGroups.workbench.mechanism", warning.path)
          assertEquals(ThemeIssueSeverity.WARNING, warning.severity)
          assertTrue(report.errors.isEmpty())
          assertTrue(report.isValid)
        }

    // Errors only: a warning never fails the theme, which is what keeps `validate` backwards compatible.
    assertTrue(ThemeValidator.validate(theme, ThemeTestKit.fullCatalogs()).isEmpty())
    assertTrue(ThemeValidator.validate(theme).isEmpty())

    val verified = ThemeValidator.inspect(theme, ThemeTestKit.fullCatalogs(ThemeTestKit.headsetVerifiedGrabMechanism()))
    assertTrue(verified.warnings.isEmpty())
  }

  @Test
  fun deltaMirrorDoesNotDependOnTheNativeParentVerificationRecord() {
    val theme =
        ThemeTestKit.cinema()
            .withGrabGroup(
                ThemeTestKit.cinema().grabGroups.single().copy(mechanism = GrabMechanism.DELTA_MIRROR)
            )

    // `DELTA_MIRROR` does not rely on the unverified SDK parent-child propagation, so it needs no record.
    assertTrue(
        ThemeValidator.inspect(theme, ThemeCatalogs.NONE).warnings.none {
          it is ThemeValidationIssue.UnverifiedGrabMechanism
        }
    )
  }

  @Test
  fun parentGroupMustExistAndMustNotCycle() {
    val dangling =
        ThemeTestKit.cinema()
            .withGrabGroup(ThemeTestKit.cinema().grabGroups.single().copy(parentGroupId = "ghost"))
    assertTrue(
        ThemeValidator.validate(dangling).any {
          it is ThemeValidationIssue.UnknownGrabGroupRef && it.parentGroupId == "ghost"
        }
    )

    val cyclic =
        ThemeTestKit.cinema()
            .withGrabGroups(
                listOf(
                    ThemeTestKit.cinema().grabGroups.single().copy(parentGroupId = "secondary"),
                    GrabGroupSpec(
                        id = "secondary",
                        handleRef = EntityRef("grabBar"),
                        parentGroupId = DefaultWorkbenchGrabGroup.ID,
                        members = listOf(GrabMemberSpec(EntityRef("mediaStage"))),
                    ),
                )
            )
    val cycle = ThemeValidator.validate(cyclic).filterIsInstance<ThemeValidationIssue.GrabGroupParentCycle>().first()
    assertEquals(setOf(DefaultWorkbenchGrabGroup.ID, "secondary"), cycle.groupIds.toSet())
  }

  @Test
  fun duplicateGrabGroupIdsAreRejected() {
    val theme = ThemeTestKit.cinema().withGrabGroups(listOf(ThemeTestKit.cinema().grabGroups.single(), ThemeTestKit.cinema().grabGroups.single()))

    assertTrue(
        ThemeValidator.validate(theme).any {
          it is ThemeValidationIssue.DuplicateGrabGroupId && it.id == DefaultWorkbenchGrabGroup.ID
        }
    )
  }

  @Test
  fun grabGroupsAreIndependentFromUiCompositionGroups() {
    val theme = ThemeTestKit.cinema()

    // Same `EntityRef` space is never the `ComponentId` space: a member id and a component id
    // happening to be spelled alike do not make the two groups interchangeable.
    assertFalse(theme.componentGroups.any { it.id == theme.grabGroups.single().id })
    assertTrue(theme.grabGroups.single().members.all { member -> member.entityRef.value !in theme.componentGroups.single().members.map { it.id } })
  }
}
