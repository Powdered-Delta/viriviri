package com.m0e_n00b.spatialworkbench.core

/** Public content-access states that can be rendered consistently across hosts. */
enum class ContentAccess {
  STANDARD,
  CHARGING_EXCLUSIVE,
}

/** Returns the compact marker text for a content-access state, or null when no marker is needed. */
fun ContentAccess.badgeText(): String? =
    when (this) {
      ContentAccess.STANDARD -> null
      ContentAccess.CHARGING_EXCLUSIVE -> "充电"
    }
