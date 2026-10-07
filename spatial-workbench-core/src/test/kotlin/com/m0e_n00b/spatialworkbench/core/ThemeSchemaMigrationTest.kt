package com.m0e_n00b.spatialworkbench.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** R2: schema versioning and the pure `vN -> vN+1` migration chain (A5/R6). */
class ThemeSchemaMigrationTest {

  @Test
  fun schemaVersionOneIsCurrentAndSupported() {
    assertEquals(1, ThemeSchema.CURRENT_SCHEMA_VERSION)
    assertTrue(ThemeSchema.isSupported(1))
    assertFalse(ThemeSchema.isSupported(2))
    assertFalse(ThemeSchema.isSupported(0))
  }

  @Test
  fun unknownAndFutureVersionsAreRejectedInsteadOfSilentlyDowngraded() {
    val document = document("""{ "schemaVersion": 1 }""")

    val future = ThemeSchemaMigration.migrate(document, 2)
    assertNull(future.document)
    assertTrue(future.issues.any { it is ThemeValidationIssue.UnsupportedSchemaVersion && it.version == 2 })

    val unknown = ThemeSchemaMigration.migrate(document, 0)
    assertNull(unknown.document)
    assertTrue(unknown.issues.any { it is ThemeValidationIssue.UnsupportedSchemaVersion && it.version == 0 })
  }

  @Test
  fun missingMigrationStepIsReportedRatherThanSkipped() {
    val chain = ThemeSchemaMigration.chain(emptyMap(), targetVersion = 2)

    val result = chain.migrate(document("""{ "schemaVersion": 1 }"""), 1)

    assertNull(result.document)
    assertTrue(
        result.issues.any {
          it is ThemeValidationIssue.UnsupportedSchemaVersion && it.version == 1 && it.reason == "missing-migration-step"
        }
    )
  }

  @Test
  fun chainAppliesEachStepAndStampsTheReachedVersion() {
    // A realistic step: add a default field only when it is absent, which also makes it idempotent.
    val addDefaults =
        ThemeSchemaStep { current ->
          if (current.get("panels") == null) {
            current.put("panels", ThemeJsonNode.Obj(emptyMap()))
          } else {
            current
          }
        }
    val chain = ThemeSchemaMigration.chain(mapOf(1 to addDefaults), targetVersion = 2)

    val migrated = chain.migrate(document("""{ "schemaVersion": 1 }"""), 1)
    val result = migrated.document
    assertTrue(migrated.issues.isEmpty())
    requireNotNull(result)
    assertEquals(2.0, (result.get("schemaVersion") as ThemeJsonNode.Num).value, 0.0)
    assertTrue(result.get("panels") is ThemeJsonNode.Obj)

    // Idempotent: migrating the already-migrated document again changes nothing.
    val again = addDefaults.migrate(result)
    assertEquals(result, again)
  }

  @Test
  fun themeValidatorRejectsAnUnsupportedDeclaredVersion() {
    val issues = ThemeValidator.validate(ThemeTestKit.cinema().copy(schemaVersion = 99))

    assertTrue(issues.any { it is ThemeValidationIssue.UnsupportedSchemaVersion && it.version == 99 })
  }

  @Test
  fun migratedDocumentIsStillRejectedByTheParserWhenItsVersionIsNotSupported() {
    val step = ThemeSchemaStep { current -> current.put("displayName", ThemeJsonNode.Str("Migrated")) }
    val chain = ThemeSchemaMigration.chain(mapOf(1 to step), targetVersion = 2)

    val parsed = ThemeJsonParser.parse("""{ "schemaVersion": 1, "id": "cinema" }""", chain)

    assertNull(parsed.theme)
    assertTrue(parsed.issues.any { it is ThemeValidationIssue.UnsupportedSchemaVersion && it.version == 2 })
  }

  private fun document(json: String): ThemeJsonNode.Obj {
    val read = ThemeJsonReader.read(json)
    assertTrue(read is ThemeJsonReadResult.Parsed)
    return (read as ThemeJsonReadResult.Parsed).node as ThemeJsonNode.Obj
  }
}
