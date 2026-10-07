package com.m0e_n00b.spatialworkbench.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** R4.1: UI composition groups reorder members and stay inside the controlled registry. */
class ComponentCompositionResolverTest {

  private val registry =
      ThemeComponentRegistry.of(
          listOf(
              ThemeComponentDescriptor(
                  id = ComponentId("watch-title"),
                  kind = ComponentKind.SEMANTIC_MODULE,
                  implementationRef = "app://module/watch-title",
              ),
              ThemeComponentDescriptor(
                  id = ComponentId("seek-timeline"),
                  kind = ComponentKind.SEMANTIC_MODULE,
                  allowedSlots = setOf(PanelSlot.TRANSPORT),
                  implementationRef = "app://module/seek-timeline",
              ),
              ThemeComponentDescriptor(
                  id = ComponentId("media-stage"),
                  kind = ComponentKind.SEMANTIC_MODULE,
                  implementationRef = "app://module/media-stage",
              ),
          )
      )

  @Test
  fun memberOrderIsTheRenderOrder() {
    val group = ComponentGroup("group", listOf(ComponentNode("watch-title", ComponentKind.SEMANTIC_MODULE), ComponentNode("seek-timeline", ComponentKind.SEMANTIC_MODULE)))

    val reordered =
        group.copy(
            members =
                listOf(
                    ComponentNode("seek-timeline", ComponentKind.SEMANTIC_MODULE),
                    ComponentNode("watch-title", ComponentKind.SEMANTIC_MODULE),
                )
        )

    assertEquals(
        listOf("watch-title", "seek-timeline"),
        ComponentCompositionResolver.resolve(group, registry).orderedMembers.map(ComponentNode::id),
    )
    assertEquals(
        listOf("seek-timeline", "watch-title"),
        ComponentCompositionResolver.resolve(reordered, registry).orderedMembers.map(ComponentNode::id),
    )
  }

  @Test
  fun parentsPrecedeTheirChildrenAndNamedSlotsAreFlattened() {
    val child = ComponentNode("seek-timeline", ComponentKind.SEMANTIC_MODULE)
    val parent =
        ComponentNode(
            id = "watch-title",
            kind = ComponentKind.PANEL,
            children = listOf(child),
            namedSlots = mapOf("extra" to listOf(ComponentNode("media-stage", ComponentKind.SEMANTIC_MODULE))),
        )

    val resolved = ComponentCompositionResolver.resolve(ComponentGroup("group", listOf(parent)), registry)

    assertEquals(listOf("watch-title", "seek-timeline", "media-stage"), resolved.orderedMembers.map(ComponentNode::id))
  }

  @Test
  fun componentsOutsideTheControlledRegistryAreRejected() {
    val group = ComponentGroup("group", listOf(ComponentNode("unregistered-widget", ComponentKind.POPUP)))

    assertTrue(ComponentCompositionResolver.resolve(group).issues.isEmpty())

    val issue = ComponentCompositionResolver.resolve(group, registry).issues.single()
    assertEquals(
        ThemeValidationIssue.UnknownComponent("componentGroups.group", "unregistered-widget", "componentGroups.group.members[unregistered-widget]"),
        issue,
    )
  }

  @Test
  fun groupSlotWhitelistIsEnforced() {
    val outside = ComponentNode("seek-timeline", ComponentKind.SEMANTIC_MODULE, properties = mapOf("slot" to "BROWSE"))
    val inside = ComponentNode("seek-timeline", ComponentKind.SEMANTIC_MODULE, properties = mapOf("slot" to "TRANSPORT"))

    val violated = ComponentCompositionResolver.resolve(ComponentGroup("group", listOf(outside), allowedSlots = setOf(PanelSlot.TRANSPORT)), registry)
    val allowed = ComponentCompositionResolver.resolve(ComponentGroup("group", listOf(inside), allowedSlots = setOf(PanelSlot.TRANSPORT)), registry)

    val issue = violated.issues.single() as ThemeValidationIssue.ComponentSlotNotAllowed
    assertEquals("group", issue.groupId)
    assertEquals(PanelSlot.BROWSE, issue.slot)
    assertTrue(allowed.issues.isEmpty())
  }

  @Test
  fun themeValidatorReusesTheResolverForComponentGroups() {
    val theme = ThemeTestKit.cinema()
    val broken =
        theme.copy(
            componentGroups =
                listOf(
                    theme.componentGroups.single().copy(
                        members = theme.componentGroups.single().members + ComponentNode("ghost-widget", ComponentKind.POPUP)
                    )
                )
        )

    assertTrue(
        ThemeValidator.validate(broken, ThemeCatalogs(components = ThemeTestKit.cinemaComponentRegistry()))
            .any {
              it is ThemeValidationIssue.UnknownComponent &&
                  it.componentId == "ghost-widget" &&
                  it.owner == "componentGroups.default-cinema-playback-canvas"
            }
    )
    assertTrue(ThemeValidator.validate(broken).none { it is ThemeValidationIssue.UnknownComponent })
  }

  @Test
  fun componentGroupSpecsCarryLabelSlotWhitelistAndReusability() {
    val group = ComponentGroup("group", emptyList(), label = "My Group", allowedSlots = setOf(PanelSlot.BROWSE), reusable = false)

    assertEquals("My Group", group.effectiveLabel)
    assertEquals("group", ComponentGroup("group", emptyList()).effectiveLabel)
    assertEquals(setOf(PanelSlot.BROWSE), group.allowedSlots)
    assertTrue(!group.reusable)
  }
}
