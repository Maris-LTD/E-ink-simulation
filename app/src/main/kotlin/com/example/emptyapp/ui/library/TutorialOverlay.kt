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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.emptyapp.R
import com.example.emptyapp.ui.eink.EInkText
import kotlinx.coroutines.launch

private data class TutorialStep(
  val tagRes: Int,
  val titleRes: Int,
  val bullets: List<Pair<Int, Int>>,
)

private val TUTORIAL_STEPS =
  listOf(
    TutorialStep(
      tagRes = R.string.tutorial_s1_tag,
      titleRes = R.string.tutorial_s1_title,
      bullets =
        listOf(
          R.string.tutorial_s1_b1_t to R.string.tutorial_s1_b1_d,
          R.string.tutorial_s1_b2_t to R.string.tutorial_s1_b2_d,
          R.string.tutorial_s1_b3_t to R.string.tutorial_s1_b3_d,
        ),
    ),
    TutorialStep(
      tagRes = R.string.tutorial_s2_tag,
      titleRes = R.string.tutorial_s2_title,
      bullets =
        listOf(
          R.string.tutorial_s2_b1_t to R.string.tutorial_s2_b1_d,
          R.string.tutorial_s2_b2_t to R.string.tutorial_s2_b2_d,
          R.string.tutorial_s2_b3_t to R.string.tutorial_s2_b3_d,
        ),
    ),
    TutorialStep(
      tagRes = R.string.tutorial_s3_tag,
      titleRes = R.string.tutorial_s3_title,
      bullets =
        listOf(
          R.string.tutorial_s3_b1_t to R.string.tutorial_s3_b1_d,
          R.string.tutorial_s3_b2_t to R.string.tutorial_s3_b2_d,
          R.string.tutorial_s3_b3_t to R.string.tutorial_s3_b3_d,
        ),
    ),
    TutorialStep(
      tagRes = R.string.tutorial_s4_tag,
      titleRes = R.string.tutorial_s4_title,
      bullets =
        listOf(
          R.string.tutorial_s4_b1_t to R.string.tutorial_s4_b1_d,
          R.string.tutorial_s4_b2_t to R.string.tutorial_s4_b2_d,
          R.string.tutorial_s4_b3_t to R.string.tutorial_s4_b3_d,
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
        EInkText(stringResource(R.string.tutorial_header), style = MaterialTheme.typography.titleMedium)
        TextButton(onClick = onDismiss) {
          EInkText(stringResource(R.string.tutorial_skip), color = MaterialTheme.colorScheme.secondary)
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
            text = stringResource(step.tagRes),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.secondary,
          )
          EInkText(
            text = stringResource(step.titleRes),
            style = MaterialTheme.typography.titleLarge,
          )
          step.bullets.forEach { (headingRes, bodyRes) ->
            Column(
              modifier =
                Modifier.fillMaxWidth()
                  .border(1.dp, MaterialTheme.colorScheme.outlineVariant)
                  .padding(12.dp),
              verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
              EInkText(text = "• ${stringResource(headingRes)}", style = MaterialTheme.typography.titleSmall)
              EInkText(
                text = stringResource(bodyRes),
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
            EInkText(stringResource(R.string.tutorial_prev))
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
          EInkText(if (isLast) stringResource(R.string.tutorial_start) else stringResource(R.string.tutorial_next))
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
