package com.example.emptyapp.data.stats

import android.content.Context
import com.example.emptyapp.R

data class ReaderRankInfo(
  val level: Int,
  val title: String,
  val xp: Int,
  val currentFloorXp: Int,
  val nextLevelXp: Int?,
  val nextLevelTitle: String?,
) {
  val progressToNext: Float
    get() {
      val target = nextLevelXp ?: return 1f
      val span = (target - currentFloorXp).coerceAtLeast(1)
      return ((xp - currentFloorXp).toFloat() / span.toFloat()).coerceIn(0f, 1f)
    }
}

data class ReadingBadge(
  val id: String,
  val title: String,
  val description: String,
  val unlocked: Boolean,
)

object ReadingGamification {
  private data class RankTier(val level: Int, val minXp: Int, val titleRes: Int, val fallback: String)

  private val TIERS =
    listOf(
      RankTier(1, 0, R.string.rank_1, "Tân binh"),
      RankTier(2, 300, R.string.rank_2, "Độc giả"),
      RankTier(3, 1_000, R.string.rank_3, "Mọt sách"),
      RankTier(4, 2_500, R.string.rank_4, "Học giả"),
      RankTier(5, 6_000, R.string.rank_5, "Triết gia"),
    )

  private fun str(context: Context?, resId: Int, fallback: String): String =
    context?.getString(resId) ?: fallback

  fun computeXp(
    stats: ReadingStatsData,
    completedBooksCount: Int,
    highlightsCount: Int,
  ): Int {
    val minuteXp = (stats.totalSeconds / 60L).toInt() * 10
    val pageXp = stats.totalPagesTurned * 2
    val bookXp = completedBooksCount * 100
    val highlightXp = highlightsCount * 25
    return minuteXp + pageXp + bookXp + highlightXp
  }

  fun rankFor(
    stats: ReadingStatsData,
    completedBooksCount: Int,
    highlightsCount: Int,
    context: Context? = null,
  ): ReaderRankInfo {
    val xp = computeXp(stats, completedBooksCount, highlightsCount)
    val tierIndex = TIERS.indexOfLast { xp >= it.minXp }.coerceAtLeast(0)
    val current = TIERS[tierIndex]
    val next = TIERS.getOrNull(tierIndex + 1)
    return ReaderRankInfo(
      level = current.level,
      title = str(context, current.titleRes, current.fallback),
      xp = xp,
      currentFloorXp = current.minXp,
      nextLevelXp = next?.minXp,
      nextLevelTitle = next?.let { str(context, it.titleRes, it.fallback) },
    )
  }

  fun badgesFor(
    stats: ReadingStatsData,
    completedBooksCount: Int,
    highlightsCount: Int,
    context: Context? = null,
  ): List<ReadingBadge> =
    listOf(
      ReadingBadge(
        id = "first_page",
        title = str(context, R.string.badge_first_page_title, "Trang đầu tiên"),
        description = str(context, R.string.badge_first_page_desc, "Đọc ít nhất 1 phút"),
        unlocked = stats.totalSeconds >= 60L,
      ),
      ReadingBadge(
        id = "streak_3",
        title = str(context, R.string.badge_streak_3_title, "Nhịp đều 3 ngày"),
        description = str(context, R.string.badge_streak_3_desc, "Chuỗi 3 ngày liên tiếp"),
        unlocked = stats.longestStreak >= 3,
      ),
      ReadingBadge(
        id = "streak_7",
        title = str(context, R.string.badge_streak_7_title, "Tuần lễ vàng"),
        description = str(context, R.string.badge_streak_7_desc, "Chuỗi 7 ngày liên tiếp"),
        unlocked = stats.longestStreak >= 7,
      ),
      ReadingBadge(
        id = "streak_30",
        title = str(context, R.string.badge_streak_30_title, "Kỷ luật thép"),
        description = str(context, R.string.badge_streak_30_desc, "Chuỗi 30 ngày liên tiếp"),
        unlocked = stats.longestStreak >= 30,
      ),
      ReadingBadge(
        id = "pages_100",
        title = str(context, R.string.badge_pages_100_title, "Trăm trang sách"),
        description = str(context, R.string.badge_pages_100_desc, "Lật ít nhất 100 trang"),
        unlocked = stats.totalPagesTurned >= 100,
      ),
      ReadingBadge(
        id = "hours_5",
        title = str(context, R.string.badge_hours_5_title, "5 giờ nghiền ngẫm"),
        description = str(context, R.string.badge_hours_5_desc, "Tổng thời gian đọc ≥ 5 giờ"),
        unlocked = stats.totalSeconds >= 5L * 3600L,
      ),
      ReadingBadge(
        id = "book_done_1",
        title = str(context, R.string.badge_book_done_title, "Khép trang cuối"),
        description = str(context, R.string.badge_book_done_desc, "Hoàn thành 1 cuốn sách"),
        unlocked = completedBooksCount >= 1,
      ),
      ReadingBadge(
        id = "annotator",
        title = str(context, R.string.badge_annotator_title, "Người ghi chú"),
        description = str(context, R.string.badge_annotator_desc, "Tạo ít nhất 5 highlights"),
        unlocked = highlightsCount >= 5,
      ),
    )
}
