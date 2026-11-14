package com.example.emptyapp.system

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.os.BatteryManager
import android.os.Handler
import android.os.Looper
import android.service.wallpaper.WallpaperService
import android.view.SurfaceHolder
import androidx.core.content.res.ResourcesCompat
import com.example.emptyapp.EmptyApplication
import com.example.emptyapp.R
import com.example.emptyapp.data.stats.ReadingGamification
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

class EInkWallpaperService : WallpaperService() {
  override fun onCreateEngine(): Engine = EInkEngine()

  private inner class EInkEngine : Engine() {
    private val handler = Handler(Looper.getMainLooper())
    private val engineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var observerJob: Job? = null
    private var visible = false
    private var cachedCoverPath: String? = null
    private var cachedCoverBitmap: Bitmap? = null

    private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm", Locale.getDefault())
    private val dateFormatter = DateTimeFormatter.ofPattern("EEEE, dd/MM/yyyy", Locale("vi", "VN"))
    private val monoFilter = ColorMatrixColorFilter(ColorMatrix().apply { setSaturation(0f) })

    private val drawTask =
      object : Runnable {
        override fun run() {
          drawFrame()
          if (visible) {
            val nowMs = System.currentTimeMillis()
            val delayToNextMinute = 60_000L - (nowMs % 60_000L) + 50L
            handler.postDelayed(this, delayToNextMinute)
          }
        }
      }

    override fun onCreate(surfaceHolder: SurfaceHolder?) {
      super.onCreate(surfaceHolder)
      val container = (applicationContext as? EmptyApplication)?.container ?: return
      observerJob =
        engineScope.launch {
          combine(
            container.settings.settings,
            container.library.books,
            container.stats.stats,
            container.annotations.highlights,
          ) { _, _, _, _ -> Unit }.collect {
            if (visible) drawFrame()
          }
        }
    }

    override fun onVisibilityChanged(visible: Boolean) {
      this.visible = visible
      handler.removeCallbacks(drawTask)
      if (visible) {
        handler.post(drawTask)
      }
    }

    override fun onSurfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
      super.onSurfaceChanged(holder, format, width, height)
      drawFrame()
    }

    override fun onSurfaceDestroyed(holder: SurfaceHolder) {
      super.onSurfaceDestroyed(holder)
      visible = false
      handler.removeCallbacks(drawTask)
    }

    override fun onDestroy() {
      observerJob?.cancel()
      engineScope.cancel()
      cachedCoverBitmap?.recycle()
      cachedCoverBitmap = null
      super.onDestroy()
    }

    private fun drawFrame() {
      val holder = surfaceHolder
      var canvas: Canvas? = null
      try {
        canvas = holder.lockCanvas() ?: return
        renderWallpaper(canvas)
      } finally {
        if (canvas != null) {
          runCatching { holder.unlockCanvasAndPost(canvas) }
        }
      }
    }

    private fun renderWallpaper(canvas: Canvas) {
      val container = (applicationContext as? EmptyApplication)?.container
      val settings = container?.settings?.settings?.value
      val bgColor = settings?.tone?.background ?: 0xFFFFFFFF.toInt()
      val fgColor = settings?.tone?.text ?: 0xFF111111.toInt()

      canvas.drawColor(bgColor)

      val w = canvas.width.toFloat()
      val h = canvas.height.toFloat()
      if (w <= 0f || h <= 0f) return

      drawPaperGrain(canvas, w, h, fgColor)

      val books = container?.library?.books?.value.orEmpty()
      val currentBook =
        books.firstOrNull { it.progress > 0f && it.progress < 0.99f } ?: books.firstOrNull()
      val completedCount = books.count { it.progress >= 0.99f }
      val highlightsCount = container?.annotations?.highlights?.value?.size ?: 0
      val stats = container?.stats?.stats?.value
      val todaySec = stats?.todaySeconds() ?: 0
      val todayMin = todaySec / 60
      val goalMin = (stats?.dailyGoalMinutes ?: 20).coerceAtLeast(5)
      val goalProgress = (todaySec.toFloat() / (goalMin * 60f)).coerceIn(0f, 1f)
      val streak = stats?.currentStreak ?: 0
      val localized = com.example.emptyapp.LocaleHelper.wrap(applicationContext)
      val rank =
        if (stats != null) ReadingGamification.rankFor(stats, completedCount, highlightsCount, localized)
        else null
      val batteryPct = readBatteryPercent(applicationContext)
      val now = LocalDateTime.now()
      val baseTypeface = ResourcesCompat.getFont(this@EInkWallpaperService, R.font.rubik) ?: Typeface.SANS_SERIF

      val statusPaint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
          color = fgColor
          alpha = 175
          textSize = w * 0.033f
          typeface = Typeface.create(baseTypeface, Typeface.BOLD)
          textAlign = Paint.Align.CENTER
        }
      val clockPaint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
          color = fgColor
          textSize = w * 0.21f
          typeface = Typeface.create(baseTypeface, Typeface.BOLD)
          textAlign = Paint.Align.CENTER
        }
      val datePaint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
          color = fgColor
          alpha = 185
          textSize = w * 0.042f
          typeface = Typeface.create(baseTypeface, Typeface.NORMAL)
          textAlign = Paint.Align.CENTER
        }
      val rankText = rank?.let { localized.getString(com.example.emptyapp.R.string.wallpaper_rank_format, it.level, it.title) } ?: "eInk Reader"
      canvas.drawText(
        localized.getString(com.example.emptyapp.R.string.wallpaper_status_format, batteryPct, streak, rankText),
        w / 2f,
        h * 0.14f,
        statusPaint,
      )
      canvas.drawText(now.format(timeFormatter), w / 2f, h * 0.29f, clockPaint)
      canvas.drawText(now.format(dateFormatter), w / 2f, h * 0.34f, datePaint)

      val strokeW = (w * 0.004f).coerceAtLeast(2f)
      val borderPaint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
          color = fgColor
          style = Paint.Style.STROKE
          strokeWidth = strokeW
        }
      val fillPaint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
          color = fgColor
          style = Paint.Style.FILL
        }

      // Daily goal bar
      val padX = w * 0.09f
      val goalTop = h * 0.635f
      val goalBarH = w * 0.022f
      val leftPaint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
          color = fgColor
          alpha = 175
          textSize = w * 0.032f
          typeface = Typeface.create(baseTypeface, Typeface.BOLD)
          textAlign = Paint.Align.LEFT
        }
      val rightPaint =
        Paint(leftPaint).apply {
          textAlign = Paint.Align.RIGHT
        }
      canvas.drawText(localized.getString(com.example.emptyapp.R.string.wallpaper_daily_goal), padX, goalTop - w * 0.02f, leftPaint)
      canvas.drawText(localized.getString(com.example.emptyapp.R.string.wallpaper_minutes_format, todayMin, goalMin), w - padX, goalTop - w * 0.02f, rightPaint)
      canvas.drawRect(padX, goalTop, w - padX, goalTop + goalBarH, borderPaint)
      if (goalProgress > 0f) {
        val fillW = (w - 2f * padX) * goalProgress
        canvas.drawRect(padX, goalTop, padX + fillW, goalTop + goalBarH, fillPaint)
      }

      // Currently reading card with monochrome book cover
      val cardTop = h * 0.695f
      val cardH = w * 0.31f
      val cardBottom = cardTop + cardH
      canvas.drawRect(padX, cardTop, w - padX, cardBottom, borderPaint)

      val innerPad = w * 0.04f
      val coverW = w * 0.16f
      val coverH = cardH - innerPad * 2f
      val coverRect = RectF(padX + innerPad, cardTop + innerPad, padX + innerPad + coverW, cardTop + innerPad + coverH)
      canvas.drawRect(coverRect, borderPaint)

      val coverFile = currentBook?.let { container?.library?.coverFileOf(it) }
      val coverBmp = loadCachedCover(coverFile)
      if (coverBmp != null) {
        val imgPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply { colorFilter = monoFilter }
        val src = Rect(0, 0, coverBmp.width, coverBmp.height)
        canvas.drawBitmap(coverBmp, src, coverRect, imgPaint)
      } else {
        val badgePaint =
          Paint(leftPaint).apply {
            textAlign = Paint.Align.CENTER
            textSize = w * 0.028f
          }
        canvas.drawText(
          currentBook?.format?.label ?: "BOOK",
          coverRect.centerX(),
          coverRect.centerY() + w * 0.01f,
          badgePaint,
        )
      }

      val textX = coverRect.right + w * 0.04f
      val maxTextW = (w - padX - innerPad) - textX
      val titlePaint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
          color = fgColor
          textSize = w * 0.044f
          typeface = Typeface.create(baseTypeface, Typeface.BOLD)
          textAlign = Paint.Align.LEFT
        }
      val subPaint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
          color = fgColor
          alpha = 180
          textSize = w * 0.034f
          typeface = Typeface.create(baseTypeface, Typeface.NORMAL)
          textAlign = Paint.Align.LEFT
        }

      canvas.drawText(localized.getString(com.example.emptyapp.R.string.wallpaper_currently_reading), textX, cardTop + innerPad + w * 0.032f, leftPaint)
      val rawTitle = currentBook?.title ?: localized.getString(com.example.emptyapp.R.string.wallpaper_no_book)
      val clippedTitle = ellipsize(rawTitle, titlePaint, maxTextW)
      canvas.drawText(clippedTitle, textX, cardTop + innerPad + w * 0.095f, titlePaint)

      if (currentBook != null) {
        val authorText = currentBook.author.ifBlank { currentBook.format.label }
        canvas.drawText(
          ellipsize(authorText, subPaint, maxTextW),
          textX,
          cardTop + innerPad + w * 0.145f,
          subPaint,
        )
        val pct = (currentBook.progress * 100).toInt()
        val pBarY = cardBottom - innerPad - w * 0.024f
        val pBarH = w * 0.018f
        val pBarRight = w - padX - innerPad - w * 0.11f
        canvas.drawRect(textX, pBarY, pBarRight, pBarY + pBarH, borderPaint)
        if (currentBook.progress > 0f) {
          val fill = (pBarRight - textX) * currentBook.progress.coerceIn(0f, 1f)
          canvas.drawRect(textX, pBarY, textX + fill, pBarY + pBarH, fillPaint)
        }
        canvas.drawText("$pct%", w - padX - innerPad, pBarY + pBarH, rightPaint)
      }
    }

    private fun drawPaperGrain(
      canvas: Canvas,
      w: Float,
      h: Float,
      fgColor: Int,
    ) {
      val grainPaint =
        Paint().apply {
          color = fgColor
          alpha = 18
          strokeWidth = 2f
        }
      var seed = 0x5DEECE66DL
      val step = (w / 28f).coerceAtLeast(24f).toInt()
      var y = step / 2
      while (y < h.toInt()) {
        var x = step / 2
        while (x < w.toInt()) {
          seed = (seed * 0x5DEECE66DL + 0xBL) and 0xFFFFFFFFFFFFL
          if ((seed and 3L) == 0L) {
            val ox = ((seed shr 4) % 7).toFloat() - 3f
            val oy = ((seed shr 8) % 7).toFloat() - 3f
            canvas.drawPoint(x + ox, y + oy, grainPaint)
          }
          x += step
        }
        y += step
      }
    }

    private fun loadCachedCover(file: File?): Bitmap? {
      val path = file?.takeIf { it.exists() }?.absolutePath
      if (path == null) {
        cachedCoverPath = null
        cachedCoverBitmap?.recycle()
        cachedCoverBitmap = null
        return null
      }
      if (path == cachedCoverPath && cachedCoverBitmap != null) return cachedCoverBitmap
      cachedCoverBitmap?.recycle()
      cachedCoverBitmap = runCatching { BitmapFactory.decodeFile(path) }.getOrNull()
      cachedCoverPath = path
      return cachedCoverBitmap
    }

    private fun ellipsize(text: String, paint: Paint, maxWidth: Float): String {
      if (paint.measureText(text) <= maxWidth) return text
      var end = text.length
      while (end > 1 && paint.measureText(text.substring(0, end) + "…") > maxWidth) {
        end -= 1
      }
      return text.substring(0, end) + "…"
    }

    private fun readBatteryPercent(context: Context): Int {
      val manager = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager ?: return 100
      val pct = manager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
      return if (pct in 0..100) pct else 100
    }
  }
}
