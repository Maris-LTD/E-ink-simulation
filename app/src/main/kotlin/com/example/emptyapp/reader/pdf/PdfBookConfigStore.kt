package com.example.emptyapp.reader.pdf

import android.content.Context
import com.example.emptyapp.data.storage.AtomicJsonFile
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer

class PdfBookConfigStore internal constructor(
  storageFile: File,
  preloadInBackground: Boolean,
  private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO.limitedParallelism(1),
) {
  constructor(storageFile: File) : this(
    storageFile = storageFile,
    preloadInBackground = false,
    ioDispatcher = Dispatchers.Unconfined,
  )

  constructor(context: Context) : this(
    storageFile = File(context.applicationContext.filesDir, "pdf_book_configs.json"),
    preloadInBackground = true,
    ioDispatcher = Dispatchers.IO.limitedParallelism(1),
  )

  private val store =
    AtomicJsonFile(storageFile, MapSerializer(String.serializer(), PdfBookConfig.serializer()))
  private val ioScope = CoroutineScope(SupervisorJob() + ioDispatcher)
  private val lock = Any()
  private val state =
    MutableStateFlow(if (preloadInBackground) emptyMap() else store.read(emptyMap()))
  private val mutableLoaded = MutableStateFlow(!preloadInBackground)
  @Volatile private var isLoaded = !preloadInBackground
  private var stateVersion = 0L
  private var persistedVersion = 0L
  private val pendingPreloadTransforms = mutableListOf<Pair<String, (PdfBookConfig) -> PdfBookConfig>>()
  private var lastWriteJob: Job? = null
  private val preloadJob: Job? =
    if (preloadInBackground) {
      ioScope.launch { performInitialLoad() }
    } else {
      null
    }

  val configs: StateFlow<Map<String, PdfBookConfig>> = state.asStateFlow()
  val loaded: StateFlow<Boolean> = mutableLoaded.asStateFlow()

  fun get(bookId: String): PdfBookConfig? = state.value[bookId]

  suspend fun awaitLoaded() {
    if (isLoaded) return
    preloadJob?.join()
    if (!isLoaded) {
      withContext(ioDispatcher) {
        performInitialLoad()
      }
    }
  }

  fun update(bookId: String, transform: (PdfBookConfig) -> PdfBookConfig): PdfBookConfig {
    synchronized(lock) {
      val current = state.value[bookId] ?: PdfBookConfig()
      val updated = transform(current)
      val nextMap = state.value.toMutableMap().apply { put(bookId, updated) }
      state.value = nextMap
      stateVersion += 1L
      if (isLoaded) {
        schedulePersistLocked()
      } else {
        pendingPreloadTransforms += bookId to transform
      }
      return updated
    }
  }

  internal suspend fun flushForTest() {
    awaitLoaded()
    while (true) {
      val job = synchronized(lock) { lastWriteJob }
      if (job == null || job.isCompleted) break
      job.join()
    }
  }

  private fun performInitialLoad() {
    if (isLoaded) return
    val fromDisk = store.read(emptyMap())
    synchronized(lock) {
      if (isLoaded) return
      val merged = fromDisk.toMutableMap()
      var needPersist = false
      if (pendingPreloadTransforms.isNotEmpty()) {
        for ((bookId, transform) in pendingPreloadTransforms) {
          val cur = merged[bookId] ?: PdfBookConfig()
          merged[bookId] = transform(cur)
        }
        pendingPreloadTransforms.clear()
        needPersist = true
      }
      state.value = merged
      isLoaded = true
      mutableLoaded.value = true
      if (needPersist) {
        schedulePersistLocked()
      }
    }
  }

  private fun schedulePersistLocked() {
    lastWriteJob =
      ioScope.launch {
        persistLatestIfNeeded()
      }
  }

  private fun persistLatestIfNeeded() {
    val (snapshot, targetVersion) =
      synchronized(lock) {
        if (!isLoaded || persistedVersion == stateVersion) return
        state.value to stateVersion
      }
    val wrote =
      try {
        store.write(snapshot)
        true
      } catch (e: CancellationException) {
        throw e
      } catch (_: Throwable) {
        false
      }
    if (wrote) {
      synchronized(lock) {
        if (persistedVersion < targetVersion) {
          persistedVersion = targetVersion
        }
      }
    }
  }
}
