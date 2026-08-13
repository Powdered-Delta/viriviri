package com.m0e_n00b.viriviri

import com.m0e_n00b.spatialworkbench.core.TransientMessage
import com.m0e_n00b.spatialworkbench.core.TransientMessageSeverity
import com.m0e_n00b.spatialworkbench.core.TransientMessageState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class TransientMessageAppStateTest {
  @Test
  fun errorHelperEnqueuesVisibleErrorAndPreservesExistingMessage() {
    val existing = TransientMessage("existing", "Existing")

    val result = enqueueErrorMessage(TransientMessageState(current = existing), id = 7L, text = "Unable to load")

    assertSame(existing, result.current)
    assertEquals(1, result.pending.size)
    assertEquals("error-7", result.pending.single().id)
    assertEquals("Unable to load", result.pending.single().text)
    assertEquals(TransientMessageSeverity.ERROR, result.pending.single().severity)
  }
}
