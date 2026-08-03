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
  fun sign_sortsParametersAndStripsWbiDisallowedCharacters() {
    val signed = BilibiliWbi.sign(mapOf("z" to "x!y", "a" to "two words"), "key", 100L)

    assertEquals("a=two+words&wts=100&z=xy&w_rid=a64e67fc3a3a8441b3d6d1118c933f37", signed)
  }
}
