package com.m0e_n00b.viriviri

import com.m0e_n00b.spatialworkbench.compose.LibraryComponents
import com.m0e_n00b.spatialworkbench.core.CinemaTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Makes the component directory authoritative instead of decorative.
 *
 * `ThemeComponentRegistry` shipped in Phase 1 as a contract with no instance, so its `UnknownComponent`
 * check could never fire against a real table. These tests are what turn the table into something with
 * consequences: ids must be unique and collision-free, every panel content component the default theme
 * declares must be registered, and the set of theme ids that have **no** implementation is pinned so it
 * can neither grow nor shrink unnoticed.
 */
class AppComponentCatalogueTest {

  @Test
  fun everyCatalogueIdIsUnique() {
    val ids = AppComponentCatalogue.descriptors().map { it.id }
    assertEquals(
        "duplicate ComponentId in the catalogue: the theme validator's UnknownComponent check would " +
            "resolve ambiguously",
        ids.size,
        ids.toSet().size,
    )
  }

  @Test
  fun noTwoIdsCollideOnceCaseAndDashesAreNormalised() {
    // The ComponentId space holds both kebab-case library ids (`workbench-title`) and camelCase panel
    // content ids (`mediaStage`). Those are deliberately different conventions, but a pair that differs
    // only by dashes/case would be indistinguishable to a human reading a theme file, and the compiler
    // would not catch it. Cheaper to forbid than to debug.
    val normalised = AppComponentCatalogue.ids.groupBy { it.value.lowercase().replace("-", "") }
    val collisions = normalised.filterValues { it.size > 1 }
    assertTrue(
        "these ComponentIds collide once case and dashes are removed: " +
            collisions.values.joinToString("; ") { group -> group.joinToString(" vs ") { it.value } },
        collisions.isEmpty(),
    )
  }

  @Test
  fun theLibraryHalfIsNonEmptyAndResolvable() {
    val registry = LibraryComponents.registry()
    assertTrue("the library catalogue must not be empty", LibraryComponents.descriptors.isNotEmpty())
    LibraryComponents.descriptors.forEach { descriptor ->
      assertEquals(
          "registry must resolve ${descriptor.id}",
          descriptor,
          registry.descriptor(descriptor.id),
      )
    }
  }

  @Test
  fun everyPanelContentComponentTheDefaultThemeDeclaresIsRegistered() {
    val registered = AppComponentCatalogue.ids
    val missing =
        CinemaTheme.create()
            .panels
            .values
            .map { it.componentId }
            .filterNot { it in registered }
            .map { it.value }
            .sorted()
    assertEquals(
        "the default theme names panel content components that no module registers: $missing",
        emptyList<String>(),
        missing,
    )
  }

  @Test
  fun theUnimplementedThemeVocabularyIsPinned() {
    // The point of this assertion is its failure modes, both of which are wanted:
    //  - someone adds a new semantic id to the theme without implementing it  -> the set grows -> fail
    //  - someone registers a component under one of these ids               -> the set shrinks -> fail
    // A failure means the theme's vocabulary and the implementation's vocabulary moved relative to each
    // other, which is exactly the drift that stayed invisible while the registry had no instance.
    val referenced = CinemaTheme.create().referencedComponentIds()
    val unimplemented =
        referenced.filterNot { it in AppComponentCatalogue.ids }.mapTo(linkedSetOf()) { it }

    assertEquals(
        "theme-referenced ids with no registered implementation changed. " +
            "Added: ${(unimplemented - AppComponentCatalogue.themeComponentsWithoutImplementation).map { it.value }} " +
            "Closed: ${(AppComponentCatalogue.themeComponentsWithoutImplementation - unimplemented).map { it.value }}",
        AppComponentCatalogue.themeComponentsWithoutImplementation,
        unimplemented,
    )
  }

  @Test
  fun theCatalogueCoversEveryThemeReferenceExceptThePinnedGap() {
    // Stated as a coverage ratio so the size of the gap is visible in the test output rather than only
    // in a comment. It is not an assertion about how large the gap *should* be -- that is a product
    // decision -- only that it is the pinned one.
    val referenced = CinemaTheme.create().referencedComponentIds()
    val covered = referenced.count { it in AppComponentCatalogue.ids }
    assertEquals(
        "covered ${covered} of ${referenced.size} theme-referenced ComponentIds",
        referenced.size - AppComponentCatalogue.themeComponentsWithoutImplementation.size,
        covered,
    )
  }
}
