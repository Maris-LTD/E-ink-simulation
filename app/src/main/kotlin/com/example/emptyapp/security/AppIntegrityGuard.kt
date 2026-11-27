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
      (0x61 xor 0x5A).toByte(), (0x1F xor 0x5A).toByte(), (0xF0 xor 0x5A).toByte(), (0xB3 xor 0x5A).toByte(),
      (0x21 xor 0x5A).toByte(), (0x77 xor 0x5A).toByte(), (0x92 xor 0x5A).toByte(), (0xA0 xor 0x5A).toByte(),
      (0x5B xor 0x5A).toByte(), (0xAE xor 0x5A).toByte(), (0xB5 xor 0x5A).toByte(), (0xCD xor 0x5A).toByte(),
      (0xD2 xor 0x5A).toByte(), (0xD1 xor 0x5A).toByte(), (0x98 xor 0x5A).toByte(), (0xC4 xor 0x5A).toByte(),
      (0x71 xor 0x5A).toByte(), (0x6B xor 0x5A).toByte(), (0xA9 xor 0x5A).toByte(), (0x5A xor 0x5A).toByte(),
      (0x2E xor 0x5A).toByte(), (0x4C xor 0x5A).toByte(), (0x0C xor 0x5A).toByte(), (0x2E xor 0x5A).toByte(),
      (0xE2 xor 0x5A).toByte(), (0x84 xor 0x5A).toByte(), (0x40 xor 0x5A).toByte(), (0x79 xor 0x5A).toByte(),
      (0x2A xor 0x5A).toByte(), (0x20 xor 0x5A).toByte(), (0xFF xor 0x5A).toByte(), (0xA0 xor 0x5A).toByte(),
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
