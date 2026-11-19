package com.example.emptyapp.data.sync

import com.example.emptyapp.data.storage.AtomicJsonFile
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable

@Serializable
data class SyncTombstoneData(
  val ownerEmail: String? = null,
  val books: List<SyncTombstone> = emptyList(),
  val annotations: List<SyncTombstone> = emptyList(),
)

class SyncTombstoneStore(
  file: File,
  private val clock: () -> Long = System::currentTimeMillis,
) {
  private val store = AtomicJsonFile(file, SyncTombstoneData.serializer())
  private val lock = Any()
  private var dirty = false
  private val mutableLastWriteError = MutableStateFlow(false)

  @Volatile
  private var data: SyncTombstoneData = store.read(SyncTombstoneData())

  val lastWriteError: StateFlow<Boolean> = mutableLastWriteError.asStateFlow()

  fun snapshot(): SyncTombstoneData = data

  fun deletedBookHashes(): Set<String> = SyncTombstoneMerger.deletedKeys(data.books)

  fun deletedAnnotationIds(): Set<String> = SyncTombstoneMerger.deletedKeys(data.annotations)

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
