package com.m0e_n00b.spatialworkbench.core

/*
 * Injected catalogs (R3.2/R3.3, design §5.3). `ThemeValidator` must stay a pure function that can be
 * asserted in a JVM test, so "does this scene anchor exist" and "is this entity ref registered" are
 * injected rather than hard-coded. The scene-anchor, entity, component and overlay-surface catalogs
 * are deliberately opt-in: those checks are only reported when the catalog is supplied, which is what
 * keeps `ThemeValidator.validate(CinemaTheme.create())` valid even though the scene does not contain
 * the anchors `CinemaTheme` declares (design §3.2).
 *
 * [ThemeCatalogs.grabVerification] is the deliberate exception: it carries *positive evidence*, so an
 * absent catalog means "no record exists" (unverified) and is reported, not skipped.
 */

/** Scene-authored anchor node names (Meta Spatial Editor export). */
fun interface SceneAnchorCatalog {
  fun contains(name: String): Boolean

  companion object {
    val EMPTY: SceneAnchorCatalog = SceneAnchorCatalog { false }

    fun of(names: Iterable<String>): SceneAnchorCatalog {
      val set = names.toSet()
      return SceneAnchorCatalog { it in set }
    }
  }
}

/** Semantic entity references known to the adapter (scene nodes + runtime registrations). */
fun interface EntityCatalog {
  fun contains(ref: EntityRef): Boolean

  companion object {
    val EMPTY: EntityCatalog = EntityCatalog { false }

    fun of(refs: Iterable<EntityRef>): EntityCatalog {
      val set = refs.toSet()
      return EntityCatalog { it in set }
    }

    fun ofNames(names: Iterable<String>): EntityCatalog = of(names.map(::EntityRef))
  }
}

/**
 * One entry of the controlled component directory (design §4.2). This is the only place a theme may
 * take a UI component from; outside it there is no implementation to resolve.
 */
data class ThemeComponentDescriptor(
    val id: ComponentId,
    val kind: ComponentKind,
    /** Slots this component may be attributed to; empty = unrestricted. */
    val allowedSlots: Set<SlotId> = emptySet(),
    /** Panel classes this component can be rendered by. */
    val compatiblePanelClasses: Set<PanelClass> = PanelClass.entries.toSet(),
    /** Compose implementation or semantic-module id, resolved by the app adapter. */
    val implementationRef: String,
)

/** The controlled component directory: `ComponentId -> descriptor`. */
interface ThemeComponentRegistry {
  fun descriptor(id: ComponentId): ThemeComponentDescriptor?

  fun ids(): Set<ComponentId>

  companion object {
    val EMPTY: ThemeComponentRegistry = MapComponentRegistry(emptyMap())

    fun of(descriptors: Iterable<ThemeComponentDescriptor>): ThemeComponentRegistry =
        MapComponentRegistry(descriptors.associateBy(ThemeComponentDescriptor::id))
  }
}

private class MapComponentRegistry(
    private val descriptors: Map<ComponentId, ThemeComponentDescriptor>,
) : ThemeComponentRegistry {
  override fun descriptor(id: ComponentId): ThemeComponentDescriptor? = descriptors[id]

  override fun ids(): Set<ComponentId> = descriptors.keys
}

/** Danmaku/caption render targets declared by the host; never a video Surface. */
interface OverlaySurfaceCatalog {
  fun spec(id: OverlaySurfaceId): OverlaySurfaceSpec?

  fun all(): List<OverlaySurfaceSpec>

  companion object {
    val EMPTY: OverlaySurfaceCatalog =
        object : OverlaySurfaceCatalog {
          override fun spec(id: OverlaySurfaceId): OverlaySurfaceSpec? = null

          override fun all(): List<OverlaySurfaceSpec> = emptyList()
        }

    fun of(surfaces: Iterable<OverlaySurfaceSpec>): OverlaySurfaceCatalog {
      val byId = surfaces.associateBy(OverlaySurfaceSpec::id)
      return object : OverlaySurfaceCatalog {
        override fun spec(id: OverlaySurfaceId): OverlaySurfaceSpec? = byId[id.value]

        override fun all(): List<OverlaySurfaceSpec> = byId.values.toList()
      }
    }
  }
}

/**
 * Headset verification records for grab mechanisms (design §4.3, Phase 3.0 gate). `NATIVE_PARENT`
 * is a code-structure fact plus an *unverified* SDK behaviour, so the validator reports
 * `UnverifiedGrabMechanism` as a warning unless a **positive** record for the group and mechanism is
 * supplied here. An absent or empty catalog means "no record exists", i.e. unverified — never a
 * silent pass. The catalog is optional only in the sense that its absence cannot make a theme valid
 * silently; the warning is a warning, so it never fails `ThemeValidator.validate`.
 */
fun interface GrabMechanismVerificationCatalog {
  fun isVerified(groupId: String, mechanism: GrabMechanism): Boolean

  companion object {
    val NONE: GrabMechanismVerificationCatalog = GrabMechanismVerificationCatalog { _, _ -> false }

    fun ofVerified(entries: Iterable<Pair<String, GrabMechanism>>): GrabMechanismVerificationCatalog {
      val set = entries.toSet()
      return GrabMechanismVerificationCatalog { groupId, mechanism -> (groupId to mechanism) in set }
    }
  }
}

/** The set of catalogs a caller may inject. Absent catalogs disable the checks that need them. */
data class ThemeCatalogs(
    val sceneAnchors: SceneAnchorCatalog? = null,
    val entities: EntityCatalog? = null,
    val components: ThemeComponentRegistry? = null,
    val overlaySurfaces: OverlaySurfaceCatalog? = null,
    val grabVerification: GrabMechanismVerificationCatalog? = null,
) {
  companion object {
    val NONE: ThemeCatalogs = ThemeCatalogs()
  }
}
