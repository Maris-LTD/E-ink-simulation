package com.example.emptyapp.ui.library

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.example.emptyapp.ui.eink.EInkText
import kotlinx.coroutines.launch

private data class TutorialStep(
  val tag: String,
  val title: String,
  val bullets: List<Pair<String, String>>,
)

private val TUTORIAL_STEPS =
  listOf(
    TutorialStep(
      tag = "BƯỚC 1 / 4 · THƯ VIỆN",
      title = "Nhập sách từ máy & Google Drive",
      bullets =
        listOf(
          "Thêm sách nhanh" to "Chạm nút (+) ở góc dưới phải để nhập tệp EPUB, PDF hoặc CBZ từ bộ nhớ máy.",
          "Mở từ Google Drive" to "Trong trình chọn tệp, bấm menu ≡ (góc trên trái) → chọn Drive để tải sách trực tiếp về đọc offline.",
          "Lọc & Sắp xếp" to "Đổi kiểu xem Danh sách / Lưới 2-3-4 cột, lọc theo tác giả hoặc tìm trong Dấu trang & Highlights.",
        ),
    ),
    TutorialStep(
      tag = "BƯỚC 2 / 4 · ĐỌC SÁCH",
      title = "Cử chỉ đọc & Focus Mode",
      bullets =
        listOf(
          "Lật trang & Công cụ" to "Chạm mép trái/phải (hoặc phím âm lượng) để lật trang; chạm giữa màn hình để mở thanh công cụ.",
          "Độ sáng & Ghi chú" to "Vuốt dọc mép trái để chỉnh nhanh độ sáng; nhấn giữ chọn chữ để Highlight, Ghi chú hoặc Tra từ điển.",
          "Focus Mode" to "Bật nút Focus trên thanh công cụ để ẩn mọi thanh hệ thống, giữ sáng màn hình và tự bật Không làm phiền (DND).",
        ),
    ),
    TutorialStep(
      tag = "BƯỚC 3 / 4 · HỆ THỐNG E-INK",
      title = "Launcher, Màn hình khóa & Hình nền",
      bullets =
        listOf(
          "E-Ink Launcher" to "Đặt app làm Màn hình chính mặc định để dùng giao diện tối giản đen trắng; vuốt sang phải mở Ngăn ứng dụng.",
          "Ghim & Chế độ xem" to "Ở Ngăn ứng dụng, đổi giữa Danh sách / Lưới 3-4 / Chỉ chữ và nhấn giữ app để ghim ra Home.",
          "Màn hình khóa & Wallpaper" to "Bật 'Đọc trên màn hình khóa' trong Cài đặt đọc, hoặc đặt Hình nền động E-Ink trong Hồ sơ & Thống kê.",
        ),
    ),
    TutorialStep(
      tag = "BƯỚC 4 / 4 · ĐỒNG BỘ",
      title = "Đăng nhập Google & Đồng bộ đa máy",
      bullets =
        listOf(
          "Mở Hồ sơ & Thống kê" to "Chạm biểu tượng Hồ sơ ở góc trên phải (hoặc vuốt sang phải từ Thư viện).",
          "Đăng nhập Google (0đ)" to "Bấm 'Đăng nhập với Google' để tự động lưu tiến độ đọc, dấu trang, highlight và chuỗi ngày đọc lên Drive AppData.",
          "Đồng bộ cả tủ sách" to "Bật 'Đồng bộ cả file sách' để sang máy khác chỉ cần đăng nhập là tự tải lại toàn bộ sách và trang đang đọc.",
        ),
    ),
  )

@Composable
fun TutorialOverlay(
  onDismiss: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val pagerState = rememberPagerState(pageCount = { TUTORIAL_STEPS.size })
  val scope = rememberCoroutineScope()

  BackHandler(onBack = onDismiss)

  Box(
    modifier =
      modifier
        .fillMaxSize()
        .background(MaterialTheme.colorScheme.background)
        .padding(16.dp),
  ) {
    Column(
      modifier =
        Modifier.fillMaxSize()
          .border(2.dp, MaterialTheme.colorScheme.outline)
          .background(MaterialTheme.colorScheme.background),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        EInkText("Hướng dẫn sử dụng", style = MaterialTheme.typography.titleMedium)
        TextButton(onClick = onDismiss) {
          EInkText("Bỏ qua", color = MaterialTheme.colorScheme.secondary)
        }
      }
      HorizontalDivider(thickness = 1.5.dp, color = MaterialTheme.colorScheme.outline)

      HorizontalPager(
        state = pagerState,
        beyondViewportPageCount = 1,
        modifier = Modifier.weight(1f).fillMaxWidth(),
      ) { pageIndex ->
        val step = TUTORIAL_STEPS[pageIndex]
        Column(
          modifier =
            Modifier.fillMaxSize()
              .verticalScroll(rememberScrollState())
              .padding(horizontal = 18.dp, vertical = 16.dp),
          verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
          TutorialIllustration(
            pageIndex = pageIndex,
            modifier =
              Modifier.fillMaxWidth()
                .height(120.dp)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant),
          )
          EInkText(
            text = step.tag,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.secondary,
          )
          EInkText(
            text = step.title,
            style = MaterialTheme.typography.titleLarge,
          )
          step.bullets.forEach { (heading, body) ->
            Column(
              modifier =
                Modifier.fillMaxWidth()
                  .border(1.dp, MaterialTheme.colorScheme.outlineVariant)
                  .padding(12.dp),
              verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
              EInkText(text = "• $heading", style = MaterialTheme.typography.titleSmall)
              EInkText(
                text = body,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.secondary,
              )
            }
          }
        }
      }

      HorizontalDivider(thickness = 1.5.dp, color = MaterialTheme.colorScheme.outline)
      Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        if (pagerState.currentPage > 0) {
          OutlinedButton(
            onClick = { scope.launch { pagerState.animateScrollToPage(pagerState.currentPage - 1) } },
          ) {
            EInkText("‹ Trước")
          }
        } else {
          Spacer(modifier = Modifier.width(84.dp))
        }

        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
          repeat(TUTORIAL_STEPS.size) { index ->
            val active = index == pagerState.currentPage
            Box(
              modifier =
                Modifier.size(width = if (active) 18.dp else 8.dp, height = 8.dp)
                  .background(
                    if (active) MaterialTheme.colorScheme.onBackground
                    else MaterialTheme.colorScheme.outlineVariant,
                  )
                  .clickable { scope.launch { pagerState.animateScrollToPage(index) } },
            )
          }
        }

        val isLast = pagerState.currentPage == TUTORIAL_STEPS.lastIndex
        OutlinedButton(
          onClick = {
            if (isLast) onDismiss()
            else scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
          },
        ) {
          EInkText(if (isLast) "Bắt đầu đọc" else "Tiếp ›")
        }
      }
    }
  }
}

@Composable
private fun TutorialIllustration(pageIndex: Int, modifier: Modifier = Modifier) {
  val ink = MaterialTheme.colorScheme.onBackground
  val muted = MaterialTheme.colorScheme.outlineVariant
  Canvas(modifier = modifier) {
    val stroke = 2.dp.toPx()
    val cx = size.width / 2f
    val cy = size.height / 2f
    when (pageIndex) {
      0 -> {
        val bw = 56.dp.toPx()
        val bh = 74.dp.toPx()
        drawRect(ink, Offset(cx - bw / 2f, cy - bh / 2f), Size(bw, bh), style = Stroke(stroke))
        drawLine(ink, Offset(cx - bw / 2f + 10.dp.toPx(), cy - bh / 2f), Offset(cx - bw / 2f + 10.dp.toPx(), cy + bh / 2f), strokeWidth = stroke)
        drawLine(muted, Offset(cx - 8.dp.toPx(), cy - 10.dp.toPx()), Offset(cx + 18.dp.toPx(), cy - 10.dp.toPx()), strokeWidth = stroke)
        drawLine(muted, Offset(cx - 8.dp.toPx(), cy + 2.dp.toPx()), Offset(cx + 14.dp.toPx(), cy + 2.dp.toPx()), strokeWidth = stroke)
        val pr = 14.dp.toPx()
        val pc = Offset(cx + bw / 2f + 18.dp.toPx(), cy + bh / 2f - 12.dp.toPx())
        drawCircle(ink, radius = pr, center = pc, style = Stroke(stroke))
        drawLine(ink, Offset(pc.x - 6.dp.toPx(), pc.y), Offset(pc.x + 6.dp.toPx(), pc.y), strokeWidth = stroke, cap = StrokeCap.Round)
        drawLine(ink, Offset(pc.x, pc.y - 6.dp.toPx()), Offset(pc.x, pc.y + 6.dp.toPx()), strokeWidth = stroke, cap = StrokeCap.Round)
      }
      1 -> {
        val pw = 66.dp.toPx()
        val ph = 84.dp.toPx()
        val left = cx - pw / 2f
        val top = cy - ph / 2f
        drawRect(ink, Offset(left, top), Size(pw, ph), style = Stroke(stroke))
        drawLine(muted, Offset(left + pw * 0.28f, top + 8.dp.toPx()), Offset(left + pw * 0.28f, top + ph - 8.dp.toPx()), strokeWidth = 1.dp.toPx())
        drawLine(muted, Offset(left + pw * 0.72f, top + 8.dp.toPx()), Offset(left + pw * 0.72f, top + ph - 8.dp.toPx()), strokeWidth = 1.dp.toPx())
        drawCircle(ink, radius = 8.dp.toPx(), center = Offset(cx, cy), style = Stroke(stroke))
      }
      2 -> {
        val pw = 62.dp.toPx()
        val ph = 84.dp.toPx()
        val left = cx - pw / 2f
        val top = cy - ph / 2f
        drawRoundRect(ink, Offset(left, top), Size(pw, ph), CornerRadius(6.dp.toPx()), style = Stroke(stroke))
        val cell = 10.dp.toPx()
        val gap = 6.dp.toPx()
        val startX = cx - cell - gap / 2f
        val startY = cy - cell - gap / 2f
        for (r in 0..1) {
          for (c in 0..1) {
            drawRect(ink, Offset(startX + c * (cell + gap), startY + r * (cell + gap)), Size(cell, cell), style = Stroke(1.5.dp.toPx()))
          }
        }
      }
      else -> {
        val d = 34.dp.toPx()
        drawRect(ink, Offset(cx - d - 10.dp.toPx(), cy - 26.dp.toPx()), Size(32.dp.toPx(), 52.dp.toPx()), style = Stroke(stroke))
        drawRect(ink, Offset(cx + 12.dp.toPx(), cy - 26.dp.toPx()), Size(32.dp.toPx(), 52.dp.toPx()), style = Stroke(stroke))
        drawLine(ink, Offset(cx - 6.dp.toPx(), cy - 6.dp.toPx()), Offset(cx + 8.dp.toPx(), cy - 6.dp.toPx()), strokeWidth = stroke, cap = StrokeCap.Round)
        drawLine(ink, Offset(cx - 6.dp.toPx(), cy + 6.dp.toPx()), Offset(cx + 8.dp.toPx(), cy + 6.dp.toPx()), strokeWidth = stroke, cap = StrokeCap.Round)
      }
    }
  }
}
