package com.example.emptyapp.data.billing

import com.example.emptyapp.data.storage.AtomicJsonFile
import java.io.File

class EntitlementStore(
  filesDir: File,
  fileName: String = DEFAULT_FILE_NAME,
) {
  private val store = AtomicJsonFile(File(filesDir, fileName), ProEntitlement.serializer())

  fun read(): ProEntitlement = store.read(ProEntitlement())

  fun write(entitlement: ProEntitlement) = store.write(entitlement)

  fun clear() = store.write(ProEntitlement())

  companion object {
    const val DEFAULT_FILE_NAME = "pro_entitlement.json"
  }
}
