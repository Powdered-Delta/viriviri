package com.m0e_n00b.viriviri

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class VideoContextTest {
  @Test fun collectionUsesEpisodeMetadataWithoutBorrowingAnotherVideosCover() {
    val details = parseVideoContext(JSONObject("""{"bvid":"BVcurrent","pic":"https://current.jpg",
      "owner":{"name":"Season owner"},"ugc_season":{"sections":[{"episodes":[
      {"bvid":"BVother","arc":{"author":{"name":"Episode author"},"pic":"//episode.jpg",
        "stat":{"view":1234,"danmaku":0,"like":20},"duration":90}},
      {"bvid":"BVmissing","arc":{}},
      {"bvid":"BVcurrent","arc":{}}]}]}}"""))
    val videos = details.sections.single().videos
    assertEquals("Episode author", videos[0].authorName)
    assertEquals("//episode.jpg", videos[0].coverUrl)
    assertEquals(1234L, videos[0].viewCount)
    assertEquals(0L, videos[0].danmakuCount)
    assertEquals(20L, videos[0].likeCount)
    assertNull(videos[1].coverUrl)
    assertNull(videos[1].viewCount)
    assertNull(videos[1].danmakuCount)
    assertEquals("https://current.jpg", videos[2].coverUrl)
  }

  @Test fun partsPreserveCidOrderAndSkipInvalidOrDuplicatePages() {
    val details = parseVideoContext(JSONObject("""{"pages":[
      {"cid":101,"page":1,"part":"First","duration":60},
      {"cid":202,"page":2,"part":"Second","duration":90},
      {"cid":202,"page":2,"part":"Duplicate"},{"cid":0,"page":3}]}"""))
    assertEquals(listOf(101L, 202L), details.parts.map { it.contentId })
    assertEquals(VideoPart(202, 2, "Second", 90), details.parts[1])
  }

  @Test fun collectionPreservesSectionsAndExplicitEpisodeCid() {
    val details = parseVideoContext(JSONObject("""{"owner":{"name":"Creator"},"ugc_season":{
      "title":"Series","sections":[{"title":"Chapter 1","episodes":[
        {"bvid":"BVone","cid":301,"title":"Episode 1","arc":{"pic":"https://example.com/a.png","duration":70}},
        {"bvid":"BVtwo","cid":401,"title":"Episode 2","arc":{"duration":80}},
        {"title":"Missing BV"}]},{"title":"Chapter 2","episodes":[
        {"bvid":"BVthree","cid":501,"title":"Episode 3"}]}]}}"""))
    assertEquals("Series", details.collectionTitle)
    assertEquals(listOf("Chapter 1", "Chapter 2"), details.sections.map { it.title })
    assertEquals(listOf("BVone", "BVtwo"), details.sections[0].videos.map { it.videoId })
    assertEquals(401L, details.sections[0].videos[1].contentId)
    assertEquals("Creator", details.sections[0].videos[0].authorName)
  }

  @Test fun selectingSecondPartChangesMediaIdentityWithoutLosingVideoMetadata() {
    val original = Recommendation("BVsample", "Video", "Creator", null, 200, 25, null, "https://example.com")
    val second = VideoPart(202, 2, "Second", 90)
    val selected = original.withPart(second)
    assertEquals("BVsample", selected.videoId)
    assertEquals("Creator", selected.authorName)
    assertEquals(202L, selected.contentId)
    assertEquals(2, selected.partNumber)
    assertEquals("Second", selected.partTitle)
    assertTrue(selected.videoUrl.endsWith("?p=2"))
    assertTrue(selected.matchesPart(second))
    assertFalse(selected.matchesPart(VideoPart(101, 1, "First", 60)))
    // Quality changes/retries consume this immutable selection, including its cid.
    assertEquals(202L, selected.copy().contentId)
  }

  @Test fun initialSelectionHighlightsFirstPartButNotAllPartsWithSameBv() {
    val video = Recommendation("BVsample", "Video", "Creator", null, null, null, null, "")
    assertTrue(video.matchesPart(VideoPart(101, 1, "First", 60)))
    assertFalse(video.matchesPart(VideoPart(202, 2, "Second", 90)))
  }

  @Test fun singleVideoWithoutCollectionHasValidEmptyContext() {
    val details = parseVideoContext(JSONObject("""{"pages":[{"cid":101,"page":1,"part":"Only"}]}"""))
    assertEquals(1, details.parts.size)
    assertTrue(details.sections.isEmpty())
    assertNull(details.collectionTitle)
    assertNull(details.parts.single().durationSeconds)
  }
}
