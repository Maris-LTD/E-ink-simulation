package com.example.emptyapp.data.storage

import android.os.Build
import android.system.Os
import android.system.OsConstants
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.channels.FileChannel
import java.nio.file.StandardOpenOption
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json

class AtomicJsonFile<T>(
  private val file: File,
  private val serializer: KSerializer<T>,
  private val json: Json = DEFAULT_JSON,
  private val clock: () -> Long = System::currentTimeMillis,
  private val textReader: (File) -> String = { it.readText() },
  private val directorySync: (File?) -> Unit = ::defaultFsyncDirectory,
) {
  private val lock = Any()
  private val tempFile: File get() = File(file.parentFile, "${file.name}.tmp")
  @Volatile private var unquarantinedReadError = false

  fun read(defaultValue: T): T =
    synchronized(lock) {
      if (!file.exists()) {
        unquarantinedReadError = false
        val temp = tempFile
        if (temp.exists()) {
          val recovered =
            try {
              Result.success(json.decodeFromString(serializer, textReader(temp)))
            } catch (e: CancellationException) {
              throw e
            } catch (t: Throwable) {
              Result.failure(t)
            }
          if (recovered.isSuccess) {
            if (temp.renameTo(file)) {
              directorySync(file.parentFile)
            } else {
              val copied =
                runCatching {
                  temp.copyTo(file, overwrite = true)
                  true
                }.getOrDefault(false)
              if (copied) {
                temp.delete()
                directorySync(file.parentFile)
              }
            }
            return recovered.getOrThrow()
          }
        }
        return defaultValue
      }

      try {
        val text = textReader(file)
        val decoded = json.decodeFromString(serializer, text)
        unquarantinedReadError = false
        decoded
      } catch (e: CancellationException) {
        throw e
      } catch (_: Throwable) {
        val quarantined = quarantineCorruptFile()
        unquarantinedReadError = !quarantined
        defaultValue
      }
    }

  fun write(value: T) {
    val encoded = json.encodeToString(serializer, value)
    synchronized(lock) {
      if (unquarantinedReadError && file.exists()) {
        if (quarantineCorruptFile()) {
          unquarantinedReadError = false
        } else {
          throw IOException("Cannot quarantine unreadable ${file.name}")
        }
      } else if (!file.exists()) {
        unquarantinedReadError = false
      }
      file.parentFile?.mkdirs()
      val temp = tempFile
      FileOutputStream(temp).use { out ->
        out.write(encoded.toByteArray(Charsets.UTF_8))
        out.flush()
        out.fd.sync()
      }
      if (!temp.renameTo(file)) {
        val deletedExisting = !file.exists() || file.delete()
        if (!deletedExisting || !temp.renameTo(file)) {
          if (file.exists()) temp.delete()
          throw IOException("Cannot replace ${file.name}")
        }
      }
      directorySync(file.parentFile)
    }
  }

  private fun quarantineCorruptFile(): Boolean {
    val backup = File(file.parentFile, "${file.name}.corrupt-${clock()}")
    if (file.renameTo(backup)) {
      directorySync(file.parentFile)
      return true
    }
    val copied =
      runCatching {
        file.copyTo(backup, overwrite = true)
        true
      }.getOrDefault(false)
    if (copied) {
      directorySync(file.parentFile)
      file.delete()
      return true
    }
    return false
  }

  private companion object {
    val DEFAULT_JSON = Json { ignoreUnknownKeys = true }

    fun defaultFsyncDirectory(dir: File?) {
      if (dir == null || !dir.isDirectory) return
      val syncedViaOs =
        runCatching {
          val fd = Os.open(dir.absolutePath, OsConstants.O_RDONLY, 0)
          try {
            Os.fsync(fd)
          } finally {
            Os.close(fd)
          }
          true
        }.getOrDefault(false)
      if (!syncedViaOs && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        runCatching {
          FileChannel.open(dir.toPath(), StandardOpenOption.READ).use { it.force(true) }
        }
      }
    }
  }
}
