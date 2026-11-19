package com.example.emptyapp.data.sync

import com.example.emptyapp.data.storage.AtomicJsonFile
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable

@Serializable
data class SyncTombstoneData(
  val ownerEmail: String? = null,
  val books: List<SyncTombstone> = emptyList(),
  val annotations: List<SyncTombstone> = emptyList(),
)

class SyncTombstoneStore internal constructor(
  file: File,
  private val clock: () -> Long = System::currentTimeMillis,
  preloadInBackground: Boolean,
  private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
  constructor(
    file: File,
    clock: () -> Long = System::currentTimeMillis,
  ) : this(
    file = file,
    clock = clock,
    preloadInBackground = false,
    ioDispatcher = Dispatchers.IO,
  )

  private val store = AtomicJsonFile(file, SyncTombstoneData.serializer())
  private val lock = Any()
  private val backgroundScope = CoroutineScope(SupervisorJob() + ioDispatcher)
  private var dirty = false
  @Volatile private var isLoaded = !preloadInBackground
  private val mutableLoaded = MutableStateFlow(!preloadInBackground)
  private val mutableLastWriteError = MutableStateFlow(false)

  @Volatile
  private var data: SyncTombstoneData =
    if (preloadInBackground) SyncTombstoneData() else store.read(SyncTombstoneData())

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
      synchronized(lock) {
        ensureLoadedLocked()
      }
    }
  }

  private fun ensureLoadedLocked() {
    if (isLoaded) return
    data = store.read(SyncTombstoneData())
    isLoaded = true
    mutableLoaded.value = true
  }

  fun snapshot(): SyncTombstoneData {
    if (!isLoaded) {
      synchronized(lock) { ensureLoadedLocked() }
    }
    return data
  }

  fun deletedBookHashes(): Set<String> = SyncTombstoneMerger.deletedKeys(snapshot().books)

  fun deletedAnnotationIds(): Set<String> = SyncTombstoneMerger.deletedKeys(snapshot().annotations)

  fun markBooks(hashes: Collection<String>, deleted: Boolean) {
    if (hashes.isEmpty()) return
    mutate { it.copy(books = SyncTombstoneMerger.upsert(it.books, hashes, deleted, clock())) }
  }

  fun markAnnotations(ids: Collection<String>, deleted: Boolean) {
    if (ids.isEmpty()) return
    mutate { it.copy(annotations = SyncTombstoneMerger.upsert(it.annotations, ids, deleted, clock())) }
  }

  fun mergeRemote(books: List<SyncTombstone>, annotations: List<SyncTombstone>) {
    mutate { current ->
      val now = clock()
      current.copy(
        books = SyncTombstoneMerger.merge(current.books, books, now),
        annotations = SyncTombstoneMerger.merge(current.annotations, annotations, now),
      )
    }
  }

  fun bindToAccount(email: String) {
    mutate { current ->
      when (current.ownerEmail) {
        null -> current.copy(ownerEmail = email)
        email -> current
        else -> SyncTombstoneData(ownerEmail = email)
      }
    }
  }

  fun clear() {
    mutate { SyncTombstoneData() }
  }

  private fun mutate(transform: (SyncTombstoneData) -> SyncTombstoneData) {
    synchronized(lock) {
      ensureLoadedLocked()
      val next = transform(data)
      val changed = next != data
      if (!changed && !dirty) return
      if (changed) {
        data = next
        dirty = true
      }
      try {
        store.write(data)
        dirty = false
        mutableLastWriteError.value = false
      } catch (e: CancellationException) {
        throw e
      } catch (_: Throwable) {
        dirty = true
        mutableLastWriteError.value = true
      }
    }
  }
}
