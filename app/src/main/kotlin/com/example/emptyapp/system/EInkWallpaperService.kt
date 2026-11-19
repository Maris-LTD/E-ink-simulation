package com.example.emptyapp.system

import android.app.KeyguardManager
import android.content.Context
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.os.BatteryManager
import android.os.Handler
import android.os.Looper
import android.service.wallpaper.WallpaperService
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.format.DateFormat
import android.view.SurfaceHolder
import androidx.core.content.res.ResourcesCompat
import com.example.emptyapp.EmptyApplication
import com.example.emptyapp.LocaleHelper
import com.example.emptyapp.R
import com.example.emptyapp.data.book.mostRecentlyReadBook
import com.example.emptyapp.data.stats.ReaderRankInfo
import com.example.emptyapp.data.stats.ReadingGamification
import com.example.emptyapp.data.stats.ReadingStatsData
import com.example.emptyapp.reader.LockScreenReadingNotifier
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
import kotlinx.coroutines.withContext

class EInkWallpaperService : WallpaperService() {
  override fun onCreateEngine(): Engine = EInkEngine()

  private inner class EInkEngine : Engine() {
    private val handler = Handler(Looper.getMainLooper())
    private val engineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var observerJob: Job? = null
    private var visible = false
    private var cachedCoverKey: WallpaperCoverKey? = null
    private var loadingCoverKey: WallpaperCoverKey? = null
    private var coverLoadJob: Job? = null
    private var cachedCoverBitmap: Bitmap? = null

    private var cachedLocaleKey: WallpaperLocaleKey? = null
    private var cachedLocalizedContext: Context? = null
    private var cachedDateLocale: Locale? = null
    private var cachedLanguageCode: String? = null
    private var cachedFormatterKey: WallpaperFormatterKey? = null
    private var cachedDateFormatter: DateTimeFormatter? = null
    private var cachedClockFormatter: DateTimeFormatter? = null
    private var cachedRankKey: WallpaperRankKey? = null
    private var cachedRankInfo: ReaderRankInfo? = null
    private var cachedExcerptKey: WallpaperExcerptLayoutKey? = null
    private var cachedExcerptLayout: StaticLayout? = null

    private val monoFilter = ColorMatrixColorFilter(ColorMatrix().apply { setSaturation(0f) })
    private val baseTypeface: Typeface by lazy {
      ResourcesCompat.getFont(this@EInkWallpaperService, R.font.rubik) ?: Typeface.SANS_SERIF
    }
    private val boldTypeface: Typeface by lazy { Typeface.create(baseTypeface, Typeface.BOLD) }
    private val normalTypeface: Typeface by lazy { Typeface.create(baseTypeface, Typeface.NORMAL) }

    private var lastPaintWidth = -1f
    private var lastPaintColor = 0
    private val statusPaint by lazy {
      Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = boldTypeface
        textAlign = Paint.Align.CENTER
      }
    }
    private val clockPaint by lazy {
      Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = boldTypeface
        textAlign = Paint.Align.CENTER
      }
    }
    private val datePaint by lazy {
      Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = normalTypeface
        textAlign = Paint.Align.CENTER
      }
    }
    private val borderPaint by lazy {
      Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
      }
    }
    private val fillPaint by lazy {
      Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
      }
    }
    private val leftPaint by lazy {
      Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = boldTypeface
        textAlign = Paint.Align.LEFT
      }
    }
    private val rightPaint by lazy {
      Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = boldTypeface
        textAlign = Paint.Align.RIGHT
      }
    }
    private val imgPaint by lazy {
      Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
        colorFilter = monoFilter
      }
    }
    private val badgePaint by lazy {
      Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = boldTypeface
        textAlign = Paint.Align.CENTER
      }
    }
    private val titlePaint by lazy {
      Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = boldTypeface
        textAlign = Paint.Align.LEFT
      }
    }
    private val subPaint by lazy {
      Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = normalTypeface
        textAlign = Paint.Align.LEFT
      }
    }
    private val excerptHeaderPaint by lazy {
      Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = boldTypeface
        textAlign = Paint.Align.LEFT
      }
    }
    private val excerptCounterPaint by lazy {
      Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = boldTypeface
        textAlign = Paint.Align.RIGHT
      }
    }
    private val excerptBodyPaint by lazy {
      TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.SERIF
      }
    }
    private val grainTilePaint = Paint()
    private var grainTileColor: Int = 0
    private var grainTileBitmap: Bitmap? = null

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
            container.lockScreenReading.state,
          ) { _, _, _, _, _ -> Unit }.collect {
            if (visible) drawFrame()
          }
        }
    }

    override fun onVisibilityChanged(visible: Boolean) {
      this.visible = visible
      handler.removeCallbacks(drawTask)
      if (visible) {
        ensureLockScreenNotificationOnLock()
        handler.post(drawTask)
      }
    }

    private fun ensureLockScreenNotificationOnLock() {
      val keyguard = getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager ?: return
      if (!keyguard.isKeyguardLocked) return
      val container = (applicationContext as? EmptyApplication)?.container ?: return
      if (!container.settings.settings.value.lockScreenReader) return
      if (LockScreenReadingNotifier.isDismissedByUser(applicationContext)) return

      val lockState = container.lockScreenReading.state.value
      if (lockState != null && lockState.snippets.isNotEmpty()) {
        val coverPath =
          container.library.books.value
            .firstOrNull { it.id == lockState.bookId }
            ?.let { container.library.coverFileOf(it)?.absolutePath }
        LockScreenReadingNotifier.show(
          context = applicationContext,
          bookId = lockState.bookId,
          title = lockState.title,
          progress = lockState.progress,
          author = lockState.author,
          coverPath = coverPath,
          snippet = lockState.currentSnippet,
          snippetIndex = lockState.currentSnippetIndex,
          snippetCount = lockState.snippets.size,
          locatorJson = lockState.locatorJson,
          locatorCreatedAt = lockState.capturedAt,
        )
        return
      }

      val books = container.library.books.value
      val currentBook = books.mostRecentlyReadBook() ?: return
      LockScreenReadingNotifier.show(
        context = applicationContext,
        bookId = currentBook.id,
        title = currentBook.title,
        progress = currentBook.progress,
        author = currentBook.author,
        coverPath = container.library.coverFileOf(currentBook)?.absolutePath,
      )
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
      handler.removeCallbacks(drawTask)
      coverLoadJob?.cancel()
      observerJob?.cancel()
      engineScope.cancel()
      cachedCoverBitmap?.recycle()
      cachedCoverBitmap = null
      cachedCoverKey = null
      loadingCoverKey = null
      grainTileBitmap?.recycle()
      grainTileBitmap = null
      super.onDestroy()
    }

    private fun drawFrame() {
      val holder = surfaceHolder
      var canvas: Canvas? = null
      try {
        canvas = holder.lockCanvas() ?: return
        renderWallpaper(canvas)
      } catch (_: Throwable) {
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
      val currentBook = books.mostRecentlyReadBook()
      val completedCount = books.count { it.progress >= 0.99f }
      val highlightsCount = container?.annotations?.highlights?.value?.size ?: 0
      val stats = container?.stats?.stats?.value
      val todaySec = stats?.todaySeconds() ?: 0
      val todayMin = todaySec / 60
      val goalMin = (stats?.dailyGoalMinutes ?: 20).coerceAtLeast(5)
      val goalProgress = (todaySec.toFloat() / (goalMin * 60f)).coerceIn(0f, 1f)
      val streak = stats?.currentStreak ?: 0
      val localized = resolveLocalizedContext()
      val dateLocale = cachedDateLocale ?: LocaleHelper.effectiveLocale(applicationContext)
      val languageCode = cachedLanguageCode ?: LocaleHelper.effectiveLanguageCode(applicationContext)
      val rank = resolveRank(stats, completedCount, highlightsCount, localized, languageCode)
      val batteryPct = readBatteryPercent(applicationContext)
      val now = LocalDateTime.now()
      ensurePaints(w, fgColor)

      val rankText =
        rank?.let { localized.getString(R.string.wallpaper_rank_format, it.level, it.title) }
          ?: localized.getString(R.string.app_name)
      val is24Hour = DateFormat.is24HourFormat(applicationContext)
      ensureFormatters(dateLocale, is24Hour)
      canvas.drawText(
        localized.getString(R.string.wallpaper_status_format, batteryPct, streak, rankText),
        w / 2f,
        h * 0.14f,
        statusPaint,
      )
      val formattedTime = now.format(cachedClockFormatter!!)
      canvas.drawText(formattedTime, w / 2f, h * 0.29f, clockPaint)
      canvas.drawText(now.format(cachedDateFormatter!!), w / 2f, h * 0.34f, datePaint)

      // Lock-screen reading excerpt panel pushed to the bottom to stay clear of notifications
      val lockState = container?.lockScreenReading?.state?.value
      val padX = w * 0.09f
      val hasLockExcerpt = settings?.lockScreenReader == true && lockState != null && lockState.currentSnippet.isNotBlank()
      if (hasLockExcerpt) {
        drawReadingExcerptPanel(
          canvas = canvas,
          w = w,
          top = h * 0.61f,
          bottom = h * 0.895f,
          padX = padX,
          fgColor = fgColor,
          borderPaint = borderPaint,
          lockState = lockState,
          localized = localized,
        )
        return
      }

      // Daily goal bar
      val goalTop = h * 0.635f
      val goalBarH = w * 0.022f
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
      val coverBmp = resolveCoverBitmap(currentBook?.id, coverFile)
      if (coverBmp != null) {
        val src = Rect(0, 0, coverBmp.width, coverBmp.height)
        canvas.drawBitmap(coverBmp, src, coverRect, imgPaint)
      } else {
        canvas.drawText(
          currentBook?.format?.label ?: localized.getString(com.example.emptyapp.R.string.book_badge_label),
          coverRect.centerX(),
          coverRect.centerY() + w * 0.01f,
          badgePaint,
        )
      }

      val textX = coverRect.right + w * 0.04f
      val maxTextW = (w - padX - innerPad) - textX

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

    private fun ensurePaints(w: Float, fgColor: Int) {
      if (w == lastPaintWidth && fgColor == lastPaintColor) return
      lastPaintWidth = w
      lastPaintColor = fgColor
      statusPaint.apply {
        color = fgColor
        alpha = 175
        textSize = w * 0.033f
      }
      clockPaint.apply {
        color = fgColor
        textSize = w * 0.21f
      }
      datePaint.apply {
        color = fgColor
        alpha = 185
        textSize = w * 0.042f
      }
      borderPaint.apply {
        color = fgColor
        strokeWidth = (w * 0.004f).coerceAtLeast(2f)
      }
      fillPaint.color = fgColor
      leftPaint.apply {
        color = fgColor
        alpha = 175
        textSize = w * 0.032f
      }
      rightPaint.apply {
        color = fgColor
        alpha = 175
        textSize = w * 0.032f
      }
      badgePaint.apply {
        color = fgColor
        alpha = 175
        textSize = w * 0.028f
      }
      titlePaint.apply {
        color = fgColor
        textSize = w * 0.044f
      }
      subPaint.apply {
        color = fgColor
        alpha = 180
        textSize = w * 0.034f
      }
      excerptHeaderPaint.apply {
        color = fgColor
        textSize = w * 0.032f
      }
      excerptCounterPaint.apply {
        color = fgColor
        alpha = 180
        textSize = w * 0.032f
      }
      excerptBodyPaint.apply {
        color = fgColor
        textSize = w * 0.037f
      }
    }

    private fun resolveLocalizedContext(): Context {
      val appLang = LocaleHelper.getSavedLanguage(applicationContext)
      val sysTag = (Resources.getSystem().configuration.locales[0] ?: Locale.getDefault()).toLanguageTag()
      val key = WallpaperLocaleKey(appLang, sysTag)
      val existing = cachedLocalizedContext
      if (key == cachedLocaleKey && existing != null) return existing
      val wrapped = LocaleHelper.wrap(applicationContext)
      cachedLocaleKey = key
      cachedLocalizedContext = wrapped
      cachedDateLocale = LocaleHelper.effectiveLocale(applicationContext)
      cachedLanguageCode = LocaleHelper.effectiveLanguageCode(applicationContext)
      return wrapped
    }

    private fun ensureFormatters(dateLocale: Locale, is24Hour: Boolean) {
      val key = WallpaperFormatterKey(dateLocale.toLanguageTag(), is24Hour)
      if (key == cachedFormatterKey && cachedDateFormatter != null && cachedClockFormatter != null) return
      cachedFormatterKey = key
      cachedDateFormatter = DateTimeFormatter.ofPattern("EEEE, dd/MM/yyyy", dateLocale)
      val clockPattern = if (is24Hour) "HH:mm" else "h:mm a"
      cachedClockFormatter = DateTimeFormatter.ofPattern(clockPattern, dateLocale)
    }

    private fun resolveRank(
      stats: ReadingStatsData?,
      completedCount: Int,
      highlightsCount: Int,
      localized: Context,
      languageCode: String,
    ): ReaderRankInfo? {
      if (stats == null) {
        cachedRankKey = null
        cachedRankInfo = null
        return null
      }
      val key =
        WallpaperRankKey(
          totalSeconds = stats.totalSeconds,
          totalPagesTurned = stats.totalPagesTurned,
          completedBooksCount = completedCount,
          highlightsCount = highlightsCount,
          languageCode = languageCode,
        )
      if (key == cachedRankKey && cachedRankInfo != null) return cachedRankInfo
      val computed = ReadingGamification.rankFor(stats, completedCount, highlightsCount, localized)
      cachedRankKey = key
      cachedRankInfo = computed
      return computed
    }

    private fun drawPaperGrain(
      canvas: Canvas,
      w: Float,
      h: Float,
      fgColor: Int,
    ) {
      if (grainTileBitmap == null || grainTileColor != fgColor) {
        grainTileBitmap?.recycle()
        val tileSize = 256
        val tile = Bitmap.createBitmap(tileSize, tileSize, Bitmap.Config.ARGB_8888)
        val tileCanvas = Canvas(tile)
        val dotPaint =
          Paint().apply {
            color = fgColor
            alpha = 18
            strokeWidth = 2f
          }
        var seed = 0x5DEECE66DL
        val step = 28
        var y = step / 2
        while (y < tileSize) {
          var x = step / 2
          while (x < tileSize) {
            seed = (seed * 0x5DEECE66DL + 0xBL) and 0xFFFFFFFFFFFFL
            if ((seed and 3L) == 0L) {
              val ox = ((seed shr 4) % 7).toFloat() - 3f
              val oy = ((seed shr 8) % 7).toFloat() - 3f
              tileCanvas.drawPoint(x + ox, y + oy, dotPaint)
            }
            x += step
          }
          y += step
        }
        grainTileBitmap = tile
        grainTileColor = fgColor
        grainTilePaint.shader = BitmapShader(tile, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)
      }
      canvas.drawRect(0f, 0f, w, h, grainTilePaint)
    }

    private fun resolveCoverBitmap(bookId: String?, file: File?): Bitmap? {
      val path = file?.takeIf { it.exists() }?.absolutePath
      if (bookId == null || path == null) {
        coverLoadJob?.cancel()
        loadingCoverKey = null
        cachedCoverKey = null
        cachedCoverBitmap?.recycle()
        cachedCoverBitmap = null
        return null
      }
      val key = WallpaperCoverKey(bookId, path)
      if (key == cachedCoverKey && cachedCoverBitmap != null) return cachedCoverBitmap
      if (key != loadingCoverKey) {
        coverLoadJob?.cancel()
        loadingCoverKey = key
        coverLoadJob =
          engineScope.launch(Dispatchers.IO) {
            val decoded =
              runCatching {
                val opts =
                  BitmapFactory.Options().apply {
                    inSampleSize = 2
                    inPreferredConfig = Bitmap.Config.RGB_565
                  }
                BitmapFactory.decodeFile(path, opts)
              }.getOrNull()
            withContext(Dispatchers.Main.immediate) {
              if (loadingCoverKey != key) {
                decoded?.recycle()
                return@withContext
              }
              loadingCoverKey = null
              if (decoded !== cachedCoverBitmap) {
                cachedCoverBitmap?.recycle()
              }
              cachedCoverBitmap = decoded
              cachedCoverKey = if (decoded != null) key else null
              if (visible && decoded != null) {
                drawFrame()
              }
            }
          }
      }
      return null
    }

    private fun ellipsize(text: String, paint: Paint, maxWidth: Float): String {
      if (paint.measureText(text) <= maxWidth) return text
      var end = text.length
      while (end > 1 && paint.measureText(text.substring(0, end) + "…") > maxWidth) {
        end -= 1
      }
      return text.substring(0, end) + "…"
    }

    private fun drawReadingExcerptPanel(
      canvas: Canvas,
      w: Float,
      top: Float,
      bottom: Float,
      padX: Float,
      fgColor: Int,
      borderPaint: Paint,
      lockState: com.example.emptyapp.reader.LockScreenReadingState,
      localized: Context,
    ) {
      val right = w - padX
      val innerPad = w * 0.04f
      canvas.drawRect(padX, top, right, bottom, borderPaint)

      val pct = (lockState.progress.coerceIn(0f, 1f) * 100).toInt()
      val counterText =
        if (lockState.snippets.isNotEmpty()) {
          localized.getString(
            R.string.lockscreen_snippet_counter,
            lockState.currentSnippetIndex + 1,
            lockState.snippets.size,
            pct,
          )
        } else {
          "$pct%"
        }
      val counterW = excerptCounterPaint.measureText(counterText)
      val titleMaxW = ((right - innerPad) - (padX + innerPad) - counterW - w * 0.03f).coerceAtLeast(40f)
      val headerY = top + innerPad + w * 0.03f
      canvas.drawText(ellipsize(lockState.title, excerptHeaderPaint, titleMaxW), padX + innerPad, headerY, excerptHeaderPaint)
      canvas.drawText(counterText, right - innerPad, headerY, excerptCounterPaint)

      val dividerY = headerY + w * 0.022f
      canvas.drawLine(padX + innerPad, dividerY, right - innerPad, dividerY, borderPaint)

      val bodyTop = dividerY + w * 0.025f
      val bodyMaxH = (bottom - innerPad - bodyTop).coerceAtLeast(20f)
      val bodyWidth = ((right - innerPad) - (padX + innerPad)).toInt().coerceAtLeast(40)
      val layout = resolveExcerptLayout(lockState.currentSnippet, bodyWidth, w, fgColor)

      canvas.save()
      canvas.clipRect(padX + innerPad, bodyTop, right - innerPad, bodyTop + bodyMaxH)
      canvas.translate(padX + innerPad, bodyTop)
      layout.draw(canvas)
      canvas.restore()
    }

    private fun resolveExcerptLayout(
      snippet: String,
      bodyWidth: Int,
      widthPx: Float,
      fgColor: Int,
    ): StaticLayout {
      val key = WallpaperExcerptLayoutKey(snippet, bodyWidth, widthPx, fgColor)
      val existing = cachedExcerptLayout
      if (key == cachedExcerptKey && existing != null) return existing
      val built =
        StaticLayout.Builder
          .obtain(snippet, 0, snippet.length, excerptBodyPaint, bodyWidth)
          .setAlignment(Layout.Alignment.ALIGN_NORMAL)
          .setLineSpacing(0f, 1.25f)
          .build()
      cachedExcerptKey = key
      cachedExcerptLayout = built
      return built
    }

    private fun readBatteryPercent(context: Context): Int {
      val manager = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager ?: return 100
      val pct = manager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
      return if (pct in 0..100) pct else 100
    }
  }
}
