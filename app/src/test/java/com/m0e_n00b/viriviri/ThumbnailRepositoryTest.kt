package com.m0e_n00b.viriviri

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ThumbnailRepositoryTest {
  @Test
  fun loadingStateIsOnlyMarkedOnce() {
    val repository = ThumbnailRepository(maxEntries = 2, downloader = { null })

    assertTrue(repository.markLoading("https://example.com/a.jpg"))
    assertFalse(repository.markLoading("https://example.com/a.jpg"))
    assertEquals(ThumbnailState.Loading, repository.state("https://example.com/a.jpg"))
  }

  @Test
  fun decodeSamplingBoundsLargeCoverMemory() {
    assertEquals(1, thumbnailDecodeSampleSize(480, 270))
    assertEquals(2, thumbnailDecodeSampleSize(960, 540))
    assertEquals(4, thumbnailDecodeSampleSize(1920, 1080))
  }

  @Test
  fun cacheEvictsLeastRecentlyUsedEntryAtBound() {
    val repository = ThumbnailRepository(maxEntries = 2, downloader = { null })

    repository.markLoading("https://example.com/a.jpg")
    repository.markLoading("https://example.com/b.jpg")
    repository.state("https://example.com/a.jpg")
    repository.markLoading("https://example.com/c.jpg")

    assertEquals(ThumbnailState.Loading, repository.state("https://example.com/a.jpg"))
    assertNull(repository.state("https://example.com/b.jpg"))
    assertEquals(ThumbnailState.Loading, repository.state("https://example.com/c.jpg"))
  }
}
