package com.m0e_n00b.spatialworkbench.core

import com.m0e_n00b.spatialworkbench.core.ThemeTestKit.panel
import com.m0e_n00b.spatialworkbench.core.ThemeTestKit.placement
import com.m0e_n00b.spatialworkbench.core.ThemeTestKit.sceneAnchor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** R5 geometric ranges and the injected scene-anchor catalog (A5, design §5.3). */
class ThemeGeometryValidatorTest {

  private val transport = panel("transport")

  @Test
  fun offsetIsBoundedAtThreeMetersOnEveryAxis() {
    assertTrue(issueFor(offset = Meters3(ThemeValidator.MAX_OFFSET_METERS, 0f, 0f)) == null)
    assertTrue(issueFor(offset = Meters3(0f, -ThemeValidator.MAX_OFFSET_METERS, 0f)) == null)

    listOf(
            "x" to Meters3(3.01f, 0f, 0f),
            "y" to Meters3(0f, -4f, 0f),
            "z" to Meters3(0f, 0f, 3.5f),
        )
        .forEach { (axis, offset) ->
          val issue = issueFor(offset = offset)
          assertTrue("expected an issue for axis $axis", issue is ThemeValidationIssue.OffsetOutOfRange)
          assertEquals(axis, (issue as ThemeValidationIssue.OffsetOutOfRange).axis)
          assertEquals("panels.transport.offsetMeters.$axis", issue.path)
        }

    assertTrue(
        issueFor(offset = Meters3(Float.NaN, 0f, 0f)) is ThemeValidationIssue.OffsetOutOfRange
    )
  }

  @Test
  fun rotationIsBoundedPerAxis() {
    assertTrue(issueFor(rotation = EulerDegrees(80f, 180f, -80f)) == null)

    val pitch = issueFor(rotation = EulerDegrees(80.5f, 0f, 0f)) as ThemeValidationIssue.RotationOutOfRange
    assertEquals("pitch", pitch.axis)
    assertEquals("panels.transport.rotationDegrees.pitch", pitch.path)

    val roll = issueFor(rotation = EulerDegrees(0f, 0f, -81f)) as ThemeValidationIssue.RotationOutOfRange
    assertEquals("roll", roll.axis)

    val yaw = issueFor(rotation = EulerDegrees(0f, 181f, 0f)) as ThemeValidationIssue.RotationOutOfRange
    assertEquals("yaw", yaw.axis)
  }

  @Test
  fun sizeIsBoundedBetweenFifteenCentimetersAndFourMeters() {
    assertTrue(issueFor(size = PanelSizeMeters(0.15f, 4f)) == null)
    assertTrue(issueFor(size = PanelSizeMeters(0.14f, 1f)) is ThemeValidationIssue.InvalidPanelSize)
    assertTrue(issueFor(size = PanelSizeMeters(1f, 4.1f)) is ThemeValidationIssue.InvalidPanelSize)
    assertTrue(issueFor(size = PanelSizeMeters(Float.NaN, 1f)) is ThemeValidationIssue.InvalidPanelSize)
  }

  @Test
  fun cylinderRadiusAndArcAreBounded() {
    assertTrue(issueFor(shape = PanelShape.Cylinder(1.5f, 0.1f)) == null)
    assertTrue(issueFor(shape = PanelShape.Cylinder(8f, 359.9f)) == null)
    assertTrue(issueFor(shape = PanelShape.Cylinder(1.4f, 60f)) is ThemeValidationIssue.InvalidCylinderGeometry)
    assertTrue(issueFor(shape = PanelShape.Cylinder(8.1f, 60f)) is ThemeValidationIssue.InvalidCylinderGeometry)
    assertTrue(issueFor(shape = PanelShape.Cylinder(4f, 0.05f)) is ThemeValidationIssue.InvalidCylinderGeometry)
    assertTrue(issueFor(shape = PanelShape.Cylinder(4f, 360f)) is ThemeValidationIssue.InvalidCylinderGeometry)
  }

  @Test
  fun zOrderIsBoundedToPlusMinusOneHundred() {
    assertTrue(zOrderIssue(-100) == null)
    assertTrue(zOrderIssue(100) == null)
    assertTrue(zOrderIssue(-101) is ThemeValidationIssue.ZOrderOutOfRange)
    assertTrue(zOrderIssue(101) is ThemeValidationIssue.ZOrderOutOfRange)
  }

  @Test
  fun nonHittablePanelCannotAlsoBeUserAdjustable() {
    val theme =
        ThemeTestKit.cinema()
            .withPanel(
                transport.copy(
                    hitTest = PanelHitTest.NONE,
                    themeableGeometry = transport.themeableGeometry + ThemeableGeometry.HIT_TEST,
                    userAdjustable = setOf(UserAdjustableProperty.STAGE_SCALE),
                )
            )

    val issues = ThemeValidator.validate(theme)

    assertTrue(
        issues.any {
          it is ThemeValidationIssue.HitTestConflict && it.path == "panels.transport.hitTest"
        }
    )
  }

  @Test
  fun currentDefaultThemeDeclaresOnlyRealSceneAnchors() {
    val theme = ThemeTestKit.cinema()

    val issues = ThemeValidator.validate(theme, ThemeTestKit.fullCatalogs())

    assertTrue(issues.none { it is ThemeValidationIssue.MissingSceneAnchor })
  }

  @Test
  fun missingSceneAnchorsAreReportedOnlyWhenTheCatalogIsInjected() {
    val theme = ThemeTestKit.cinema()

    assertTrue(ThemeValidator.validate(theme).none { it is ThemeValidationIssue.MissingSceneAnchor })

    val issues = ThemeValidator.validate(theme, ThemeCatalogs(sceneAnchors = ThemeTestKit.emptyAnchorCatalog()))

    // Every declared anchor plus every panel anchor is dangling: the scene contains none of them.
    assertTrue(issues.any { it is ThemeValidationIssue.MissingSceneAnchor && it.anchor == "media_stage_anchor" })
    assertTrue(issues.any { it is ThemeValidationIssue.MissingSceneAnchor && it.anchor == "context_anchor" })
    assertTrue(
        issues.any {
          it is ThemeValidationIssue.MissingSceneAnchor && it.context == "scene.requiredAnchors"
        }
    )
    assertTrue(
        issues.any {
          it is ThemeValidationIssue.MissingSceneAnchor && it.path == "panels.mediaStage.anchor"
        }
    )
  }

  @Test
  fun legacyLayoutPlacementAnchorsAreCatalogCheckedToo() {
    val theme = ThemeTestKit.cinema()
    val catalog = SceneAnchorCatalog.of(listOf("media_stage_anchor"))

    val issues = ThemeValidator.validate(theme, ThemeCatalogs(sceneAnchors = catalog))

    assertTrue(
        issues.any {
          it is ThemeValidationIssue.MissingSceneAnchor &&
              it.path == "layouts.WATCH.placements.TRANSPORT.anchor"
        }
    )
  }

  @Test
  fun unknownComponentAndPanelClassMismatchAreCatalogGated() {
    val theme = ThemeTestKit.cinema()
    val registry = ThemeTestKit.cinemaComponentRegistry()

    assertTrue(ThemeValidator.validate(theme).none { it is ThemeValidationIssue.UnknownComponent })
    assertTrue(
        ThemeValidator.validate(theme, ThemeCatalogs(components = registry))
            .none { it is ThemeValidationIssue.UnknownComponent }
    )

    val unknown =
        theme.withPanel(transport.copy(componentId = ComponentId("not-registered")))
    assertTrue(
        ThemeValidator.validate(unknown, ThemeCatalogs(components = registry))
            .any { it is ThemeValidationIssue.UnknownComponent && it.componentId == "not-registered" }
    )

    // `centerContent` only exists as a scene panel component, not as a UI panel component.
    val mismatched = theme.withPanel(panel("centerContent").copy(panelClass = PanelClass.UI_PANEL))
    assertTrue(
        ThemeValidator.validate(mismatched, ThemeCatalogs(components = registry))
            .any { it is ThemeValidationIssue.ComponentClassMismatch && it.panelId == "centerContent" }
    )
  }

  @Test
  fun geometryOutsideThePanelClassWhitelistIsRejected() {
    val media = panel("mediaStage")

    // Declaring a property the class does not open at all.
    val declared = ThemeTestKit.cinema().withPanel(
        media.copy(themeableGeometry = media.themeableGeometry + ThemeableGeometry.ROTATION_DEGREES)
    )
    assertTrue(
        ThemeValidator.validate(declared).any {
          it is ThemeValidationIssue.GeometryNotThemeable &&
              it.panelId == "mediaStage" &&
              it.property == ThemeableGeometry.ROTATION_DEGREES
        }
    )

    // Declaring a value that is neither a class default nor themeable: the fake capability.
    val used =
        ThemeTestKit.cinema().withPanel(
            media.copy(placement = media.placement.copy(rotationDegrees = EulerDegrees(yaw = 15f)))
        )
    assertTrue(
        ThemeValidator.validate(used).any {
          it is ThemeValidationIssue.GeometryNotThemeable &&
              it.property == ThemeableGeometry.ROTATION_DEGREES &&
              it.path == "panels.mediaStage.rotationDegrees"
        }
    )
  }

  private fun issueFor(
      offset: Meters3 = Meters3(),
      rotation: EulerDegrees = EulerDegrees(),
      size: PanelSizeMeters = transport.placement.sizeMeters,
      shape: PanelShape = PanelShape.Quad,
  ): ThemeValidationIssue? {
    val theme =
        ThemeTestKit.cinema()
            .withPanel(
                transport.copy(
                    placement =
                        placement(
                            anchor = sceneAnchor("transport_anchor"),
                            offset = offset,
                            rotation = rotation,
                            shape = shape,
                        ).copy(sizeMeters = size)
                )
            )
    return ThemeValidator.validate(theme).firstOrNull {
      it is ThemeValidationIssue.OffsetOutOfRange ||
          it is ThemeValidationIssue.RotationOutOfRange ||
          it is ThemeValidationIssue.InvalidPanelSize ||
          it is ThemeValidationIssue.InvalidCylinderGeometry
    }
  }

  private fun zOrderIssue(zOrder: Int): ThemeValidationIssue? {
    val theme = ThemeTestKit.cinema().withPanel(transport.copy(zOrder = zOrder))
    return ThemeValidator.validate(theme).firstOrNull { it is ThemeValidationIssue.ZOrderOutOfRange }
  }
}
