package com.example.emptyapp.data.stats

import android.content.Context
import com.example.emptyapp.data.storage.AtomicJsonFile
import java.io.File
import java.time.LocalDate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable

@Serializable
data class DailyReadingStat(
  val date: String,
  val secondsRead: Int = 0,
  val pagesTurned: Int = 0,
)

@Serializable
data class ReadingStatsData(
  val dailyGoalMinutes: Int = 20,
  val dailyGoalUpdatedAt: Long = 0L,
  val currentStreak: Int = 0,
  val longestStreak: Int = 0,
  val totalSeconds: Long = 0L,
  val totalPagesTurned: Int = 0,
  val dailyStats: List<DailyReadingStat> = emptyList(),
) {
  fun todaySeconds(today: String = LocalDate.now().toString()): Int =
    dailyStats.firstOrNull { it.date == today }?.secondsRead ?: 0
}

class ReadingStatsRepository internal constructor(
  private val statsFile: File,
  private val todayProvider: () -> LocalDate = { LocalDate.now() },
  private val nowProvider: () -> Long = { System.currentTimeMillis() },
  preloadInBackground: Boolean,
  private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
  constructor(
    statsFile: File,
    todayProvider: () -> LocalDate = { LocalDate.now() },
    nowProvider: () -> Long = { System.currentTimeMillis() },
  ) : this(
    statsFile = statsFile,
    todayProvider = todayProvider,
    nowProvider = nowProvider,
    preloadInBackground = false,
    ioDispatcher = Dispatchers.IO,
  )

  constructor(context: Context) : this(
    statsFile = File(context.applicationContext.filesDir, "reading_stats.json"),
    todayProvider = { LocalDate.now() },
    nowProvider = { System.currentTimeMillis() },
    preloadInBackground = true,
    ioDispatcher = Dispatchers.IO,
  )

  private val store = AtomicJsonFile(statsFile, ReadingStatsData.serializer())
  private val mutex = Mutex()
  private val backgroundScope = CoroutineScope(SupervisorJob() + ioDispatcher)
  @Volatile private var dirty = false
  @Volatile private var isLoaded = !preloadInBackground
  private val mutableLoaded = MutableStateFlow(!preloadInBackground)
  private val mutableLastWriteError = MutableStateFlow(false)
  private val state =
    MutableStateFlow(if (preloadInBackground) ReadingStatsData() else recompute(readInitial()))

  val stats: StateFlow<ReadingStatsData> = state.asStateFlow()
  val loaded: StateFlow<Boolean> = mutableLoaded.asStateFlow()
  val lastWriteError: StateFlow<Boolean> = mutableLastWriteError.asStateFlow()

  init {
    if (preloadInBackground) {
      backgroundScope.launch {
        awaitLoaded()
      }
    }
  }

  suspend fun awaitLoaded() {
    if (isLoaded) return
    withContext(ioDispatcher) {
      mutex.withLock {
        ensureLoadedLocked()
      }
    }
  }

  private fun ensureLoadedLocked() {
    if (isLoaded) return
    state.value = recompute(readInitial())
    isLoaded = true
    mutableLoaded.value = true
  }

  suspend fun recordReading(deltaSeconds: Int, deltaPages: Int = 0, syncToDisk: Boolean = true) {
    if (deltaSeconds <= 0 && deltaPages <= 0 && !dirty) return
    val safeSeconds = deltaSeconds.coerceAtLeast(0)
    val safePages = deltaPages.coerceAtLeast(0)
    val today = todayProvider()
    val todayKey = today.toString()
    mutex.withLock {
      if (!isLoaded) {
        withContext(ioDispatcher) { ensureLoadedLocked() }
      }
      if (safeSeconds == 0 && safePages == 0) {
        persistLocked(state.value, syncToDisk = syncToDisk)
        return
      }
      val current = state.value
      val updatedList = current.dailyStats.toMutableList()
      val existingIndex = updatedList.indexOfFirst { it.date == todayKey }
      if (existingIndex >= 0) {
        val old = updatedList[existingIndex]
        updatedList[existingIndex] =
          old.copy(
            secondsRead = old.secondsRead + safeSeconds,
            pagesTurned = old.pagesTurned + safePages,
          )
      } else {
        updatedList += DailyReadingStat(date = todayKey, secondsRead = safeSeconds, pagesTurned = safePages)
      }
      val next =
        recompute(
          current.copy(
            totalSeconds = current.totalSeconds + safeSeconds,
            totalPagesTurned = current.totalPagesTurned + safePages,
            dailyStats = updatedList,
          ),
          today = today,
        )
      persistLocked(next, syncToDisk = syncToDisk)
    }
  }

  suspend fun setDailyGoalMinutes(minutes: Int) {
    val clamped = minutes.coerceIn(5, 180)
    val today = todayProvider()
    val updatedAt = nowProvider()
    mutex.withLock {
      if (!isLoaded) {
        withContext(ioDispatcher) { ensureLoadedLocked() }
      }
      val next =
        recompute(
          state.value.copy(dailyGoalMinutes = clamped, dailyGoalUpdatedAt = updatedAt),
          today = today,
        )
      persistLocked(next)
    }
  }

  suspend fun mergeFromRemote(remote: ReadingStatsData) {
    val today = todayProvider()
    mutex.withLock {
      if (!isLoaded) {
        withContext(ioDispatcher) { ensureLoadedLocked() }
      }
      val local = state.value
      val byDate = LinkedHashMap<String, DailyReadingStat>()
      for (item in local.dailyStats) byDate[item.date] = item
      for (item in remote.dailyStats) {
        val prev = byDate[item.date]
        byDate[item.date] =
          if (prev == null) item
          else DailyReadingStat(
            date = item.date,
            secondsRead = maxOf(prev.secondsRead, item.secondsRead),
            pagesTurned = maxOf(prev.pagesTurned, item.pagesTurned),
          )
      }
      val mergedDaily = byDate.values.sortedBy { it.date }
      val totalSec = maxOf(local.totalSeconds, remote.totalSeconds, mergedDaily.sumOf { it.secondsRead.toLong() })
      val totalPages = maxOf(local.totalPagesTurned, remote.totalPagesTurned, mergedDaily.sumOf { it.pagesTurned })
      val (mergedGoal, mergedGoalUpdatedAt) =
        when {
          remote.dailyGoalUpdatedAt > local.dailyGoalUpdatedAt ->
            remote.dailyGoalMinutes.coerceIn(5, 180) to remote.dailyGoalUpdatedAt
          local.dailyGoalUpdatedAt > remote.dailyGoalUpdatedAt ->
            local.dailyGoalMinutes.coerceIn(5, 180) to local.dailyGoalUpdatedAt
          else ->
            maxOf(local.dailyGoalMinutes, remote.dailyGoalMinutes).coerceIn(5, 180) to local.dailyGoalUpdatedAt
        }
      val next =
        recompute(
          local.copy(
            dailyGoalMinutes = mergedGoal,
            dailyGoalUpdatedAt = mergedGoalUpdatedAt,
            longestStreak = maxOf(local.longestStreak, remote.longestStreak),
            totalSeconds = totalSec,
            totalPagesTurned = totalPages,
            dailyStats = mergedDaily,
          ),
          today = today,
        )
      persistLocked(next)
    }
  }

  private suspend fun persistLocked(next: ReadingStatsData, syncToDisk: Boolean = true) {
    val changed = next != state.value
    if (!changed && !dirty) return
    if (changed) {
      state.value = next
      dirty = true
    }
    try {
      write(state.value, syncToDisk = syncToDisk)
      dirty = false
      mutableLastWriteError.value = false
    } catch (e: CancellationException) {
      throw e
    } catch (_: Throwable) {
      dirty = true
      mutableLastWriteError.value = true
    }
  }

  private fun recompute(
    data: ReadingStatsData,
    today: LocalDate = todayProvider(),
  ): ReadingStatsData {
    val (currentStreak, historicalBest) = computeStreaks(data.dailyStats, data.dailyGoalMinutes, today)
    return data.copy(
      currentStreak = currentStreak,
      longestStreak = maxOf(data.longestStreak, currentStreak, historicalBest),
    )
  }

  private fun readInitial(): ReadingStatsData = store.read(ReadingStatsData())

  private suspend fun write(data: ReadingStatsData, syncToDisk: Boolean = true) {
    withContext(ioDispatcher) { store.write(data, syncToDisk = syncToDisk) }
  }

  companion object {
    internal fun computeStreaks(
      dailyStats: List<DailyReadingStat>,
      goalMinutes: Int,
      today: LocalDate,
    ): Pair<Int, Int> {
      val goalSeconds = goalMinutes.coerceAtLeast(1) * 60
      val qualified =
        dailyStats
          .filter { it.secondsRead >= goalSeconds }
          .mapNotNull { runCatching { LocalDate.parse(it.date) }.getOrNull() }
          .toSet()
      if (qualified.isEmpty()) return 0 to 0

      val start =
        when {
          today in qualified -> today
          today.minusDays(1) in qualified -> today.minusDays(1)
          else -> null
        }
      var current = 0
      var cursor = start
      while (cursor != null && cursor in qualified) {
        current += 1
        cursor = cursor.minusDays(1)
      }

      val sorted = qualified.sorted()
      var best = 0
      var run = 0
      var prev: LocalDate? = null
      for (date in sorted) {
        run = if (prev != null && prev.plusDays(1) == date) run + 1 else 1
        if (run > best) best = run
        prev = date
      }
      return current to best
    }

    fun recentDaysSeries(
      dailyStats: List<DailyReadingStat>,
      days: Int = 7,
      today: LocalDate = LocalDate.now(),
    ): List<DailyReadingStat> {
      val byDate = dailyStats.associateBy { it.date }
      return (days - 1 downTo 0).map { offset ->
        val date = today.minusDays(offset.toLong()).toString()
        byDate[date] ?: DailyReadingStat(date = date)
      }
    }
  }
}
