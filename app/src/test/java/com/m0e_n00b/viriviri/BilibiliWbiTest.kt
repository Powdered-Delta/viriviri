package com.m0e_n00b.viriviri

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BilibiliWbiTest {
  @Test
  fun recommendationEndpoint_usesTheVerifiedWebFeedContract() {
    assertEquals(
        "/x/web-interface/wbi/index/top/feed/rcmd",
        BilibiliPlaybackProvider.recommendationEndpointPath(),
    )
  }

  @Test
  fun recommendationPaginationAdvancesFreshIndexAndBrushTogether() {
    assertEquals(
        "https://api.bilibili.com/x/web-interface/wbi/index/top/feed/rcmd" +
            "?version=1&feed_version=V8&homepage_ver=1&ps=20&fresh_idx=40&brush=40&fresh_type=4",
        BilibiliPlaybackProvider.recommendationPageUrl("https://api.bilibili.com", freshIndex = 40, pageSize = 20),
    )
  }

  @Test
  fun anonymousNavResponseCanStillSupplyWbiKeys() {
    // The anonymous nav response carries these fields even when its code is -101.
    val imageUrl = "https://i0.hdslb.com/bfs/wbi/abcdefghijklmnopqrstuvwxyz012345.jpg"
    val subUrl = "https://i0.hdslb.com/bfs/wbi/ABCDEFGHIJKLMNOPQRSTUVWXYZ678901.png"
    assertEquals(
        "OPscVixApSk56dND1LfRBjKt32oHmGJn",
        BilibiliWbi.mixinKey(imageUrl, subUrl),
    )
  }

  @Test
  fun mixinKey_reordersTheTwoPublicWbiFilenames() {
    val image = "https://i0.hdslb.com/bfs/wbi/abcdefghijklmnopqrstuvwxyz012345.jpg"
    val sub = "https://i0.hdslb.com/bfs/wbi/ABCDEFGHIJKLMNOPQRSTUVWXYZ678901.png"

    assertEquals("OPscVixApSk56dND1LfRBjKt32oHmGJn", BilibiliWbi.mixinKey(image, sub))
  }

  @Test
  fun mixinKey_rejectsIncompleteSigningData() {
    assertNull(BilibiliWbi.mixinKey("https://example.com/short.jpg", "https://example.com/key.png"))
  }

  @Test
  fun sign_sortsParametersStripsWbiDisallowedCharactersAndUsesRfc3986Spaces() {
    val signed = BilibiliWbi.sign(mapOf("z" to "x!y", "a" to "two words"), "key", 100L)

    assertEquals("a=two%20words&wts=100&z=xy&w_rid=3bb4e8b9f7a900cfbd2f732d34c00f2f", signed)
  }

  @Test
  fun normalizeSearchQuery_preservesOneSpaceBetweenSearchTerms() {
    assertEquals("virtual reality video", normalizeSearchQuery("  virtual   reality\tvideo  "))
  }
}
