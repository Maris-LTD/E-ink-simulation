package com.example.emptyapp.reader.pdf

data class GuidedRegionStep(
  val consumed: Boolean,
  val nextRegionIndex: Int,
)

object PdfGuidedNavigation {
  fun stepPrev(
    readingMode: PdfReadingMode,
    hasAnalysis: Boolean,
    currentRegionIndex: Int,
    awaitingBackwardLanding: Boolean = false,
  ): GuidedRegionStep {
    if (readingMode != PdfReadingMode.GUIDED_COLUMN) {
      return GuidedRegionStep(consumed = false, nextRegionIndex = currentRegionIndex)
    }
    if (!hasAnalysis || awaitingBackwardLanding) {
      return GuidedRegionStep(consumed = true, nextRegionIndex = currentRegionIndex)
    }
    return if (currentRegionIndex > 0) {
      GuidedRegionStep(consumed = true, nextRegionIndex = currentRegionIndex - 1)
    } else {
      GuidedRegionStep(consumed = false, nextRegionIndex = currentRegionIndex)
    }
  }

  fun stepNext(
    readingMode: PdfReadingMode,
    hasAnalysis: Boolean,
    currentRegionIndex: Int,
    regionCount: Int,
    awaitingBackwardLanding: Boolean = false,
  ): GuidedRegionStep {
    if (readingMode != PdfReadingMode.GUIDED_COLUMN) {
      return GuidedRegionStep(consumed = false, nextRegionIndex = currentRegionIndex)
    }
    if (!hasAnalysis || awaitingBackwardLanding) {
      return GuidedRegionStep(consumed = true, nextRegionIndex = currentRegionIndex)
    }
    val lastIndex = (regionCount - 1).coerceAtLeast(0)
    return if (currentRegionIndex < lastIndex) {
      GuidedRegionStep(consumed = true, nextRegionIndex = currentRegionIndex + 1)
    } else {
      GuidedRegionStep(consumed = false, nextRegionIndex = currentRegionIndex)
    }
  }

  fun resolveRegionIndex(
    readingMode: PdfReadingMode,
    pageAnalysis: PdfPageAnalysis?,
    guidedRegions: List<PdfRect>,
    currentRegionIndex: Int,
    pendingBackwardLandingPage: Int?,
    pageIndex: Int,
    onConsumeBackwardLanding: () -> Unit,
  ): Int {
    if (readingMode != PdfReadingMode.GUIDED_COLUMN || pageAnalysis == null || guidedRegions.isEmpty()) {
      return currentRegionIndex
    }
    if (pendingBackwardLandingPage == pageIndex) {
      onConsumeBackwardLanding()
      return guidedRegions.lastIndex
    }
    if (currentRegionIndex > guidedRegions.lastIndex) {
      return 0
    }
    return currentRegionIndex
  }
}
