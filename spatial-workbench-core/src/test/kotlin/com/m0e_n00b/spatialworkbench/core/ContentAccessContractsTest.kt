package com.m0e_n00b.spatialworkbench.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ContentAccessContractsTest {
  @Test
  fun onlyChargingExclusiveContentProducesTheCompactBadgeText() {
    assertEquals("充电", ContentAccess.CHARGING_EXCLUSIVE.badgeText())
    assertNull(ContentAccess.STANDARD.badgeText())
  }
}
