package com.example.emptyapp.reader.pdf

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PdfGraphicMaskerTest {
  private fun page(
    lines: List<PdfTextLine> = emptyList(),
    vectors: List<PdfVectorSegment> = emptyList(),
    rects: List<PdfFilledRect> = emptyList(),
  ): PdfPageAnalysis =
    PdfPageAnalysis(
      pageIndex = 0,
      pageWidthPt = 600f,
      pageHeightPt = 800f,
      charCount = lines.sumOf { it.text.length },
      textLines = lines,
      imageBlocks = emptyList(),
      totalImageAreaFraction = 0f,
      vectorOpCount = vectors.size + rects.size,
      columnCount = 1,
      vectorSegments = vectors,
      filledRects = rects,
    )

  private fun line(
    text: String,
    left: Float,
    top: Float,
    right: Float,
    bottom: Float,
  ): PdfTextLine =
    PdfTextLine(
      text = text,
      bounds = PdfRect(left, top, right, bottom),
      fontSizePt = 11f,
    )

  @Test
  fun detectsClosedVectorGridTableAndFiltersEnclosedText() {
    val lines =
      listOf(
        line("Intro paragraph before table.", 0.10f, 0.10f, 0.85f, 0.12f),
        line("Col A", 0.15f, 0.22f, 0.30f, 0.24f),
        line("Col B", 0.55f, 0.22f, 0.70f, 0.24f),
        line("100", 0.15f, 0.28f, 0.25f, 0.30f),
        line("200", 0.55f, 0.28f, 0.65f, 0.30f),
        line("Closing paragraph after table.", 0.10f, 0.45f, 0.85f, 0.47f),
      )
    val vectors =
      listOf(
        PdfVectorSegment(0.12f, 0.20f, 0.80f, 0.20f),
        PdfVectorSegment(0.12f, 0.26f, 0.80f, 0.26f),
        PdfVectorSegment(0.12f, 0.33f, 0.80f, 0.33f),
        PdfVectorSegment(0.12f, 0.20f, 0.12f, 0.33f),
        PdfVectorSegment(0.45f, 0.20f, 0.45f, 0.33f),
        PdfVectorSegment(0.80f, 0.20f, 0.80f, 0.33f),
      )

    val masks = PdfGraphicMasker.detectMasks(page(lines = lines, vectors = vectors))
    assertEquals(1, masks.size)
    assertEquals(PdfGraphicMaskKind.TABLE, masks[0].kind)

    val remaining = PdfGraphicMasker.filterMaskedLines(lines, masks)
    assertEquals(2, remaining.size)
    assertEquals("Intro paragraph before table.", remaining[0].text)
    assertEquals("Closing paragraph after table.", remaining[1].text)
  }

  @Test
  fun detectsHorizontalOnlyAcademicTable() {
    val lines =
      listOf(
        line("Model", 0.15f, 0.23f, 0.28f, 0.25f),
        line("Score", 0.65f, 0.23f, 0.78f, 0.25f),
        line("Alpha", 0.15f, 0.29f, 0.28f, 0.31f),
        line("92.5", 0.65f, 0.29f, 0.75f, 0.31f),
      )
    val vectors =
      listOf(
        PdfVectorSegment(0.12f, 0.21f, 0.82f, 0.21f),
        PdfVectorSegment(0.12f, 0.27f, 0.82f, 0.27f),
        PdfVectorSegment(0.12f, 0.34f, 0.82f, 0.34f),
      )

    val masks = PdfGraphicMasker.detectMasks(page(lines = lines, vectors = vectors))
    assertEquals(1, masks.size)
    assertEquals(PdfGraphicMaskKind.TABLE, masks[0].kind)
  }

  @Test
  fun ignoresFullPageBorderFrame() {
    val lines = listOf(line("Normal body text inside page border.", 0.12f, 0.20f, 0.85f, 0.22f))
    val vectors =
      listOf(
        PdfVectorSegment(0.02f, 0.02f, 0.98f, 0.02f),
        PdfVectorSegment(0.02f, 0.98f, 0.98f, 0.98f),
        PdfVectorSegment(0.02f, 0.02f, 0.02f, 0.98f),
        PdfVectorSegment(0.98f, 0.02f, 0.98f, 0.98f),
      )

    val masks = PdfGraphicMasker.detectMasks(page(lines = lines, vectors = vectors))
    assertTrue(masks.isEmpty())
  }

  @Test
  fun detectsZebraStripedFilledRectTableButIgnoresSingleCalloutBox() {
    val zebraRects =
      listOf(
        PdfFilledRect(PdfRect(0.12f, 0.20f, 0.85f, 0.24f)),
        PdfFilledRect(PdfRect(0.12f, 0.28f, 0.85f, 0.32f)),
      )
    val zebraMasks = PdfGraphicMasker.detectMasks(page(rects = zebraRects))
    assertEquals(1, zebraMasks.size)
    assertEquals(PdfGraphicMaskKind.TABLE, zebraMasks[0].kind)

    val singleCallout = listOf(PdfFilledRect(PdfRect(0.12f, 0.40f, 0.85f, 0.52f)))
    val calloutMasks = PdfGraphicMasker.detectMasks(page(rects = singleCallout))
    assertTrue(calloutMasks.isEmpty())
  }

  @Test
  fun detectsBorderlessTextGridTable() {
    val lines =
      listOf(
        line("Summary of results:", 0.10f, 0.12f, 0.45f, 0.14f),
        line("Item     Qty     Price", 0.12f, 0.20f, 0.72f, 0.22f),
        line("Apple    12      $3.50", 0.12f, 0.23f, 0.72f, 0.25f),
        line("Orange   8       $2.40", 0.12f, 0.26f, 0.72f, 0.28f),
        line("Banana   15      $1.90", 0.12f, 0.29f, 0.72f, 0.31f),
        line("Conclusion follows here.", 0.10f, 0.40f, 0.55f, 0.42f),
      )

    val masks = PdfGraphicMasker.detectMasks(page(lines = lines))
    assertEquals(1, masks.size)
    assertEquals(PdfGraphicMaskKind.TABLE, masks[0].kind)

    val remaining = PdfGraphicMasker.filterMaskedLines(lines, masks)
    assertEquals(2, remaining.size)
  }

  @Test
  fun doesNotMaskTwoColumnLongProseAsTable() {
    val lines =
      listOf(
        line("This is a long left column sentence that wraps naturally across lines.", 0.08f, 0.20f, 0.48f, 0.22f),
        line("This is a long right column sentence that wraps naturally across lines.", 0.52f, 0.20f, 0.92f, 0.22f),
        line("Another full prose line in the left column continuing the paragraph.", 0.08f, 0.23f, 0.48f, 0.25f),
        line("Another full prose line in the right column continuing the paragraph.", 0.52f, 0.23f, 0.92f, 0.25f),
        line("Third full prose line in the left column with many words and punctuation.", 0.08f, 0.26f, 0.48f, 0.28f),
        line("Third full prose line in the right column with many words and punctuation.", 0.52f, 0.26f, 0.92f, 0.28f),
      )

    val masks = PdfGraphicMasker.detectMasks(page(lines = lines))
    assertTrue(masks.isEmpty())
  }

  @Test
  fun detectsHexagramSymbolStack() {
    val lines =
      listOf(
        line("Dịch nghĩa quẻ Thuần Càn trong kinh dịch:", 0.10f, 0.12f, 0.85f, 0.14f),
        line("——————", 0.44f, 0.20f, 0.56f, 0.215f),
        line("——  ——", 0.44f, 0.225f, 0.56f, 0.24f),
        line("——————", 0.44f, 0.25f, 0.56f, 0.265f),
        line("——  ——", 0.44f, 0.275f, 0.56f, 0.29f),
        line("——————", 0.44f, 0.30f, 0.56f, 0.315f),
        line("——————", 0.44f, 0.325f, 0.56f, 0.34f),
        line("Giải thích chi tiết bên dưới.", 0.10f, 0.40f, 0.70f, 0.42f),
      )

    val masks = PdfGraphicMasker.detectMasks(page(lines = lines))
    assertEquals(1, masks.size)
    assertEquals(PdfGraphicMaskKind.SYMBOL_STACK, masks[0].kind)

    val remaining = PdfGraphicMasker.filterMaskedLines(lines, masks)
    assertEquals(2, remaining.size)
  }

  @Test
  fun doesNotMaskCenteredShortPoetryAsSymbolStack() {
    val lines =
      listOf(
        line("Trăm năm trong cõi người ta", 0.10f, 0.10f, 0.90f, 0.12f),
        line("Roses are red", 0.42f, 0.20f, 0.56f, 0.215f),
        line("Violets are blue", 0.42f, 0.225f, 0.57f, 0.24f),
        line("Sugar is sweet", 0.42f, 0.25f, 0.56f, 0.265f),
      )

    val masks = PdfGraphicMasker.detectMasks(page(lines = lines))
    assertTrue(masks.isEmpty())
  }

  @Test
  fun detectsSparseThreeColumnTableWithEmptyIntermediateCells() {
    val lines =
      listOf(
        line("CÁCH BỐ QUẺ", 0.40f, 0.31f, 0.60f, 0.325f),
        line("Chánh quái", 0.08f, 0.34f, 0.22f, 0.355f),
        line("Hổ quái", 0.32f, 0.34f, 0.42f, 0.355f),
        line("Biến quái", 0.48f, 0.34f, 0.58f, 0.355f),
        line("Tên là Hỏa Trạch Khuê", 0.06f, 0.36f, 0.27f, 0.375f),
        line("(gọi tắt là Khuê quái)", 0.06f, 0.38f, 0.26f, 0.395f),
        line("Cây khô là Ly số 3", 0.06f, 0.40f, 0.25f, 0.415f),
        line("Ly Hỏa", 0.06f, 0.42f, 0.14f, 0.435f),
        line("Khám Thuỷ", 0.30f, 0.42f, 0.40f, 0.435f),
        line("Cấn Mộc", 0.44f, 0.42f, 0.54f, 0.435f),
        line("Hào 6 ____ * Dụng", 0.06f, 0.44f, 0.24f, 0.455f),
        line("Hào 6 __ __", 0.30f, 0.44f, 0.40f, 0.455f),
        line("Hào 6 ____", 0.44f, 0.44f, 0.54f, 0.455f),
        line("Giờ Thìn số 5.", 0.40f, 0.55f, 0.58f, 0.565f),
      )

    val masks = PdfGraphicMasker.detectMasks(page(lines = lines))
    assertEquals(1, masks.size)
    assertEquals(PdfGraphicMaskKind.TABLE, masks[0].kind)

    val remaining = PdfGraphicMasker.filterMaskedLines(lines, masks)
    assertEquals(2, remaining.size)
    assertEquals("CÁCH BỐ QUẺ", remaining[0].text)
    assertEquals("Giờ Thìn số 5.", remaining[1].text)
  }
}
