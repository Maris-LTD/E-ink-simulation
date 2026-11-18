package com.example.emptyapp.data.storage

import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json

class AtomicJsonFile<T>(
  private val file: File,
  private val serializer: KSerializer<T>,
  private val json: Json = DEFAULT_JSON,
  private val clock: () -> Long = System::currentTimeMillis,
) {
  private val lock = Any()
  private val tempFile: File get() = File(file.parentFile, "${file.name}.tmp")

  fun read(defaultValue: T): T =
    synchronized(lock) {
      if (!file.exists()) return defaultValue
      val text = runCatching { file.readText() }.getOrElse { return defaultValue }
      runCatching { json.decodeFromString(serializer, text) }
        .getOrElse {
          quarantineCorruptFile()
          defaultValue
        }
    }

  fun write(value: T) {
    val encoded = json.encodeToString(serializer, value)
    synchronized(lock) {
      file.parentFile?.mkdirs()
      val temp = tempFile
      FileOutputStream(temp).use { out ->
        out.write(encoded.toByteArray(Charsets.UTF_8))
        out.flush()
        out.fd.sync()
      }
      if (!temp.renameTo(file)) {
        file.delete()
        if (!temp.renameTo(file)) {
          temp.delete()
          throw IOException("Cannot replace ${file.name}")
        }
      }
    }
  }

  private fun quarantineCorruptFile() {
    val backup = File(file.parentFile, "${file.name}.corrupt-${clock()}")
    if (!file.renameTo(backup)) {
      runCatching { file.copyTo(backup, overwrite = true) }
    }
  }

  private companion object {
    val DEFAULT_JSON = Json { ignoreUnknownKeys = true }
  }
}
