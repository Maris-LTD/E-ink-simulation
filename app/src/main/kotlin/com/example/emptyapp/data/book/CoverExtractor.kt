package com.example.emptyapp.data.book

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import java.io.File
import java.io.FileOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.readium.r2.shared.publication.Publication
import org.readium.r2.shared.publication.services.cover

object CoverExtractor {
  private const val MAX_COVER_WIDTH = 360

  suspend fun extract(
    bookFile: File,
    format: BookFormat,
    publication: Publication?,
    targetFile: File,
  ): Boolean =
    withContext(Dispatchers.IO) {
      try {
        val bitmap =
          when (format) {
            BookFormat.PDF -> renderPdfFirstPage(bookFile) ?: publication?.cover()
            BookFormat.EPUB, BookFormat.CBZ -> publication?.cover()
          } ?: return@withContext false

        saveScaledJpeg(bitmap, targetFile)
      } catch (e: kotlinx.coroutines.CancellationException) {
        throw e
      } catch (_: Throwable) {
        targetFile.delete()
        false
      }
    }

  private fun renderPdfFirstPage(pdfFile: File): Bitmap? =
    runCatching {
      ParcelFileDescriptor.open(pdfFile, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
        PdfRenderer(pfd).use { renderer ->
          if (renderer.pageCount <= 0) return@runCatching null
          renderer.openPage(0).use { page ->
            val scale = (MAX_COVER_WIDTH.toFloat() / page.width).coerceAtMost(2f)
            val width = (page.width * scale).toInt().coerceAtLeast(1)
            val height = (page.height * scale).toInt().coerceAtLeast(1)
            Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply {
              eraseColor(Color.WHITE)
              page.render(this, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            }
          }
        }
      }
    }.getOrNull()

  private fun saveScaledJpeg(source: Bitmap, targetFile: File): Boolean =
    runCatching {
      targetFile.parentFile?.mkdirs()
      val scaled =
        if (source.width > MAX_COVER_WIDTH) {
          val targetHeight = (source.height * (MAX_COVER_WIDTH.toFloat() / source.width)).toInt().coerceAtLeast(1)
          Bitmap.createScaledBitmap(source, MAX_COVER_WIDTH, targetHeight, true)
        } else {
          source
        }
      FileOutputStream(targetFile).use { out ->
        scaled.compress(Bitmap.CompressFormat.JPEG, 85, out)
      }
      if (scaled !== source) scaled.recycle()
      true
    }.getOrElse {
      targetFile.delete()
      false
    }
}
