package com.m0e_n00b.spatialworkbench.core

import kotlin.math.pow

/** Semantic color roles shared by the cinema theme and its component groups. */
enum class CinemaColorRole {
  BACKGROUND,
  SURFACE,
  NORMAL_TEXT,
  SECONDARY_TEXT,
  HIGHLIGHT_TEXT,
  PRIMARY_BUTTON,
  PRIMARY_BUTTON_LABEL,
  SECONDARY_BUTTON,
  SECONDARY_BUTTON_LABEL,
  BORDER,
  DANGER,
}

data class RgbColor(val red: Int, val green: Int, val blue: Int) {
  fun isValid(): Boolean = red in 0..255 && green in 0..255 && blue in 0..255

  fun luminance(): Double {
    fun channel(value: Int): Double {
      val normalized = value / 255.0
      return if (normalized <= 0.03928) normalized / 12.92 else ((normalized + 0.055) / 1.055).pow(2.4)
    }
    return 0.2126 * channel(red) + 0.7152 * channel(green) + 0.0722 * channel(blue)
  }

  fun contrastAgainst(other: RgbColor): Double {
    val lighter = maxOf(luminance(), other.luminance())
    val darker = minOf(luminance(), other.luminance())
    return (lighter + 0.05) / (darker + 0.05)
  }

  fun derive(factor: Double): RgbColor =
      RgbColor(
          (red * factor).coerceIn(0.0, 255.0).toInt(),
          (green * factor).coerceIn(0.0, 255.0).toInt(),
          (blue * factor).coerceIn(0.0, 255.0).toInt(),
      )

}

data class CinemaPalette(
    val background: RgbColor,
    val surface: RgbColor,
    val normalText: RgbColor,
    val secondaryText: RgbColor,
    val highlightText: RgbColor,
    val primaryButton: RgbColor,
    val primaryButtonLabel: RgbColor,
    val secondaryButton: RgbColor,
    val secondaryButtonLabel: RgbColor,
    val border: RgbColor,
    val danger: RgbColor,
    val surfaceOpacity: Float,
) {
  fun color(role: CinemaColorRole): RgbColor =
      when (role) {
        CinemaColorRole.BACKGROUND -> background
        CinemaColorRole.SURFACE -> surface
        CinemaColorRole.NORMAL_TEXT -> normalText
        CinemaColorRole.SECONDARY_TEXT -> secondaryText
        CinemaColorRole.HIGHLIGHT_TEXT -> highlightText
        CinemaColorRole.PRIMARY_BUTTON -> primaryButton
        CinemaColorRole.PRIMARY_BUTTON_LABEL -> primaryButtonLabel
        CinemaColorRole.SECONDARY_BUTTON -> secondaryButton
        CinemaColorRole.SECONDARY_BUTTON_LABEL -> secondaryButtonLabel
        CinemaColorRole.BORDER -> border
        CinemaColorRole.DANGER -> danger
      }

  fun interactionColors(role: CinemaColorRole): InteractionColors {
    val base = color(role)
    return InteractionColors(
        normal = base,
        hover = base.derive(1.12),
        pressed = base.derive(0.86),
        focus = highlightText,
        disabled = secondaryText.derive(0.72),
    )
  }

  companion object {
    val DARK = CinemaPalette(
        background = RgbColor(10, 12, 16),
        surface = RgbColor(28, 32, 40),
        normalText = RgbColor(245, 247, 250),
        secondaryText = RgbColor(181, 188, 200),
        highlightText = RgbColor(255, 214, 102),
        primaryButton = RgbColor(55, 112, 214),
        primaryButtonLabel = RgbColor(255, 255, 255),
        secondaryButton = RgbColor(58, 66, 78),
        secondaryButtonLabel = RgbColor(245, 247, 250),
        border = RgbColor(106, 116, 132),
        danger = RgbColor(214, 62, 72),
        surfaceOpacity = 0.94f,
    )

    val LIGHT = CinemaPalette(
        background = RgbColor(242, 244, 247),
        surface = RgbColor(255, 255, 255),
        normalText = RgbColor(24, 29, 38),
        secondaryText = RgbColor(82, 91, 105),
        highlightText = RgbColor(19, 86, 168),
        primaryButton = RgbColor(22, 91, 180),
        primaryButtonLabel = RgbColor(255, 255, 255),
        secondaryButton = RgbColor(221, 226, 234),
        secondaryButtonLabel = RgbColor(28, 34, 43),
        border = RgbColor(116, 126, 142),
        danger = RgbColor(178, 34, 45),
        surfaceOpacity = 0.96f,
    )

    val HIGH_CONTRAST = CinemaPalette(
        background = RgbColor(0, 0, 0),
        surface = RgbColor(20, 20, 20),
        normalText = RgbColor(255, 255, 255),
        secondaryText = RgbColor(230, 230, 230),
        highlightText = RgbColor(255, 255, 0),
        primaryButton = RgbColor(0, 90, 200),
        primaryButtonLabel = RgbColor(255, 255, 255),
        secondaryButton = RgbColor(0, 0, 0),
        secondaryButtonLabel = RgbColor(255, 255, 255),
        border = RgbColor(255, 255, 255),
        danger = RgbColor(255, 80, 80),
        surfaceOpacity = 1f,
    )
  }
}

data class InteractionColors(
    val normal: RgbColor,
    val hover: RgbColor,
    val pressed: RgbColor,
    val focus: RgbColor,
    val disabled: RgbColor,
)

sealed interface PlaybackBrowseOrigin {
  data object Recommendations : PlaybackBrowseOrigin

  data class SearchResults(
      val cacheKey: String,
      val query: String,
      val filters: Map<String, String>,
      val snapshotId: String,
      val pageCursor: String?,
      val scrollPosition: Float,
  ) : PlaybackBrowseOrigin
}

sealed interface BrowseContinuation {
  data class RestoreSearchResults(val origin: PlaybackBrowseOrigin.SearchResults) : BrowseContinuation

  data object FallbackToRecommendations : BrowseContinuation
}

object BrowseOriginResolver {
  fun continuation(
      origin: PlaybackBrowseOrigin,
      hasCachedSnapshot: (String) -> Boolean,
  ): BrowseContinuation =
      when (origin) {
        PlaybackBrowseOrigin.Recommendations -> BrowseContinuation.FallbackToRecommendations
        is PlaybackBrowseOrigin.SearchResults ->
            if (origin.cacheKey.isNotBlank() && hasCachedSnapshot(origin.cacheKey)) {
              BrowseContinuation.RestoreSearchResults(origin)
            } else {
              BrowseContinuation.FallbackToRecommendations
            }
      }
}

data class CompositionRange(val start: Int, val endExclusive: Int) {
  fun isValidFor(composition: String): Boolean = start >= 0 && start < endExclusive && endExclusive <= composition.length
}

data class InputCandidate(val text: String, val consumedRange: CompositionRange)

data class CandidateConsumption(
    val committedText: String,
    val remainingComposition: String,
)

data class SearchCompositionState(
    val committedQuery: String,
    val composition: String,
    val candidates: List<InputCandidate> = emptyList(),
)

sealed interface InputValidationIssue {
  data class InvalidCandidateRange(val candidate: InputCandidate, val composition: String) : InputValidationIssue
}

object InputContractValidator {
  fun validate(composition: String, candidate: InputCandidate): List<InputValidationIssue> =
      if (candidate.text.isEmpty() || !candidate.consumedRange.isValidFor(composition)) {
        listOf(InputValidationIssue.InvalidCandidateRange(candidate, composition))
      } else {
        emptyList()
      }
}

sealed interface BrowseOriginValidationIssue {
  data class InvalidSearchResults(val field: String) : BrowseOriginValidationIssue
}

object BrowseOriginValidator {
  fun validate(origin: PlaybackBrowseOrigin): List<BrowseOriginValidationIssue> =
      when (origin) {
        PlaybackBrowseOrigin.Recommendations -> emptyList()
        is PlaybackBrowseOrigin.SearchResults -> buildList {
          if (origin.cacheKey.isBlank()) add(BrowseOriginValidationIssue.InvalidSearchResults("cacheKey"))
          if (origin.query.isBlank()) add(BrowseOriginValidationIssue.InvalidSearchResults("query"))
          if (origin.snapshotId.isBlank()) add(BrowseOriginValidationIssue.InvalidSearchResults("snapshotId"))
          if (!origin.scrollPosition.isFinite() || origin.scrollPosition < 0f) {
            add(BrowseOriginValidationIssue.InvalidSearchResults("scrollPosition"))
          }
        }
      }
}

object SearchComposition {
  fun consume(composition: String, candidate: InputCandidate): CandidateConsumption? {
    if (candidate.text.isEmpty() || !candidate.consumedRange.isValidFor(composition)) return null
    val range = candidate.consumedRange
    val remaining = composition.removeRange(range.start until range.endExclusive).normalizeDelimiters()
    return CandidateConsumption(candidate.text, remaining)
  }

  fun mergeExternalFinalText(
      committedQuery: String,
      unconfirmedComposition: String,
      finalText: String,
  ): String = committedQuery + unconfirmedComposition + finalText

  fun acceptExternalFinalText(
      state: SearchCompositionState,
      finalText: String,
  ): SearchCompositionState =
      state.copy(
          committedQuery = mergeExternalFinalText(state.committedQuery, state.composition, finalText),
          composition = "",
          candidates = emptyList(),
      )

  private fun String.normalizeDelimiters(): String = replace(Regex("'+"), "'").trim('\'')
}
