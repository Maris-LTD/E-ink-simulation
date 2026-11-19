package com.example.emptyapp.data.billing

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

interface EntitlementCipher {
  fun encrypt(plainText: String): ByteArray

  fun decrypt(cipherBytes: ByteArray): String?
}

class AndroidKeystoreEntitlementCipher(
  private val keyAlias: String = DEFAULT_KEY_ALIAS,
) : EntitlementCipher {
  override fun encrypt(plainText: String): ByteArray {
    val cipher = Cipher.getInstance(TRANSFORMATION)
    cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
    val iv = cipher.iv
    val encrypted = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
    return ByteArray(1 + iv.size + encrypted.size).apply {
      this[0] = iv.size.toByte()
      iv.copyInto(this, destinationOffset = 1)
      encrypted.copyInto(this, destinationOffset = 1 + iv.size)
    }
  }

  override fun decrypt(cipherBytes: ByteArray): String? =
    runCatching {
      if (cipherBytes.size < 2) return null
      val ivLen = cipherBytes[0].toInt() and 0xFF
      if (ivLen <= 0 || cipherBytes.size <= 1 + ivLen) return null
      val iv = cipherBytes.copyOfRange(1, 1 + ivLen)
      val payload = cipherBytes.copyOfRange(1 + ivLen, cipherBytes.size)
      val key = getExistingKey() ?: return null
      val cipher = Cipher.getInstance(TRANSFORMATION)
      cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))
      String(cipher.doFinal(payload), Charsets.UTF_8)
    }.getOrNull()

  private fun getExistingKey(): SecretKey? {
    val ks = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
    return (ks.getEntry(keyAlias, null) as? KeyStore.SecretKeyEntry)?.secretKey
  }

  private fun getOrCreateKey(): SecretKey {
    getExistingKey()?.let { return it }
    val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
    val spec =
      KeyGenParameterSpec.Builder(
          keyAlias,
          KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
        )
        .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
        .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
        .setKeySize(256)
        .build()
    generator.init(spec)
    return generator.generateKey()
  }

  private companion object {
    const val ANDROID_KEYSTORE = "AndroidKeyStore"
    const val DEFAULT_KEY_ALIAS = "inkify_entitlement_aes"
    const val TRANSFORMATION = "AES/GCM/NoPadding"
    const val GCM_TAG_BITS = 128
  }
}
