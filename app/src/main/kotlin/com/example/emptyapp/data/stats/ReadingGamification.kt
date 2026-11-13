package com.example.emptyapp.data.stats

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
  private data class RankTier(val level: Int, val minXp: Int, val title: String)

  private val TIERS =
    listOf(
      RankTier(1, 0, "Tân binh"),
      RankTier(2, 300, "Độc giả"),
      RankTier(3, 1_000, "Mọt sách"),
      RankTier(4, 2_500, "Học giả"),
      RankTier(5, 6_000, "Triết gia"),
    )

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
  ): ReaderRankInfo {
    val xp = computeXp(stats, completedBooksCount, highlightsCount)
    val tierIndex = TIERS.indexOfLast { xp >= it.minXp }.coerceAtLeast(0)
    val current = TIERS[tierIndex]
    val next = TIERS.getOrNull(tierIndex + 1)
    return ReaderRankInfo(
      level = current.level,
      title = current.title,
      xp = xp,
      currentFloorXp = current.minXp,
      nextLevelXp = next?.minXp,
      nextLevelTitle = next?.title,
    )
  }

  fun badgesFor(
    stats: ReadingStatsData,
    completedBooksCount: Int,
    highlightsCount: Int,
  ): List<ReadingBadge> =
    listOf(
      ReadingBadge(
        id = "first_page",
        title = "Trang đầu tiên",
        description = "Đọc ít nhất 1 phút",
        unlocked = stats.totalSeconds >= 60L,
      ),
      ReadingBadge(
        id = "streak_3",
        title = "Nhịp đều 3 ngày",
        description = "Chuỗi 3 ngày liên tiếp",
        unlocked = stats.longestStreak >= 3,
      ),
      ReadingBadge(
        id = "streak_7",
        title = "Tuần lễ vàng",
        description = "Chuỗi 7 ngày liên tiếp",
        unlocked = stats.longestStreak >= 7,
      ),
      ReadingBadge(
        id = "streak_30",
        title = "Kỷ luật thép",
        description = "Chuỗi 30 ngày liên tiếp",
        unlocked = stats.longestStreak >= 30,
      ),
      ReadingBadge(
        id = "pages_100",
        title = "Trăm trang sách",
        description = "Lật ít nhất 100 trang",
        unlocked = stats.totalPagesTurned >= 100,
      ),
      ReadingBadge(
        id = "hours_5",
        title = "5 giờ nghiền ngẫm",
        description = "Tổng thời gian đọc ≥ 5 giờ",
        unlocked = stats.totalSeconds >= 5L * 3600L,
      ),
      ReadingBadge(
        id = "book_done_1",
        title = "Khép trang cuối",
        description = "Hoàn thành 1 cuốn sách",
        unlocked = completedBooksCount >= 1,
      ),
      ReadingBadge(
        id = "annotator",
        title = "Người ghi chú",
        description = "Tạo ít nhất 5 highlights",
        unlocked = highlightsCount >= 5,
      ),
    )
}
