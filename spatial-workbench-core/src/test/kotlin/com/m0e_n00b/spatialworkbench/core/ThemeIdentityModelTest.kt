package com.m0e_n00b.spatialworkbench.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** R7.1/R7.2/R4.4: five distinct id spaces, slot-binding defaults and closed theme boundaries. */
class ThemeIdentityModelTest {

  @Test
  fun fiveIdentitySpacesAreDistinctTypesOverTheSameUnderlyingText() {
    val panel: PanelId = PanelId("mediaStage")
    val component: ComponentId = ComponentId("media-stage")
    val entity: EntityRef = EntityRef("mediaStage")
    val surface: OverlaySurfaceId = OverlaySurfaceId("danmaku_overlay_panel")
    val slot: SlotId = PanelSlot.MEDIA_STAGE

    // Same text, different spaces: the compiler refuses to interchange them.
    assertEquals("mediaStage", panel.value)
    assertEquals("media-stage", component.value)
    assertEquals("mediaStage", entity.value)
    assertEquals("danmaku_overlay_panel", surface.value)
    assertEquals(PanelSlot.MEDIA_STAGE, slot)

    // An overlay surface is not a UI slot and does not participate in the visibility recipe.
    val theme = ThemeTestKit.cinema()
    assertFalse(theme.canvases.any { canvas -> canvas.visibleSlots.any { it.name == surface.value.uppercase() } })
    assertTrue(theme.danmakuGroups.single().layers.single().surfaceIds.contains(surface.value))
  }

  @Test
  fun slotIsTheOnlyOneToManySemanticSpace() {
    val theme = ThemeTestKit.cinema()

    // SlotId is explicitly 1:N (one CONTEXT slot, two panels); PanelId is not (1 panel = 1 instance),
    // so no panel may appear twice across the bindings (R7.1, enforced by DuplicateSlotOwner).
    assertEquals(2, theme.bindingsFor(PanelSlot.CONTEXT).size)
    val boundPanels = theme.slotBindings.map(SlotBinding::panelId)
    assertEquals(boundPanels.size, boundPanels.toSet().size)
    assertEquals(theme.panels.size, theme.slotBindings.map(SlotBinding::panelId).toSet().size)
  }

  @Test
  fun slotBindingsDefaultToExclusive() {
    val binding = SlotBinding(PanelSlot.TRANSPORT, PanelId("transport"))

    assertEquals(SlotSharePolicy.EXCLUSIVE, binding.sharePolicy)
    assertEquals(null, binding.mirrorGroup)
    assertEquals("slotBindings[TRANSPORT->transport]", binding.path)
  }

  @Test
  fun themeActionWhitelistStaysClosed() {
    // An exhaustive `when` without an `else` branch is the compile-time proof that no theme can
    // express an action outside the whitelist of design §5.4. The ordered list below additionally
    // forces a deliberate edit of this expectation whenever the whitelist changes.
    val whitelist =
        listOf(
            ThemeAction.OpenRoute(ThemeRoute.SERIES),
            ThemeAction.OpenFocus(FocusTarget.CREATOR),
            ThemeAction.SetPlaybackSpeed(1f),
            ThemeAction.SetDanmakuMode(DanmakuMode.FLAT),
            ThemeAction.SwitchTo2D,
            ThemeAction.TogglePassthrough,
        ).map { action ->
          when (action) {
            is ThemeAction.OpenRoute -> "OpenRoute"
            is ThemeAction.OpenFocus -> "OpenFocus"
            is ThemeAction.SetPlaybackSpeed -> "SetPlaybackSpeed"
            is ThemeAction.SetDanmakuMode -> "SetDanmakuMode"
            ThemeAction.SwitchTo2D -> "SwitchTo2D"
            ThemeAction.TogglePassthrough -> "TogglePassthrough"
          }
        }

    assertEquals(
        listOf("OpenRoute", "OpenFocus", "SetPlaybackSpeed", "SetDanmakuMode", "SwitchTo2D", "TogglePassthrough"),
        whitelist,
    )
    assertTrue(ThemeTestKit.cinema().rootComponent.actions.isEmpty())
  }

  @Test
  fun controlledComponentRegistryIsTheOnlyComponentSource() {
    val registry = ThemeTestKit.cinemaComponentRegistry()

    assertTrue(ComponentId("mediaStage") in registry.ids())
    assertEquals(null, registry.descriptor(ComponentId("ghost")))
    assertEquals(
        setOf(PanelClass.MEDIA_SURFACE),
        registry.descriptor(ComponentId("mediaStage"))!!.compatiblePanelClasses,
    )
    assertEquals(ThemeComponentRegistry.EMPTY.ids(), emptySet<ComponentId>())
  }

  @Test
  fun panelSpecSeparatesThemeStaticGeometryFromUserAdjustableSettings() {
    val panel = ThemeTestKit.panel("mediaStage")

    assertTrue(panel.canTheme(ThemeableGeometry.SIZE_METERS))
    assertFalse(panel.canTheme(ThemeableGeometry.Z_ORDER))
    assertTrue(panel.isThemeStatic(ThemeableGeometry.SHAPE))
    assertTrue(panel.userAdjustable.isEmpty())
    assertEquals(PanelHitTest.CUSTOM_RECT, panel.hitTest)
    assertEquals(AttachPoint.BOTTOM_CENTER, ThemeTestKit.panel("transport").parentAttach)
  }
}
