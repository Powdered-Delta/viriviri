package com.m0e_n00b.spatialworkbench.core

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * R3.4/A7/A8: the versioned default theme fixture at `themes/cinema/theme.json` must parse, validate
 * and agree with the compiled fallback fixture [CinemaTheme.create()].
 *
 * The file lives at the repository root (design §5.1) while Gradle runs tests from the module
 * directory, so it is resolved by walking up from the working directory.
 */
class ThemeFixtureRoundTripTest {

  @Test
  fun defaultFixtureParsesWithoutAnyParserIssue() {
    val parsed = ThemeJsonParser.parse(fixtureText())

    assertTrue("parser issues: ${parsed.issues}", parsed.issues.isEmpty())
    assertTrue(parsed.isUsable)
  }

  @Test
  fun defaultFixtureAgreesWithTheCompiledFallbackFixture() {
    val created = CinemaTheme.create()
    val parsed = requireNotNull(ThemeJsonParser.parse(fixtureText()).theme)

    assertEquals(created.schemaVersion, parsed.schemaVersion)
    assertEquals(created.id, parsed.id)
    assertEquals(created.displayName, parsed.displayName)
    assertEquals(created.sceneReference, parsed.sceneReference)
    assertEquals(created.requiredAnchors, parsed.requiredAnchors)
    assertEquals(created.toolbarModules, parsed.toolbarModules)
    assertEquals(created.presentationPolicies, parsed.presentationPolicies)
    assertEquals(created.canvases, parsed.canvases)
    assertEquals(created.componentGroups, parsed.componentGroups)
    assertEquals(created.rootComponent, parsed.rootComponent)
    assertEquals(created.palette, parsed.palette)
    assertEquals(created.panels, parsed.panels)
    assertEquals(created.slotBindings, parsed.slotBindings)
    assertEquals(created.grabGroups, parsed.grabGroups)
    assertEquals(created.danmakuGroups, parsed.danmakuGroups)
    assertEquals(created.danmakuOcclusionDomains, parsed.danmakuOcclusionDomains)
    assertEquals(created.layouts, parsed.layouts)

    // The whole document, not just the fields listed above.
    assertEquals(created, parsed)
  }

  @Test
  fun defaultFixtureValidatesCleanlyAgainstCatalogsThatDescribeTheCurrentApp() {
    val theme = requireNotNull(ThemeJsonParser.parse(fixtureText()).theme)

    // `headsetVerifiedGrabMechanism()` is the only Phase 3.0 record missing today; with it the default
    // fixture is clean, so this test still fails if any other rule starts rejecting it.
    val report = ThemeValidator.inspect(theme, ThemeTestKit.fullCatalogs(ThemeTestKit.headsetVerifiedGrabMechanism()))

    assertTrue("errors: ${report.errors}", report.errors.isEmpty())
    assertTrue("overlay issues: ${report.overlayIssues}", report.overlayIssues.isEmpty())
    assertTrue("warnings: ${report.warnings}", report.warnings.isEmpty())
    assertTrue(report.isValid)
  }

  @Test
  fun defaultFixtureIsRejectedWhenTheSceneDoesNotProvideItsAnchors() {
    val theme = requireNotNull(ThemeJsonParser.parse(fixtureText()).theme)

    val report = ThemeValidator.inspect(theme, ThemeCatalogs(sceneAnchors = ThemeTestKit.emptyAnchorCatalog()))

    // Honest default: the anchors are declared but absent from `app/scenes` today (design §3.2).
    assertTrue(report.errors.any { it is ThemeValidationIssue.MissingSceneAnchor })
    assertTrue(!report.isValid)
  }

  @Test
  fun fixtureExpressesTheOneSlotTwoRailLayoutWithMirroredBindings() {
    val theme = requireNotNull(ThemeJsonParser.parse(fixtureText()).theme)

    val contextBindings = theme.bindingsFor(PanelSlot.CONTEXT)
    assertEquals(2, contextBindings.size)
    assertTrue(contextBindings.all { it.sharePolicy == SlotSharePolicy.MIRRORED })
    assertEquals(setOf("rails"), contextBindings.mapNotNull { it.mirrorGroup }.toSet())
    assertTrue(ThemeValidator.validate(theme).isEmpty())

    // Same document, but the mirror declaration is dropped: the duplicate claim is now rejected.
    val undeclared =
        theme.withBindings(
            theme.slotBindings.map { binding ->
              if (binding.slot == PanelSlot.CONTEXT) binding.copy(sharePolicy = SlotSharePolicy.EXCLUSIVE) else binding
            }
        )
    assertTrue(
        ThemeValidator.validate(undeclared).any { it is ThemeValidationIssue.DuplicateSlotOwner }
    )
  }

  @Test
  fun fixtureNativeParentMechanismIsFlaggedUntilHeadsetVerificationExists() {
    val theme = requireNotNull(ThemeJsonParser.parse(fixtureText()).theme)

    // `fullCatalogs()` carries no verification record, which is the honest state of the project today.
    val report = ThemeValidator.inspect(theme, ThemeTestKit.fullCatalogs())

    assertEquals(
        ThemeIssueSeverity.WARNING,
        report.warnings.single().severity,
    )
    assertTrue(report.warnings.single() is ThemeValidationIssue.UnverifiedGrabMechanism)
    assertTrue(report.errors.isEmpty())
  }

  private fun fixtureText(): String = fixtureFile().readText()

  private fun fixtureFile(): File {
    val relative = "themes/cinema/theme.json"
    var directory: File? = File("").absoluteFile
    while (directory != null) {
      val candidate = File(directory, relative)
      if (candidate.isFile) return candidate
      directory = directory.parentFile
    }
    throw AssertionError("cannot locate $relative from ${File("").absolutePath}")
  }
}
