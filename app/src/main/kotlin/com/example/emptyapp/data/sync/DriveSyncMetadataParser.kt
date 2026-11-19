package com.example.emptyapp.data.sync

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject

internal data class DriveFileListPage(
  val entries: List<DriveFileEntry>,
  val nextPageToken: String?,
)

internal object DriveSyncMetadataParser {
  private val json = Json { ignoreUnknownKeys = true }

  fun parseFileListPage(rawJson: String): DriveFileListPage? {
    val root = runCatching { json.parseToJsonElement(rawJson).jsonObject }.getOrNull() ?: return null
    val files = root["files"] as? JsonArray
    val entries =
      files?.mapNotNull { element ->
        val obj = element as? JsonObject ?: return@mapNotNull null
        obj.toDriveFileEntry()
      }.orEmpty()
    val nextPageToken = root.stringOrNull("nextPageToken")
    return DriveFileListPage(entries = entries, nextPageToken = nextPageToken)
  }

  fun parseFileEntry(rawJson: String): DriveFileEntry? {
    val obj = runCatching { json.parseToJsonElement(rawJson).jsonObject }.getOrNull() ?: return null
    return obj.toDriveFileEntry()
  }

  private fun JsonObject.toDriveFileEntry(): DriveFileEntry? {
    val id = stringOrNull("id") ?: return null
    val name = stringOrNull("name").orEmpty()
    return DriveFileEntry(
      id = id,
      name = name,
      createdTime = stringOrNull("createdTime"),
      version = stringOrNull("version"),
      md5Checksum = stringOrNull("md5Checksum"),
    )
  }

  private fun Map<String, JsonElement>.stringOrNull(key: String): String? {
    val primitive = this[key] as? JsonPrimitive ?: return null
    if (primitive is JsonNull) return null
    return primitive.contentOrNull?.takeIf { it.isNotBlank() }
  }
}
