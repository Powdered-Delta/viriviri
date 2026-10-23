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
    repository.store("https://example.com/a.jpg", null)
    repository.markLoading("https://example.com/b.jpg")
    repository.store("https://example.com/b.jpg", null)
    repository.state("https://example.com/a.jpg")
    repository.markLoading("https://example.com/c.jpg")
    repository.store("https://example.com/c.jpg", null)

    assertEquals(ThumbnailState.Failed, repository.state("https://example.com/a.jpg"))
    assertNull(repository.state("https://example.com/b.jpg"))
    assertEquals(ThumbnailState.Failed, repository.state("https://example.com/c.jpg"))
  }

  @Test
  fun inflightRequestIsNotLostWhenCompletedCacheOverflows() {
    val repository = ThumbnailRepository(maxEntries = 1, downloader = { null })
    assertTrue(repository.markLoading("pending"))
    repository.store("completed-a", null)
    repository.store("completed-b", null)
    assertEquals(ThumbnailState.Loading, repository.state("pending"))
    assertFalse(repository.markLoading("pending", retryFailed = true))
    repository.store("pending", null)
    assertEquals(ThumbnailState.Failed, repository.state("pending"))
  }

  @Test
  fun failedImageRetriesOnlyOnExplicitRequestAndStillDeduplicates() {
    val repository = ThumbnailRepository(downloader = { null })
    repository.markLoading("cover")
    repository.store("cover", null)
    assertFalse(repository.markLoading("cover"))
    assertTrue(repository.markLoading("cover", retryFailed = true))
    assertEquals(ThumbnailState.Loading, repository.state("cover"))
    assertFalse(repository.markLoading("cover", retryFailed = true))
  }

  @Test
  fun evictedImageCanBeRequestedAgainOnScrollback() {
    val repository = ThumbnailRepository(maxEntries = 1, downloader = { null })
    repository.store("old-cover", null)
    repository.store("new-cover", null)
    assertNull(repository.state("old-cover"))
    assertTrue(repository.markLoading("old-cover"))
  }

  @Test
  fun downloaderExceptionsBecomeRetryableFailures() {
    val repository = ThumbnailRepository(downloader = { error("Connection failed") })
    repository.markLoading("cover")
    repository.store("cover", repository.download("cover"))
    assertEquals(ThumbnailState.Failed, repository.state("cover"))
    assertTrue(repository.markLoading("cover", retryFailed = true))
  }
}
