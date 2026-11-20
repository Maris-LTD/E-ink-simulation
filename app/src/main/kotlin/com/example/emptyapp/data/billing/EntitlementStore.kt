package com.example.emptyapp.data.billing

import com.example.emptyapp.data.storage.AtomicJsonFile
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import kotlinx.serialization.json.Json

class EntitlementStore(
  filesDir: File,
  fileName: String = DEFAULT_FILE_NAME,
  private val cipher: EntitlementCipher? = null,
) {
  private val targetFile = File(filesDir, fileName)
  private val store = AtomicJsonFile(targetFile, ProEntitlement.serializer())
  private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

  fun read(): ProEntitlement {
    val activeCipher = cipher ?: return store.read(ProEntitlement())
    if (!targetFile.exists()) return ProEntitlement()
    val decoded = runCatching { activeCipher.decrypt(targetFile.readBytes()) }.getOrNull() ?: return ProEntitlement()
    return runCatching { json.decodeFromString(ProEntitlement.serializer(), decoded) }.getOrDefault(ProEntitlement())
  }

  fun write(entitlement: ProEntitlement) {
    val activeCipher = cipher
    if (activeCipher == null) {
      store.write(entitlement)
      return
    }
    targetFile.parentFile?.mkdirs()
    val encoded = json.encodeToString(ProEntitlement.serializer(), entitlement)
    val bytes = activeCipher.encrypt(encoded)
    val tmp = File(targetFile.parentFile, "${targetFile.name}.tmp")
    FileOutputStream(tmp).use { out ->
      out.write(bytes)
      out.flush()
      out.fd.sync()
    }
    if (!tmp.renameTo(targetFile)) {
      val deletedExisting = !targetFile.exists() || targetFile.delete()
      if (!deletedExisting || !tmp.renameTo(targetFile)) {
        if (targetFile.exists()) tmp.delete()
        throw IOException("Cannot replace ${targetFile.name}")
      }
    }
  }

  fun clear() = write(ProEntitlement())

  companion object {
    const val DEFAULT_FILE_NAME = "pro_entitlement.json"
    const val ENCRYPTED_FILE_NAME = "pro_entitlement.dat"
  }
}
