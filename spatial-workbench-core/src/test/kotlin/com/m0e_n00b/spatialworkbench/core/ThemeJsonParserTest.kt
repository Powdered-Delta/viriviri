package com.m0e_n00b.spatialworkbench.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** R3.1: the hand-rolled JSON reader and the theme mapper, including locatable field paths. */
class ThemeJsonParserTest {

  @Test
  fun malformedJsonIsRejectedWithTheDocumentPath() {
    val result = ThemeJsonParser.parse("""{ "schemaVersion": 1, """)

    assertNull(result.theme)
    val issue = result.issues.single() as ThemeValidationIssue.MalformedField
    assertEquals("$", issue.path)
    assertTrue(issue.reason.startsWith("malformed-json"))
  }

  @Test
  fun nonObjectRootAndMissingSchemaVersionAreRejected() {
    val array = ThemeJsonParser.parse("[1, 2, 3]")
    assertNull(array.theme)
    assertEquals("$", (array.issues.single() as ThemeValidationIssue.MalformedField).path)

    val missing = ThemeJsonParser.parse("""{ "id": "cinema" }""")
    assertNull(missing.theme)
    assertTrue(
        missing.issues.any {
          it is ThemeValidationIssue.MalformedField && it.path == "schemaVersion" && it.reason == "missing"
        }
    )
  }

  @Test
  fun wrongFieldTypeIsReportedAtItsExactPath() {
    val result = ThemeJsonParser.parse(
        """
        {
          "schemaVersion": 1,
          "id": "cinema",
          "displayName": "Cinema",
          "scene": { "reference": "Composition" },
          "panels": {
            "transport": {
              "panelClass": "UI_PANEL",
              "component": "transport",
              "anchor": { "sceneNode": "transport_anchor" },
              "offsetMeters": { "x": 0.0, "y": "high", "z": 0.0 },
              "sizeMeters": { "width": 0.9, "height": 0.24 }
            }
          }
        }
        """.trimIndent()
    )

    assertTrue(
        result.issues.any {
          it is ThemeValidationIssue.MalformedField &&
              it.path == "panels.transport.offsetMeters.y" &&
              it.reason == "expected-number"
        }
    )
  }

  @Test
  fun unknownPanelClassIsReportedAtItsPathAndExcludesThePanel() {
    val result = ThemeJsonParser.parse(
        """
        {
          "schemaVersion": 1,
          "id": "cinema",
          "displayName": "Cinema",
          "scene": { "reference": "Composition" },
          "panels": {
            "ghost": {
              "panelClass": "HOLOGRAM_PANEL",
              "component": "transport",
              "anchor": { "sceneNode": "transport_anchor" },
              "sizeMeters": { "width": 0.9, "height": 0.24 }
            }
          }
        }
        """.trimIndent()
    )

    val issue = result.issues.single { it is ThemeValidationIssue.UnknownPanelClass } as ThemeValidationIssue.UnknownPanelClass
    assertEquals("ghost", issue.panelId)
    assertEquals("HOLOGRAM_PANEL", issue.rawClass)
    assertEquals("panels.ghost.panelClass", issue.path)
    assertTrue(result.theme!!.panels.isEmpty())
  }

  @Test
  fun parsedThemeIsThenRangeValidatedWithTheSameFieldPath() {
    val parsed = ThemeJsonParser.parse(
        """
        {
          "schemaVersion": 1,
          "id": "cinema",
          "displayName": "Cinema",
          "scene": { "reference": "Composition" },
          "panels": {
            "transport": {
              "panelClass": "UI_PANEL",
              "component": "transport",
              "anchor": { "sceneNode": "transport_anchor" },
              "offsetMeters": { "x": 0.0, "y": 9.0, "z": 0.0 },
              "sizeMeters": { "width": 0.9, "height": 0.24 },
              "themeableGeometry": ["offsetMeters"]
            }
          }
        }
        """.trimIndent()
    )
    val theme = requireNotNull(parsed.theme)

    val issues = ThemeValidator.validate(theme)

    assertTrue(
        issues.any {
          it is ThemeValidationIssue.OffsetOutOfRange && it.path == "panels.transport.offsetMeters.y"
        }
    )
  }

  @Test
  fun parsesLayoutsPanelGeometryActionsAndPaletteRoles() {
    val parsed =
        ThemeJsonParser.parse(
            """
            {
              "schemaVersion": 1,
              "id": "cinema",
              "displayName": "Cinema",
              "scene": {
                "reference": "Composition",
                "requiredAnchors": ["transport_anchor"]
              },
              "layouts": {
                "WATCH": {
                  "placements": {
                    "TRANSPORT": {
                      "visibility": "HIDDEN",
                      "anchor": { "sceneNode": "transport_anchor" },
                      "offsetMeters": { "x": 1.0, "y": -2.0, "z": 3.0 },
                      "rotationDegrees": { "pitch": 10.0, "yaw": -20.0, "roll": 30.0 },
                      "sizeMeters": { "width": 1.2, "height": 0.4 },
                      "shape": { "kind": "cylinder", "radiusMeters": 4.0, "arcDegrees": 60.0 },
                      "depthRelation": "BEHIND_PARENT"
                    }
                  }
                }
              },
              "panels": {
                "transport": {
                  "panelClass": "UI_PANEL",
                  "component": "transport",
                  "anchor": { "sceneNode": "transport_anchor" },
                  "sizeMeters": { "width": 1.2, "height": 0.4 },
                  "zOrder": 12,
                  "hitTest": "CUSTOM_RECT",
                  "userAdjustable": ["stageScale"],
                  "themeableGeometry": ["sizeMeters", "shape", "zOrder", "hitTest"]
                }
              },
              "slotBindings": [
                { "slot": "TRANSPORT", "panel": "transport", "sharePolicy": "EXCLUSIVE" }
              ],
              "componentGroups": [
                {
                  "id": "group",
                  "label": "Group",
                  "reusable": false,
                  "allowedSlots": ["TRANSPORT"],
                  "members": [
                    {
                      "id": "seek-timeline",
                      "kind": "SEMANTIC_MODULE",
                      "properties": { "slot": "TRANSPORT" },
                      "actions": [{ "type": "SetPlaybackSpeed", "speed": 1.5 }]
                    }
                  ]
                }
              ],
              "rootComponent": { "id": "root", "kind": "PANEL" },
              "palette": {
                "normalText": { "red": 245, "green": 247, "blue": 250 },
                "surface": { "red": 28, "green": 32, "blue": 40 },
                "primaryButtonLabel": { "red": 255, "green": 255, "blue": 255 },
                "primaryButton": { "red": 55, "green": 112, "blue": 214 },
                "secondaryButtonLabel": { "red": 245, "green": 247, "blue": 250 },
                "secondaryButton": { "red": 58, "green": 66, "blue": 78 },
                "chargingBadgeLabel": { "red": 255, "green": 255, "blue": 255 },
                "chargingBadge": { "red": 188, "green": 48, "blue": 112 },
                "danger": { "red": 9, "green": 8, "blue": 7 },
                "surfaceOpacity": 0.5
              }
            }
            """.trimIndent()
        )

    assertTrue(parsed.issues.isEmpty())
    val theme = requireNotNull(parsed.theme)
    assertEquals(setOf("transport_anchor"), theme.requiredAnchors)

    val placement = theme.layouts.getValue(ImmersiveLayoutMode.WATCH).placements.getValue(PanelSlot.TRANSPORT)
    assertEquals(PanelVisibility.HIDDEN, placement.visibility)
    assertEquals(Meters3(1f, -2f, 3f), placement.offsetMeters)
    assertEquals(EulerDegrees(10f, -20f, 30f), placement.rotationDegrees)
    assertEquals(SpatialDepthRelation.BEHIND_PARENT, placement.depthRelation)
    assertEquals(PanelShape.Cylinder(4f, 60f), placement.shape)

    val panel = theme.panels.getValue(PanelId("transport"))
    assertEquals(PanelClass.UI_PANEL, panel.panelClass)
    assertEquals(12, panel.zOrder)
    assertEquals(PanelHitTest.CUSTOM_RECT, panel.hitTest)
    assertEquals(setOf(UserAdjustableProperty.STAGE_SCALE), panel.userAdjustable)
    assertEquals(SlotSharePolicy.EXCLUSIVE, theme.slotBindings.single().sharePolicy)

    val group = theme.componentGroups.single()
    assertEquals("Group", group.label)
    assertEquals(false, group.reusable)
    assertEquals(setOf(PanelSlot.TRANSPORT), group.allowedSlots)
    assertEquals(
        listOf(ThemeAction.SetPlaybackSpeed(1.5f)),
        group.members.single().actions,
    )

    // The explicit palette roles are honored; unspecified roles fall back to the DARK preset.
    assertEquals(RgbColor(9, 8, 7), theme.palette.danger)
    assertEquals(0.5f, theme.palette.surfaceOpacity)
    assertEquals(CinemaPalette.DARK.chargingBadge, theme.palette.chargingBadge)
  }

  @Test
  fun unknownActionNamesAreRefusedAtTheParseBoundary() {
    // R8.2: the controlled action whitelist is closed. An action the app has no implementation for
    // cannot be expressed at all, so it is refused while parsing instead of being dropped silently.
    val parsed =
        ThemeJsonParser.parse(
            """
            {
              "schemaVersion": 1,
              "id": "cinema",
              "displayName": "Cinema",
              "scene": { "reference": "Composition" },
              "rootComponent": {
                "id": "root",
                "kind": "PANEL",
                "actions": [{ "type": "CreateEntity" }, { "type": "SetQuality", "quality": "HIGH" }]
              }
            }
            """.trimIndent()
        )

    assertTrue(
        parsed.issues.any {
          it is ThemeValidationIssue.MalformedField &&
              it.reason == "unknown-action:CreateEntity" &&
              it.path == "rootComponent.actions[0].type"
        }
    )
    assertTrue(
        parsed.issues.any {
          it is ThemeValidationIssue.MalformedField &&
              it.reason == "unknown-action:SetQuality" &&
              it.path == "rootComponent.actions[1].type"
        }
    )
    assertTrue(requireNotNull(parsed.theme).rootComponent.actions.isEmpty())
  }

  @Test
  fun readerHandlesEscapesNumbersAndNesting() {
    val read = ThemeJsonReader.read("""{"a":"line\n\"quoted\"\u0041","b":-1.5e2,"c":[true,false,null],"d":{}}""")

    val node = (read as ThemeJsonReadResult.Parsed).node as ThemeJsonNode.Obj
    assertEquals("line\n\"quoted\"A", (node.get("a") as ThemeJsonNode.Str).value)
    assertEquals(-150.0, (node.get("b") as ThemeJsonNode.Num).value, 0.0001)
    assertEquals(3, (node.get("c") as ThemeJsonNode.Arr).values.size)
    assertTrue(node.get("d") is ThemeJsonNode.Obj)
  }
}
