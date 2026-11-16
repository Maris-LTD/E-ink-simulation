package com.example.emptyapp.reader.pdf

import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import java.io.File

object PdfDocumentClassifier {
  private const val MIN_TEXT_CHARS_PER_PAGE = 30f
  private const val MIN_NOVEL_CHARS_PER_PAGE = 80f
  private const val MAGAZINE_IMAGE_AREA_THRESHOLD = 0.35f
  private const val TECHNICAL_VECTOR_OPS_THRESHOLD = 120f

  fun samplePageIndices(pageCount: Int): List<Int> {
    if (pageCount <= 0) return emptyList()
    if (pageCount <= 5) return (0 until pageCount).toList()
    return listOf(
      1,
      2,
      pageCount / 3,
      pageCount / 2,
      (pageCount * 2) / 3,
    ).distinct().filter { it in 0 until pageCount }
  }

  fun classifyFile(file: File): PdfClassificationResult? {
    val pageCount =
      runCatching {
        ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { fd ->
          PdfRenderer(fd).use { it.pageCount }
        }
      }.getOrNull() ?: return null

    val indices = samplePageIndices(pageCount)
    val analyses = PdfPageAnalyzer.analyzePages(file, indices)
    if (analyses.isEmpty()) return null
    return classify(analyses)
  }

  fun classify(samples: List<PdfPageAnalysis>): PdfClassificationResult {
    if (samples.isEmpty()) {
      return PdfClassificationResult(
        category = PdfDocumentCategory.SCANNED_COMIC,
        recommendedMode = PdfReadingMode.SMART_ORIGINAL,
        reflowAllowed = false,
        avgCharsPerPage = 0f,
        avgImageAreaFraction = 0f,
        avgVectorOpsPerPage = 0f,
        detectedColumns = 1,
      )
    }

    val avgChars = samples.map { it.charCount }.average().toFloat()
    val avgImageArea = samples.map { it.totalImageAreaFraction }.average().toFloat()
    val avgVectors = samples.map { it.vectorOpCount }.average().toFloat()
    val dominantColumns = samples.maxOf { it.columnCount }

    val category: PdfDocumentCategory
    val recommendedMode: PdfReadingMode
    val reflowAllowed: Boolean

    when {
      avgChars < MIN_TEXT_CHARS_PER_PAGE -> {
        category = PdfDocumentCategory.SCANNED_COMIC
        recommendedMode = PdfReadingMode.SMART_ORIGINAL
        reflowAllowed = false
      }
      avgImageArea > MAGAZINE_IMAGE_AREA_THRESHOLD || dominantColumns >= 3 -> {
        category = PdfDocumentCategory.MAGAZINE
        recommendedMode = PdfReadingMode.SMART_ORIGINAL
        reflowAllowed = true
      }
      dominantColumns >= 2 || avgVectors > TECHNICAL_VECTOR_OPS_THRESHOLD -> {
        category = PdfDocumentCategory.TECHNICAL_PAPER
        recommendedMode = PdfReadingMode.GUIDED_COLUMN
        reflowAllowed = true
      }
      avgChars >= MIN_NOVEL_CHARS_PER_PAGE -> {
        category = PdfDocumentCategory.NOVEL_TEXT
        recommendedMode = PdfReadingMode.HYBRID_REFLOW
        reflowAllowed = true
      }
      else -> {
        category = PdfDocumentCategory.MAGAZINE
        recommendedMode = PdfReadingMode.SMART_ORIGINAL
        reflowAllowed = true
      }
    }

    return PdfClassificationResult(
      category = category,
      recommendedMode = recommendedMode,
      reflowAllowed = reflowAllowed,
      avgCharsPerPage = avgChars,
      avgImageAreaFraction = avgImageArea,
      avgVectorOpsPerPage = avgVectors,
      detectedColumns = dominantColumns,
    )
  }
}
