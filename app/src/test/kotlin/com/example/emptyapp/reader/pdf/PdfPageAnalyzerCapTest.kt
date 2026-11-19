package com.example.emptyapp.reader.pdf

import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PdfPageAnalyzerCapTest {

  @Test
  fun capVectorSegmentsByPositionDistributesAcrossTopMiddleAndBottom() {
    val segments =
      (0 until 600).map { i ->
        val y = 0.05f + (i / 599f) * 0.90f
        PdfVectorSegment(0.10f, y, 0.85f, y)
      }

    val capped = PdfPageAnalyzer.capVectorSegmentsByPosition(segments, maxCount = 400)

    assertEquals(400, capped.size)
    val topCount = capped.count { it.minY < 0.33f }
    val middleCount = capped.count { it.minY in 0.33f..0.67f }
    val bottomCount = capped.count { it.minY > 0.67f }
    assertTrue("Expected top segments, got $topCount", topCount >= 80)
    assertTrue("Expected middle segments, got $middleCount", middleCount >= 80)
    assertTrue("Expected bottom segments, got $bottomCount", bottomCount >= 80)
  }

  @Test
  fun capVectorSegmentsByPositionPreservesBottomTableBehindDenseTopGraphics() {
    val topDense =
      (0 until 600).map { i ->
        val y = 0.08f + (i / 599f) * 0.30f
        PdfVectorSegment(0.10f, y, 0.14f, y + 0.01f)
      }
    val bottomTable =
      listOf(
        PdfVectorSegment(0.10f, 0.74f, 0.90f, 0.74f),
        PdfVectorSegment(0.10f, 0.80f, 0.90f, 0.80f),
        PdfVectorSegment(0.10f, 0.88f, 0.90f, 0.88f),
        PdfVectorSegment(0.10f, 0.74f, 0.10f, 0.88f),
        PdfVectorSegment(0.50f, 0.74f, 0.50f, 0.88f),
        PdfVectorSegment(0.90f, 0.74f, 0.90f, 0.88f),
      )

    val capped = PdfPageAnalyzer.capVectorSegmentsByPosition(topDense + bottomTable, maxCount = 400)

    assertEquals(400, capped.size)
    assertTrue(bottomTable.all { it in capped })
    val analysis =
      PdfPageAnalyzer.emptyPageAnalysis(0).copy(
        charCount = 100,
        vectorSegments = capped,
      )
    val masks = PdfGraphicMasker.detectMasks(analysis)
    assertEquals(1, masks.size)
    assertEquals(PdfGraphicMaskKind.TABLE, masks.single().kind)
    assertTrue(masks.single().bounds.top >= 0.70f)
  }

  @Test
  fun capVectorSegmentsByPositionPreservesBottomTableWhenAllSegmentsAreRules() {
    val topRules =
      (0 until 500).map { i ->
        val y = 0.08f + (i / 499f) * 0.32f
        PdfVectorSegment(0.10f, y, 0.45f, y)
      }
    val bottomTable =
      listOf(
        PdfVectorSegment(0.12f, 0.78f, 0.88f, 0.78f),
        PdfVectorSegment(0.12f, 0.84f, 0.88f, 0.84f),
        PdfVectorSegment(0.12f, 0.90f, 0.88f, 0.90f),
        PdfVectorSegment(0.12f, 0.78f, 0.12f, 0.90f),
        PdfVectorSegment(0.50f, 0.78f, 0.50f, 0.90f),
        PdfVectorSegment(0.88f, 0.78f, 0.88f, 0.90f),
      )

    val capped = PdfPageAnalyzer.capVectorSegmentsByPosition(topRules + bottomTable, maxCount = 400)

    assertEquals(400, capped.size)
    assertTrue(bottomTable.all { it in capped })
  }

  @Test
  fun capFilledRectsByPositionDistributesAcrossPageAndPreservesBottomTable() {
    val topNoiseRects =
      (0 until 220).map { i ->
        val top = 0.05f + (i / 219f) * 0.35f
        PdfFilledRect(PdfRect(0.10f, top, 0.18f, top + 0.006f))
      }
    val bottomZebra =
      listOf(
        PdfFilledRect(PdfRect(0.12f, 0.74f, 0.88f, 0.78f)),
        PdfFilledRect(PdfRect(0.12f, 0.82f, 0.88f, 0.86f)),
      )

    val capped = PdfPageAnalyzer.capFilledRectsByPosition(topNoiseRects + bottomZebra, maxCount = 100)

    assertEquals(100, capped.size)
    assertTrue(bottomZebra.all { it in capped })
    val analysis = PdfPageAnalyzer.emptyPageAnalysis(0).copy(filledRects = capped)
    val masks = PdfGraphicMasker.detectMasks(analysis)
    assertEquals(1, masks.size)
    assertEquals(PdfGraphicMaskKind.TABLE, masks.single().kind)
    assertTrue(masks.single().bounds.top >= 0.70f)
  }

  @Test
  fun graphicsCollectorBufferRetainsBottomTableAcross4000RawCompactionAndFinalCap() {
    val buffer = PdfPageAnalyzer.GraphicsCollectorBuffer()
    for (i in 0 until 4500) {
      val y = 0.06f + ((i % 200) / 199f) * 0.34f
      buffer.addVectorSegment(PdfVectorSegment(0.10f, y, 0.14f, y + 0.005f))
    }
    val bottomTable =
      listOf(
        PdfVectorSegment(0.12f, 0.76f, 0.88f, 0.76f),
        PdfVectorSegment(0.12f, 0.82f, 0.88f, 0.82f),
        PdfVectorSegment(0.12f, 0.89f, 0.88f, 0.89f),
        PdfVectorSegment(0.12f, 0.76f, 0.12f, 0.89f),
        PdfVectorSegment(0.50f, 0.76f, 0.50f, 0.89f),
        PdfVectorSegment(0.88f, 0.76f, 0.88f, 0.89f),
      )
    bottomTable.forEach { buffer.addVectorSegment(it) }

    val finalSegments = buffer.buildVectorSegments()
    assertEquals(PdfPageAnalyzer.MAX_VECTOR_SEGMENTS_PER_PAGE, finalSegments.size)
    assertTrue(bottomTable.all { it in finalSegments })

    val analysis =
      PdfPageAnalyzer.emptyPageAnalysis(0).copy(
        charCount = 100,
        vectorSegments = finalSegments,
      )
    val masks = PdfGraphicMasker.detectMasks(analysis)
    assertEquals(1, masks.size)
    assertEquals(PdfGraphicMaskKind.TABLE, masks.single().kind)
    assertTrue(masks.single().bounds.top >= 0.70f)
  }

  @Test
  fun analyzeAllPagesProcessesAllStrokeOpsPast400AndRespectsCap() {
    PDDocument().use { doc ->
      val page = PDPage(PDRectangle.A4)
      doc.addPage(page)
      PDPageContentStream(doc, page).use { stream ->
        stream.beginText()
        stream.setFont(PDType1Font.HELVETICA, 11f)
        stream.newLineAtOffset(72f, 500f)
        stream.showText("Body paragraph between top chart and bottom table.")
        stream.endText()

        for (i in 0 until 450) {
          val y = 560f + (i % 60) * 4f
          val x = 60f + (i / 60) * 55f
          stream.moveTo(x, y)
          stream.lineTo(x + 40f, y)
          stream.stroke()
        }

        val tableYs = listOf(200f, 160f, 120f)
        for (ty in tableYs) {
          stream.moveTo(70f, ty)
          stream.lineTo(520f, ty)
          stream.stroke()
        }
        val tableXs = listOf(70f, 295f, 520f)
        for (tx in tableXs) {
          stream.moveTo(tx, 120f)
          stream.lineTo(tx, 200f)
          stream.stroke()
        }
      }

      val analyses = PdfPageAnalyzer.analyzeAllPages(doc)
      assertEquals(1, analyses.size)
      val analysis = analyses.single()
      assertEquals(456, analysis.vectorOpCount)
      assertTrue(analysis.vectorSegments.size <= PdfPageAnalyzer.MAX_VECTOR_SEGMENTS_PER_PAGE)
    }
  }
}
