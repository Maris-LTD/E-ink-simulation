package com.example.emptyapp.data.sync

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject

internal data class SyncUserProfile(
  val accountId: String,
  val email: String?,
  val displayName: String?,
)

internal object SyncAccountResolver {
  private val json = Json { ignoreUnknownKeys = true }

  fun parseUserProfile(rawJson: String): SyncUserProfile? {
    val obj = runCatching { json.parseToJsonElement(rawJson).jsonObject }.getOrNull() ?: return null
    val email = obj.stringOrNull("email")
    val sub = obj.stringOrNull("sub")
    val accountId = email ?: sub ?: return null
    val displayName = obj.stringOrNull("name")
    return SyncUserProfile(
      accountId = accountId,
      email = email,
      displayName = displayName,
    )
  }

  suspend fun resolveAndBindAccount(
    cachedAccountId: String?,
    fetchUserInfoJson: suspend () -> String,
    onProfileResolved: (SyncUserProfile) -> Unit,
    bindToAccount: (String) -> Unit,
    onUnresolvedAccount: () -> Throwable,
  ): String {
    val existing = cachedAccountId?.takeIf { it.isNotBlank() }
    if (existing != null) {
      bindToAccount(existing)
      return existing
    }
    val rawProfile = fetchUserInfoJson()
    val profile = parseUserProfile(rawProfile) ?: throw onUnresolvedAccount()
    bindToAccount(profile.accountId)
    onProfileResolved(profile)
    return profile.accountId
  }

  private fun Map<String, JsonElement>.stringOrNull(key: String): String? {
    val primitive = this[key] as? JsonPrimitive ?: return null
    if (primitive is JsonNull) return null
    return primitive.contentOrNull?.takeIf { it.isNotBlank() }
  }
}
