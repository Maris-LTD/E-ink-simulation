package com.example.emptyapp.security

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.Debug
import java.security.MessageDigest

object AppIntegrityGuard {
  private const val MASK: Byte = 0x5A

  private val maskedReleaseCertSha256 =
    byteArrayOf(
      (0x73 xor 0x5A).toByte(), (0x5F xor 0x5A).toByte(), (0x71 xor 0x5A).toByte(), (0xDA xor 0x5A).toByte(),
      (0x52 xor 0x5A).toByte(), (0xB0 xor 0x5A).toByte(), (0x18 xor 0x5A).toByte(), (0xD9 xor 0x5A).toByte(),
      (0x5D xor 0x5A).toByte(), (0x7D xor 0x5A).toByte(), (0xDB xor 0x5A).toByte(), (0x12 xor 0x5A).toByte(),
      (0x66 xor 0x5A).toByte(), (0x91 xor 0x5A).toByte(), (0x8B xor 0x5A).toByte(), (0x95 xor 0x5A).toByte(),
      (0x51 xor 0x5A).toByte(), (0x56 xor 0x5A).toByte(), (0x39 xor 0x5A).toByte(), (0xAD xor 0x5A).toByte(),
      (0x74 xor 0x5A).toByte(), (0x88 xor 0x5A).toByte(), (0xE8 xor 0x5A).toByte(), (0xFB xor 0x5A).toByte(),
      (0x6B xor 0x5A).toByte(), (0xD6 xor 0x5A).toByte(), (0xD4 xor 0x5A).toByte(), (0x09 xor 0x5A).toByte(),
      (0x35 xor 0x5A).toByte(), (0xEF xor 0x5A).toByte(), (0x1E xor 0x5A).toByte(), (0xF5 xor 0x5A).toByte(),
    )

  fun isIntegrityValid(context: Context): Boolean {
    val isDebuggable = (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
    if (isDebuggable) return true
    if (Debug.isDebuggerConnected() || Debug.waitingForDebugger()) return false
    return hasExpectedReleaseSignature(context)
  }

  private fun hasExpectedReleaseSignature(context: Context): Boolean {
    val expected = ByteArray(maskedReleaseCertSha256.size) { idx ->
      (maskedReleaseCertSha256[idx].toInt() xor MASK.toInt()).toByte()
    }
    val certDigests = currentSigningSha256Digests(context)
    return certDigests.any { MessageDigest.isEqual(it, expected) }
  }

  @Suppress("DEPRECATION")
  private fun currentSigningSha256Digests(context: Context): List<ByteArray> =
    runCatching {
      val pm = context.packageManager
      val pkg = context.packageName
      val signatures =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
          val info = pm.getPackageInfo(pkg, PackageManager.GET_SIGNING_CERTIFICATES)
          val signingInfo = info.signingInfo ?: return emptyList()
          if (signingInfo.hasMultipleSigners()) signingInfo.apkContentsSigners else signingInfo.signingCertificateHistory
        } else {
          pm.getPackageInfo(pkg, PackageManager.GET_SIGNATURES).signatures
        } ?: return emptyList()
      val digest = MessageDigest.getInstance("SHA-256")
      signatures.map { sig -> digest.digest(sig.toByteArray()) }
    }.getOrDefault(emptyList())
}
