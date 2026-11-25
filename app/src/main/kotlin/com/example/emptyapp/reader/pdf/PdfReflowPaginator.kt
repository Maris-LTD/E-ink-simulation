package com.example.emptyapp.reader.pdf

import kotlin.math.abs

data class ReflowReadingAnchor(
  val sourcePageIndex: Int,
  val itemIndex: Int = 0,
  val charOffset: Int = 0,
)

data class ReflowElementRange(
  val sourcePageIndex: Int,
  val itemIndex: Int,
  val charRange: IntRange,
)

data class ReflowSettleEffect(
  val snapPdfPage: Int,
  val updatedAnchor: ReflowReadingAnchor?,
  val isSubPageTurn: Boolean,
)

enum class ReflowImageTapAction {
  PREV_PAGE,
  NEXT_PAGE,
  OPEN_ZOOM,
}

fun resolveReflowImageTapAction(
  tapX: Float,
  containerWidth: Float,
): ReflowImageTapAction {
  val width = containerWidth.coerceAtLeast(1f)
  return when {
    tapX < width * 0.25f -> ReflowImageTapAction.PREV_PAGE
    tapX > width * 0.75f -> ReflowImageTapAction.NEXT_PAGE
    else -> ReflowImageTapAction.OPEN_ZOOM
  }
}

data class ReflowPageMeta(
  val pageIndex: Int,
  val hasSpecialObjects: Boolean,
  val result: ReflowPageResult,
  val pageAspectRatio: Float = 1f,
)

sealed interface PaginatedReflowElement {
  val sourcePage: Int
  val itemIndex: Int
  val charOffset: Int
  val charEndOffset: Int

  data class ParagraphSlice(
    val text: String,
    val isHeading: Boolean,
    override val sourcePage: Int,
    override val itemIndex: Int = 0,
    override val charOffset: Int = 0,
    override val charEndOffset: Int = charOffset + text.length,
  ) : PaginatedReflowElement

  data class InlineImage(
    val bounds: PdfRect,
    override val sourcePage: Int,
    val displayHeightPx: Int,
    val displayWidthPx: Int = 0,
    val kind: ReflowImageKind = ReflowImageKind.ILLUSTRATION,
    override val itemIndex: Int = 0,
    override val charOffset: Int = 0,
    override val charEndOffset: Int = 0,
  ) : PaginatedReflowElement

  data class FallbackPage(
    override val sourcePage: Int,
    val reason: ReflowFallbackReason,
    override val itemIndex: Int = 0,
    override val charOffset: Int = 0,
    override val charEndOffset: Int = 0,
  ) : PaginatedReflowElement
}

fun PaginatedReflowElement.toReadingAnchor(): ReflowReadingAnchor =
  ReflowReadingAnchor(
    sourcePageIndex = sourcePage,
    itemIndex = itemIndex,
    charOffset = charOffset,
  )

fun PaginatedReflowElement.toElementRange(): ReflowElementRange {
  val endInclusive =
    when (this) {
      is PaginatedReflowElement.ParagraphSlice ->
        if (charEndOffset > charOffset) charEndOffset - 1
        else charOffset + (text.length - 1).coerceAtLeast(0)
      is PaginatedReflowElement.InlineImage,
      is PaginatedReflowElement.FallbackPage -> charOffset
    }
  return ReflowElementRange(
    sourcePageIndex = sourcePage,
    itemIndex = itemIndex,
    charRange = charOffset..endInclusive,
  )
}

data class ReflowScreenPage(
  val screenIndex: Int,
  val elements: List<PaginatedReflowElement>,
  val sourcePages: List<Int>,
  val specialObjectPages: Set<Int>,
  val startAnchor: ReflowReadingAnchor =
    elements.firstOrNull()?.toReadingAnchor()
      ?: ReflowReadingAnchor(sourcePageIndex = sourcePages.firstOrNull() ?: 0),
  val elementRanges: List<ReflowElementRange> = elements.map { it.toElementRange() },
)

object PdfReflowPaginator {

  fun hasSpecialObjects(analysis: PdfPageAnalysis, result: ReflowPageResult): Boolean {
    if (result is ReflowPageResult.FallbackOriginal) return true
    if (result is ReflowPageResult.Reflowed && result.items.any { it is ReflowItem.Image }) return true
    val hasInlineImage = analysis.imageBlocks.any { it.pageAreaFraction in 0.01f..0.85f }
    val hasTablesOrVectors = analysis.vectorOpCount > 40
    return hasInlineImage || hasTablesOrVectors
  }

  fun resolveSnapBackPage(screenPage: ReflowScreenPage): Int {
    val sources = screenPage.sourcePages
    if (sources.isEmpty()) return 0
    if (sources.size == 1) return sources[0]
    val specialInScreen = sources.filter { it in screenPage.specialObjectPages }
    return if (specialInScreen.size == 1) specialInScreen[0] else sources.first()
  }

  fun findScreenIndexForPdfPage(screens: List<ReflowScreenPage>, pdfPageIndex: Int): Int {
    if (screens.isEmpty()) return 0
    val startsWith = screens.indexOfFirst { it.sourcePages.firstOrNull() == pdfPageIndex }
    if (startsWith >= 0) return startsWith
    val exact = screens.indexOfFirst { pdfPageIndex in it.sourcePages }
    if (exact >= 0) return exact
    val after = screens.indexOfFirst { it.sourcePages.any { p -> p >= pdfPageIndex } }
    return if (after >= 0) after else screens.lastIndex
  }

  fun findScreenIndexForAnchor(screens: List<ReflowScreenPage>, anchor: ReflowReadingAnchor): Int {
    if (screens.isEmpty()) return 0

    val exactRange =
      screens.indexOfFirst { screen ->
        screen.elementRanges.any { range ->
          range.sourcePageIndex == anchor.sourcePageIndex &&
            range.itemIndex == anchor.itemIndex &&
            anchor.charOffset in range.charRange
        }
      }
    if (exactRange >= 0) return exactRange

    var bestBehindScreen = -1
    var bestBehindOffset = Int.MIN_VALUE
    var earliestScreen = -1
    var earliestOffset = Int.MAX_VALUE
    for ((screenIdx, screen) in screens.withIndex()) {
      for (range in screen.elementRanges) {
        if (range.sourcePageIndex == anchor.sourcePageIndex && range.itemIndex == anchor.itemIndex) {
          val start = range.charRange.first
          if (start <= anchor.charOffset && start > bestBehindOffset) {
            bestBehindOffset = start
            bestBehindScreen = screenIdx
          }
          if (start < earliestOffset) {
            earliestOffset = start
            earliestScreen = screenIdx
          }
        }
      }
    }
    if (bestBehindScreen >= 0) return bestBehindScreen
    if (earliestScreen >= 0) return earliestScreen

    val startsWith = screens.indexOfFirst { it.sourcePages.firstOrNull() == anchor.sourcePageIndex }
    if (startsWith >= 0) return startsWith
    val contains = screens.indexOfFirst { anchor.sourcePageIndex in it.sourcePages }
    if (contains >= 0) return contains

    var bestScreen = -1
    var bestDistance = Int.MAX_VALUE
    var bestIsAtOrAfter = false
    for ((screenIdx, screen) in screens.withIndex()) {
      if (screen.sourcePages.isEmpty()) continue
      val distance = screen.sourcePages.minOf { abs(it - anchor.sourcePageIndex) }
      val isAtOrAfter = screen.sourcePages.any { it >= anchor.sourcePageIndex }
      if (distance < bestDistance || (distance == bestDistance && isAtOrAfter && !bestIsAtOrAfter)) {
        bestDistance = distance
        bestScreen = screenIdx
        bestIsAtOrAfter = isAtOrAfter
      }
    }
    return if (bestScreen >= 0) bestScreen else 0
  }

  fun evaluateScreenSettle(
    screen: ReflowScreenPage,
    previousScreenIndex: Int?,
    previousPdfPage: Int,
    isExplicitJump: Boolean,
  ): ReflowSettleEffect {
    val snapPage = resolveSnapBackPage(screen)
    val screenChanged = previousScreenIndex != null && previousScreenIndex != screen.screenIndex
    return ReflowSettleEffect(
      snapPdfPage = snapPage,
      updatedAnchor = if (screenChanged && !isExplicitJump) screen.startAnchor else null,
      isSubPageTurn = screenChanged && !isExplicitJump && snapPage == previousPdfPage,
    )
  }

  fun paginate(
    pages: List<ReflowPageMeta>,
    maxContentWidthPx: Int,
    maxContentHeightPx: Int,
    paragraphSpacingPx: Int,
    measureTextHeightPx: (text: String, isHeading: Boolean, maxWidthPx: Int) -> Int,
  ): List<ReflowScreenPage> {
    if (pages.isEmpty() || maxContentWidthPx <= 0 || maxContentHeightPx <= 0) {
      return emptyList()
    }

    val specialPages = pages.filter { it.hasSpecialObjects }.map { it.pageIndex }.toSet()
    val screens = ArrayList<ReflowScreenPage>()
    val currentElements = ArrayList<PaginatedReflowElement>()
    var usedHeight = 0

    fun flushScreen() {
      if (currentElements.isEmpty()) return
      val elements = currentElements.toList()
      val sources = elements.map { it.sourcePage }.distinct()
      val specials = sources.filter { it in specialPages }.toSet()
      screens +=
        ReflowScreenPage(
          screenIndex = screens.size,
          elements = elements,
          sourcePages = sources,
          specialObjectPages = specials,
        )
      currentElements.clear()
      usedHeight = 0
    }

    for (pageMeta in pages) {
      when (val res = pageMeta.result) {
        is ReflowPageResult.FallbackOriginal -> {
          flushScreen()
          screens +=
            ReflowScreenPage(
              screenIndex = screens.size,
              elements =
                listOf(
                  PaginatedReflowElement.FallbackPage(
                    sourcePage = pageMeta.pageIndex,
                    reason = res.reason,
                    itemIndex = 0,
                    charOffset = 0,
                    charEndOffset = 0,
                  ),
                ),
              sourcePages = listOf(pageMeta.pageIndex),
              specialObjectPages = setOf(pageMeta.pageIndex),
            )
        }
        is ReflowPageResult.Reflowed -> {
          for ((itemIndex, item) in res.items.withIndex()) {
            when (item) {
              is ReflowItem.Image -> {
                val aspect =
                  (item.bounds.width / item.bounds.height.coerceAtLeast(0.01f)) *
                    pageMeta.pageAspectRatio.coerceAtLeast(0.1f)
                val targetWidthPx =
                  if (item.kind == ReflowImageKind.SYMBOL_STACK) {
                    val maxSymbolW = (maxContentWidthPx * 0.45f).toInt().coerceAtLeast(80)
                    (maxContentWidthPx * item.bounds.width.coerceIn(0.14f, 0.35f) * 1.4f)
                      .toInt()
                      .coerceIn(80, maxSymbolW)
                  } else {
                    maxContentWidthPx
                  }
                val maxAllowedHeight =
                  if (item.kind == ReflowImageKind.SYMBOL_STACK) {
                    (maxContentHeightPx * 0.45f).toInt().coerceAtLeast(48)
                  } else {
                    maxContentHeightPx
                  }
                val rawHeight = (targetWidthPx / aspect).toInt().coerceIn(48, maxAllowedHeight)
                val gap = if (currentElements.isEmpty()) 0 else paragraphSpacingPx
                if (currentElements.isNotEmpty() && usedHeight + gap + rawHeight > maxContentHeightPx) {
                  flushScreen()
                }
                val startGap = if (currentElements.isEmpty()) 0 else paragraphSpacingPx
                currentElements +=
                  PaginatedReflowElement.InlineImage(
                    bounds = item.bounds,
                    sourcePage = pageMeta.pageIndex,
                    displayHeightPx = rawHeight,
                    displayWidthPx = targetWidthPx,
                    kind = item.kind,
                    itemIndex = itemIndex,
                    charOffset = 0,
                    charEndOffset = 0,
                  )
                usedHeight += startGap + rawHeight
              }
              is ReflowItem.Paragraph -> {
                val rawText = item.text
                val firstNonWs = rawText.indexOfFirst { !it.isWhitespace() }
                if (firstNonWs < 0) continue
                val lastNonWs = rawText.indexOfLast { !it.isWhitespace() }
                var currentOffset = firstNonWs
                var remainingText = rawText.substring(firstNonWs, lastNonWs + 1)
                while (remainingText.isNotEmpty()) {
                  val gap = if (currentElements.isEmpty()) 0 else paragraphSpacingPx
                  val available = maxContentHeightPx - usedHeight - gap
                  if (available <= 0) {
                    flushScreen()
                    continue
                  }

                  val fullHeight = measureTextHeightPx(remainingText, item.isHeading, maxContentWidthPx)
                  if (fullHeight <= available) {
                    currentElements +=
                      PaginatedReflowElement.ParagraphSlice(
                        text = remainingText,
                        isHeading = item.isHeading,
                        sourcePage = pageMeta.pageIndex,
                        itemIndex = itemIndex,
                        charOffset = currentOffset,
                        charEndOffset = currentOffset + remainingText.length,
                      )
                    usedHeight += gap + fullHeight
                    remainingText = ""
                  } else {
                    val splitIndex =
                      findBestSplitIndex(
                        text = remainingText,
                        isHeading = item.isHeading,
                        maxWidthPx = maxContentWidthPx,
                        availableHeightPx = available,
                        measureTextHeightPx = measureTextHeightPx,
                      )
                    if (splitIndex <= 0) {
                      if (currentElements.isNotEmpty()) {
                        flushScreen()
                      } else {
                        // Single line/word taller than full screen: emit anyway to guarantee progress
                        currentElements +=
                          PaginatedReflowElement.ParagraphSlice(
                            text = remainingText,
                            isHeading = item.isHeading,
                            sourcePage = pageMeta.pageIndex,
                            itemIndex = itemIndex,
                            charOffset = currentOffset,
                            charEndOffset = currentOffset + remainingText.length,
                          )
                        flushScreen()
                        remainingText = ""
                      }
                    } else {
                      val head = remainingText.substring(0, splitIndex).trimEnd()
                      if (head.isNotEmpty()) {
                        currentElements +=
                          PaginatedReflowElement.ParagraphSlice(
                            text = head,
                            isHeading = item.isHeading,
                            sourcePage = pageMeta.pageIndex,
                            itemIndex = itemIndex,
                            charOffset = currentOffset,
                            charEndOffset = currentOffset + head.length,
                          )
                      }
                      flushScreen()
                      val tailRaw = remainingText.substring(splitIndex)
                      val tailStart = tailRaw.indexOfFirst { !it.isWhitespace() }
                      if (tailStart < 0) {
                        remainingText = ""
                      } else {
                        currentOffset += splitIndex + tailStart
                        remainingText = tailRaw.substring(tailStart)
                      }
                    }
                  }
                }
              }
            }
          }
        }
      }
    }
    flushScreen()
    return screens
  }

  private val wordTokenRegex = Regex("""\S+\s*""")

  private fun findBestSplitIndex(
    text: String,
    isHeading: Boolean,
    maxWidthPx: Int,
    availableHeightPx: Int,
    measureTextHeightPx: (String, Boolean, Int) -> Int,
  ): Int {
    val words = wordTokenRegex.findAll(text).map { it.range.last + 1 }.toList()
    if (words.size <= 1) return 0

    var low = 0
    var high = words.lastIndex
    var bestEnd = 0

    while (low <= high) {
      val mid = (low + high) / 2
      val candidateEnd = words[mid]
      val candidate = text.substring(0, candidateEnd).trimEnd()
      val h = measureTextHeightPx(candidate, isHeading, maxWidthPx)
      if (h <= availableHeightPx) {
        bestEnd = candidateEnd
        low = mid + 1
      } else {
        high = mid - 1
      }
    }
    return bestEnd
  }
}
