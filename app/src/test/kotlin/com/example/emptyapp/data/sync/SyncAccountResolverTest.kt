package com.example.emptyapp.data.sync

import java.io.File
import java.io.IOException
import kotlin.io.path.createTempDirectory
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class SyncAccountResolverTest {
  private lateinit var dir: File

  @Before
  fun setUp() {
    dir = createTempDirectory().toFile()
  }

  @After
  fun tearDown() {
    dir.deleteRecursively()
  }

  @Test
  fun parseUserProfilePrefersEmailAndReadsName() {
    val profile =
      SyncAccountResolver.parseUserProfile("""{"email":"alice@example.com","sub":"sub-1","name":"Alice"}""")

    assertEquals(SyncUserProfile(accountId = "alice@example.com", email = "alice@example.com", displayName = "Alice"), profile)
  }

  @Test
  fun parseUserProfileFallsBackToSubWhenEmailMissingOrBlank() {
    val profileMissingEmail = SyncAccountResolver.parseUserProfile("""{"sub":"sub-123","name":"Bob"}""")
    val profileBlankEmail = SyncAccountResolver.parseUserProfile("""{"email":"  ","sub":"sub-456"}""")
    val profileNullEmail = SyncAccountResolver.parseUserProfile("""{"email":null,"sub":"sub-789"}""")

    assertEquals(SyncUserProfile(accountId = "sub-123", email = null, displayName = "Bob"), profileMissingEmail)
    assertEquals(SyncUserProfile(accountId = "sub-456", email = null, displayName = null), profileBlankEmail)
    assertEquals(SyncUserProfile(accountId = "sub-789", email = null, displayName = null), profileNullEmail)
  }

  @Test
  fun parseUserProfileReturnsNullOnMissingIdentityOrMalformedJson() {
    assertNull(SyncAccountResolver.parseUserProfile("""{"name":"NoId"}"""))
    assertNull(SyncAccountResolver.parseUserProfile("""{"email":"","sub":" "}"""))
    assertNull(SyncAccountResolver.parseUserProfile("{not-json"))
  }

  @Test
  fun stopsBeforeBindOrDriveWriteWhenUserInfoFails() = runTest {
    var bindCalled = false
    var profileSaved = false
    var driveWriteCalled = false

    val failure =
      runCatching {
        SyncAccountResolver.resolveAndBindAccount(
          cachedAccountId = null,
          fetchUserInfoJson = { throw IOException("network down") },
          onProfileResolved = { profileSaved = true },
          bindToAccount = { bindCalled = true },
          onUnresolvedAccount = { DriveRemoteReadException("userinfo") },
        )
        driveWriteCalled = true
      }.exceptionOrNull()

    assertTrue(failure is IOException)
    assertFalse(bindCalled)
    assertFalse(profileSaved)
    assertFalse(driveWriteCalled)
  }

  @Test
  fun stopsBeforeBindWhenUserInfoHasNeitherEmailNorSub() = runTest {
    val store = SyncTombstoneStore(File(dir, "tombstones.json"), clock = { 100L })
    store.bindToAccount("old@example.com")
    store.markBooks(listOf("book-old"), deleted = true)
    var profileSaved = false

    val failure =
      runCatching {
        SyncAccountResolver.resolveAndBindAccount(
          cachedAccountId = null,
          fetchUserInfoJson = { """{"name":"Nameless"}""" },
          onProfileResolved = { profileSaved = true },
          bindToAccount = store::bindToAccount,
          onUnresolvedAccount = { DriveRemoteReadException("userinfo") },
        )
      }.exceptionOrNull()

    assertTrue(failure is DriveRemoteReadException)
    assertFalse(profileSaved)
    assertEquals("old@example.com", store.snapshot().ownerEmail)
    assertEquals(setOf("book-old"), store.deletedBookHashes())
  }

  @Test
  fun propagates401FromUserInfoSoDriveAuthRetryCanRefreshToken() = runTest {
    val store = SyncTombstoneStore(File(dir, "tombstones.json"), clock = { 100L })
    store.bindToAccount("a@example.com")
    store.markBooks(listOf("book-a"), deleted = true)
    var savedProfile: SyncUserProfile? = null

    val outcome =
      DriveAuthRetry.executeWithSingle401Retry<String, String>(
        initialToken = "expired-token",
        clearToken = {},
        authorize = { AuthTokenResponse("fresh-token", hasResolution = false) },
      ) { activeToken ->
        SyncAccountResolver.resolveAndBindAccount(
          cachedAccountId = null,
          fetchUserInfoJson = {
            if (activeToken == "expired-token") throw DriveHttpException(401, "HTTP 401")
            """{"sub":"sub-b","name":"User B"}"""
          },
          onProfileResolved = { savedProfile = it },
          bindToAccount = store::bindToAccount,
          onUnresolvedAccount = { DriveRemoteReadException("userinfo") },
        )
      }

    assertEquals(TokenRetryOutcome.Succeeded("sub-b", "fresh-token"), outcome)
    assertEquals(SyncUserProfile("sub-b", email = null, displayName = "User B"), savedProfile)
    assertEquals("sub-b", store.snapshot().ownerEmail)
    assertEquals(emptySet<String>(), store.deletedBookHashes())
  }

  @Test
  fun usesCachedAccountIdWithoutFetchingAndBindsTombstones() = runTest {
    val store = SyncTombstoneStore(File(dir, "tombstones.json"), clock = { 100L })
    store.bindToAccount("a@example.com")
    store.markBooks(listOf("book-a"), deleted = true)
    var fetchCalled = false

    val accountId =
      SyncAccountResolver.resolveAndBindAccount(
        cachedAccountId = "b@example.com",
        fetchUserInfoJson = {
          fetchCalled = true
          "{}"
        },
        onProfileResolved = {},
        bindToAccount = store::bindToAccount,
        onUnresolvedAccount = { DriveRemoteReadException("userinfo") },
      )

    assertFalse(fetchCalled)
    assertEquals("b@example.com", accountId)
    assertEquals("b@example.com", store.snapshot().ownerEmail)
    assertEquals(emptySet<String>(), store.deletedBookHashes())
  }

  @Test
  fun tombstoneStoreImportRestoreBeatsOlderCloudDeletionWhenNotLocallyDeletedBefore() {
    var now = 200L
    val store = SyncTombstoneStore(File(dir, "tombstones.json"), clock = { now })
    store.bindToAccount("user@example.com")

    store.markBooks(listOf("book-reimported"), deleted = false)
    now = 300L
    store.mergeRemote(
      books = listOf(SyncTombstone("book-reimported", changedAt = 100L, deleted = true)),
      annotations = emptyList(),
    )

    assertEquals(emptySet<String>(), store.deletedBookHashes())
    assertEquals(
      listOf(SyncTombstone("book-reimported", changedAt = 200L, deleted = false)),
      store.snapshot().books,
    )
  }
}
