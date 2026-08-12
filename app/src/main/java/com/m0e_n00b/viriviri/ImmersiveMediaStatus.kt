package com.m0e_n00b.viriviri

internal data class ImmersiveMediaStatus(
    val title: String,
    val detail: String,
)

internal fun immersiveMediaStatus(
    selected: Recommendation?,
    error: String?,
    maxTitleLength: Int = 42,
    maxDetailLength: Int = 56,
): ImmersiveMediaStatus {
  val title = selected?.title?.trim().orEmpty().ifBlank { "No video selected" }.truncateForPanel(maxTitleLength)
  val detail = (error?.trim()?.takeIf { it.isNotBlank() } ?: selected?.authorName?.trim().orEmpty().ifBlank { "Browse to choose a video" })
      .truncateForPanel(maxDetailLength)
  return ImmersiveMediaStatus(title = title, detail = detail)
}

private fun String.truncateForPanel(maxLength: Int): String {
  if (maxLength <= 0) return ""
  if (length <= maxLength) return this
  if (maxLength <= 3) return take(maxLength)
  return take(maxLength - 3) + "..."
}
