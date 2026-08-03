package com.m0e_n00b.spatialworkbench.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeValidatorTest {
  @Test
  fun cinemaThemeIsValidAndResolvesMediaStageForWatchAndFocus() {
    val theme = CinemaTheme.create()

    assertTrue(ThemeValidator.validate(theme).isEmpty())
    assertEquals(PanelVisibility.VISIBLE, theme.placement(ImmersiveLayoutMode.WATCH, PanelSlot.MEDIA_STAGE)?.visibility)
    assertEquals(
        "pip_dock_anchor",
        (theme.placement(ImmersiveLayoutMode.FOCUS, PanelSlot.MEDIA_STAGE)?.anchor as SpatialAnchor.SceneAnchor).name,
    )
  }

  @Test
  fun validatorRejectsMissingSlotReferencesAndInvalidGeometry() {
    val invalidLayout =
        ThemeLayout(
            placements =
                mapOf(
                    PanelSlot.MEDIA_STAGE to
                        SlotPlacement(
                            visibility = PanelVisibility.VISIBLE,
                            anchor = SpatialAnchor.SlotAnchor(PanelSlot.BROWSE),
                            sizeMeters = PanelSizeMeters(-1f, 0f),
                            shape = PanelShape.Cylinder(0f, 400f),
                        )
                )
        )
    val theme = CinemaTheme.create().copy(layouts = mapOf(ImmersiveLayoutMode.WATCH to invalidLayout))

    val issues = ThemeValidator.validate(theme)

    assertTrue(issues.any { it is ThemeValidationIssue.MissingLayout && it.mode == ImmersiveLayoutMode.FOCUS })
    assertTrue(issues.any { it is ThemeValidationIssue.MissingSlotReference && it.mode == ImmersiveLayoutMode.WATCH })
    assertTrue(issues.any { it is ThemeValidationIssue.InvalidPanelSize })
    assertTrue(issues.any { it is ThemeValidationIssue.InvalidCylinderGeometry })
  }

  @Test
  fun validatorReportsTheLayoutModeForFocusSlotReferenceFailures() {
    val invalidFocus =
        ThemeLayout(
            placements =
                mapOf(
                    PanelSlot.MEDIA_STAGE to
                        SlotPlacement(
                            visibility = PanelVisibility.VISIBLE,
                            anchor = SpatialAnchor.SlotAnchor(PanelSlot.CONTEXT),
                            sizeMeters = PanelSizeMeters(1f, 1f),
                        )
                )
        )
    val theme =
        CinemaTheme.create().copy(
            layouts = mapOf(ImmersiveLayoutMode.WATCH to invalidFocus, ImmersiveLayoutMode.FOCUS to invalidFocus),
        )

    val issues = ThemeValidator.validate(theme)

    assertTrue(issues.any { it is ThemeValidationIssue.MissingSlotReference && it.mode == ImmersiveLayoutMode.FOCUS })
  }

  @Test
  fun validatorRejectsDuplicateToolbarIdsDuplicateComponentsAndUnsafeBindings() {
    val duplicateComponent = ComponentNode("context-tabs", ComponentKind.POPUP, dataBindings = mapOf("value" to "currentVideo.title()"))
    val invalidTheme =
        CinemaTheme.create().copy(
            toolbarModules =
                listOf(
                    ToolbarModule("clock", SystemToolbarModule.CLOCK),
                    ToolbarModule("clock", SystemToolbarModule.BATTERY),
                ),
            rootComponent =
                CinemaTheme.create().rootComponent.copy(children = listOf(duplicateComponent)),
        )

    val issues = ThemeValidator.validate(invalidTheme)

    assertTrue(issues.any { it is ThemeValidationIssue.DuplicateToolbarModuleId && it.id == "clock" })
    assertTrue(issues.any { it is ThemeValidationIssue.DuplicateComponentId && it.id == "context-tabs" })
    assertTrue(issues.any { it is ThemeValidationIssue.InvalidDataBinding && it.binding == "currentVideo.title()" })
  }

  @Test
  fun validatorRejectsOutOfRangePlaybackActionsAndInvalidPipDocks() {
    val root =
        ComponentNode(
            id = "root",
            kind = ComponentKind.PANEL,
            actions = listOf(ThemeAction.SetPlaybackSpeed(5f)),
        )
    val layout =
        ThemeLayout(
            placements = CinemaTheme.create().layouts.getValue(ImmersiveLayoutMode.WATCH).placements,
            pipDocks =
                mapOf(
                    "" to
                        PipDock(
                            id = "wrong-id",
                            anchor = SpatialAnchor.SceneAnchor(""),
                            sizeMeters = PanelSizeMeters(0f, 0.3f),
                            exclusion = ContentExclusion("", SpatialAnchor.SceneAnchor("")),
                        )
                ),
        )
    val theme =
        CinemaTheme.create().copy(
            layouts = mapOf(ImmersiveLayoutMode.WATCH to layout, ImmersiveLayoutMode.FOCUS to layout),
            rootComponent = root,
        )

    val issues = ThemeValidator.validate(theme)

    assertTrue(issues.any { it is ThemeValidationIssue.InvalidAction })
    assertTrue(issues.any { it is ThemeValidationIssue.InvalidPipDock })
    assertTrue(issues.any { it is ThemeValidationIssue.InvalidAnchor })
  }
}
