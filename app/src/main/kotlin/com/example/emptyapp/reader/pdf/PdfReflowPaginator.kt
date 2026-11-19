package com.example.emptyapp.reader.pdf

data class ReflowPageMeta(
  val pageIndex: Int,
  val hasSpecialObjects: Boolean,
  val result: ReflowPageResult,
  val pageAspectRatio: Float = 1f,
)

sealed interface PaginatedReflowElement {
  val sourcePage: Int

  data class ParagraphSlice(
    val text: String,
    val isHeading: Boolean,
    override val sourcePage: Int,
  ) : PaginatedReflowElement

  data class InlineImage(
    val bounds: PdfRect,
    override val sourcePage: Int,
    val displayHeightPx: Int,
    val displayWidthPx: Int = 0,
    val kind: ReflowImageKind = ReflowImageKind.ILLUSTRATION,
  ) : PaginatedReflowElement

  data class FallbackPage(
    override val sourcePage: Int,
    val reason: ReflowFallbackReason,
  ) : PaginatedReflowElement
}

data class ReflowScreenPage(
  val screenIndex: Int,
  val elements: List<PaginatedReflowElement>,
  val sourcePages: List<Int>,
  val specialObjectPages: Set<Int>,
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
    val exact = screens.indexOfFirst { pdfPageIndex in it.sourcePages }
    if (exact >= 0) return exact
    val after = screens.indexOfFirst { it.sourcePages.any { p -> p >= pdfPageIndex } }
    return if (after >= 0) after else screens.lastIndex
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
      val sources = currentElements.map { it.sourcePage }.distinct()
      val specials = sources.filter { it in specialPages }.toSet()
      screens +=
        ReflowScreenPage(
          screenIndex = screens.size,
          elements = currentElements.toList(),
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
              elements = listOf(PaginatedReflowElement.FallbackPage(pageMeta.pageIndex, res.reason)),
              sourcePages = listOf(pageMeta.pageIndex),
              specialObjectPages = setOf(pageMeta.pageIndex),
            )
        }
        is ReflowPageResult.Reflowed -> {
          for (item in res.items) {
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
                  )
                usedHeight += startGap + rawHeight
              }
              is ReflowItem.Paragraph -> {
                var remainingText = item.text.trim()
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
                          )
                      }
                      flushScreen()
                      remainingText = remainingText.substring(splitIndex).trimStart()
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

  private fun findBestSplitIndex(
    text: String,
    isHeading: Boolean,
    maxWidthPx: Int,
    availableHeightPx: Int,
    measureTextHeightPx: (String, Boolean, Int) -> Int,
  ): Int {
    val words = Regex("""\S+\s*""").findAll(text).map { it.range.last + 1 }.toList()
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
