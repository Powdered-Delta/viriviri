package com.m0e_n00b.spatialworkbench.core

/*
 * Theme schema versioning (R2, design §5.1). Migration runs on the raw document **before**
 * validation so that the validator only ever sees the current schema. Unknown or future versions are
 * rejected with `UnsupportedSchemaVersion`; a theme is never silently downgraded.
 */

object ThemeSchema {
  /** The schema revision this build of core understands and validates. */
  const val CURRENT_SCHEMA_VERSION: Int = 1

  /** Every schema revision that can be loaded without migration steps. */
  val SUPPORTED_SCHEMA_VERSIONS: Set<Int> = setOf(CURRENT_SCHEMA_VERSION)

  fun isSupported(version: Int): Boolean = version in SUPPORTED_SCHEMA_VERSIONS
}

/** One pure migration step: `vN -> vN+1` on the raw document, with no I/O and no side effects. */
fun interface ThemeSchemaStep {
  fun migrate(document: ThemeJsonNode.Obj): ThemeJsonNode.Obj
}

data class ThemeSchemaMigrationResult(
    /** Migrated document, or null when the version is rejected. */
    val document: ThemeJsonNode.Obj?,
    val issues: List<ThemeValidationIssue> = emptyList(),
) {
  val isUsable: Boolean
    get() = document != null && issues.isEmpty()
}

/**
 * A chain of [ThemeSchemaStep]s, each mapping version `N` to `N+1`. The default chain is empty
 * because schema version 1 is the current revision; later revisions register their step here.
 */
class ThemeSchemaMigrationChain(
    private val steps: Map<Int, ThemeSchemaStep> = emptyMap(),
    private val targetVersion: Int = ThemeSchema.CURRENT_SCHEMA_VERSION,
) {
  val supportedVersions: Set<Int>
    get() = (ThemeSchema.SUPPORTED_SCHEMA_VERSIONS + steps.keys).filterTo(linkedSetOf()) { it <= targetVersion }

  fun migrate(document: ThemeJsonNode.Obj, fromVersion: Int): ThemeSchemaMigrationResult {
    if (fromVersion > ThemeSchema.CURRENT_SCHEMA_VERSION) {
      return rejected(fromVersion, "future-version")
    }
    if (fromVersion < 1) {
      return rejected(fromVersion, "unknown-version")
    }
    if (fromVersion > targetVersion) {
      return rejected(fromVersion, "future-version")
    }

    var current = document
    var version = fromVersion
    while (version < targetVersion) {
      val step = steps[version] ?: return rejected(version, "missing-migration-step")
      current = step.migrate(current).put("schemaVersion", ThemeJsonNode.Num((version + 1).toDouble()))
      version += 1
    }
    return ThemeSchemaMigrationResult(current)
  }

  private fun rejected(version: Int, reason: String): ThemeSchemaMigrationResult =
      ThemeSchemaMigrationResult(null, listOf(ThemeValidationIssue.UnsupportedSchemaVersion(version, reason)))
}

object ThemeSchemaMigration {
  /** Production chain: no registered steps yet. */
  val DEFAULT: ThemeSchemaMigrationChain = ThemeSchemaMigrationChain()

  fun migrate(
      document: ThemeJsonNode.Obj,
      fromVersion: Int,
  ): ThemeSchemaMigrationResult = DEFAULT.migrate(document, fromVersion)

  /** Builds a chain with explicit steps; used by tests and by future schema revisions. */
  fun chain(
      steps: Map<Int, ThemeSchemaStep>,
      targetVersion: Int = ThemeSchema.CURRENT_SCHEMA_VERSION,
  ): ThemeSchemaMigrationChain = ThemeSchemaMigrationChain(steps, targetVersion)
}
