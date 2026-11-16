package com.example.emptyapp.reader.pdf

import kotlinx.serialization.Serializable

@Serializable
enum class PdfReadingMode {
  SMART_ORIGINAL,
  GUIDED_COLUMN,
  HYBRID_REFLOW,
}

@Serializable
enum class PdfDocumentCategory {
  NOVEL_TEXT,
  TECHNICAL_PAPER,
  MAGAZINE,
  SCANNED_COMIC,
}

@Serializable
data class PdfBookConfig(
  val mode: PdfReadingMode? = null,
  val category: PdfDocumentCategory? = null,
  val autoCrop: Boolean? = null,
  val reflowFontScale: Float? = null,
  val reflowAllowed: Boolean = true,
)

@Serializable
data class PdfRect(
  val left: Float,
  val top: Float,
  val right: Float,
  val bottom: Float,
) {
  val width: Float
    get() = (right - left).coerceAtLeast(0f)

  val height: Float
    get() = (bottom - top).coerceAtLeast(0f)

  val area: Float
    get() = width * height
}

@Serializable
data class PdfTextLine(
  val text: String,
  val bounds: PdfRect,
  val fontSizePt: Float,
)

@Serializable
data class PdfImageBlock(
  val bounds: PdfRect,
  val pageAreaFraction: Float,
)

@Serializable
data class PdfVectorSegment(
  val x1: Float,
  val y1: Float,
  val x2: Float,
  val y2: Float,
) {
  val minX: Float get() = minOf(x1, x2)
  val maxX: Float get() = maxOf(x1, x2)
  val minY: Float get() = minOf(y1, y2)
  val maxY: Float get() = maxOf(y1, y2)
  val width: Float get() = maxX - minX
  val height: Float get() = maxY - minY
}

@Serializable
data class PdfFilledRect(
  val bounds: PdfRect,
)

enum class PdfGraphicMaskKind {
  TABLE,
  SYMBOL_STACK,
}

data class PdfGraphicMask(
  val bounds: PdfRect,
  val kind: PdfGraphicMaskKind,
)

@Serializable
data class PdfPageAnalysis(
  val pageIndex: Int,
  val pageWidthPt: Float,
  val pageHeightPt: Float,
  val charCount: Int,
  val textLines: List<PdfTextLine>,
  val imageBlocks: List<PdfImageBlock>,
  val totalImageAreaFraction: Float,
  val vectorOpCount: Int,
  val columnCount: Int,
  val vectorSegments: List<PdfVectorSegment> = emptyList(),
  val filledRects: List<PdfFilledRect> = emptyList(),
)

data class PdfClassificationResult(
  val category: PdfDocumentCategory,
  val recommendedMode: PdfReadingMode,
  val reflowAllowed: Boolean,
  val avgCharsPerPage: Float,
  val avgImageAreaFraction: Float,
  val avgVectorOpsPerPage: Float,
  val detectedColumns: Int,
)
