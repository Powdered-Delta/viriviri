package com.m0e_n00b.viriviri

internal const val RECOMMENDATION_PAGE_SIZE = 20

internal data class RecommendationPageMerge(
    val recommendations: List<Recommendation>,
    val addedCount: Int,
    val canLoadMore: Boolean,
)

internal fun mergeRecommendationPage(
    existing: List<Recommendation>,
    incoming: List<Recommendation>,
    pageSize: Int = RECOMMENDATION_PAGE_SIZE,
): RecommendationPageMerge {
  val seenVideoIds = existing.mapTo(mutableSetOf()) { it.videoId }
  val appended = incoming.filter { seenVideoIds.add(it.videoId) }
  return RecommendationPageMerge(
      recommendations = existing + appended,
      addedCount = appended.size,
      canLoadMore = incoming.size >= pageSize && appended.isNotEmpty(),
  )
}
