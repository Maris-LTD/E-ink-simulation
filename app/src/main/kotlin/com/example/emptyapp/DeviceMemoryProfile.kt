package com.example.emptyapp

import android.app.ActivityManager
import android.content.ComponentCallbacks2
import android.content.Context

data class DeviceMemoryTier(
  val isLowRam: Boolean,
  val memoryClassMb: Int,
  val maxHeapBytes: Long,
) {
  val coverCacheSizeKb: Int
    get() =
      when {
        isLowRam -> 4 * 1024
        memoryClassMb <= 256 -> 8 * 1024
        else -> 16 * 1024
      }

  val pdfBoxMainMemoryBytes: Long
    get() = if (isLowRam) 4L * 1024L * 1024L else 8L * 1024L * 1024L

  val launcherIconCacheCount: Int
    get() = if (isLowRam) 96 else 256

  companion object {
    fun from(
      isLowRamDevice: Boolean,
      memoryClassMb: Int,
      maxHeapBytes: Long,
    ): DeviceMemoryTier {
      val heapMb = (maxHeapBytes / (1024L * 1024L)).toInt().coerceAtLeast(16)
      val effectiveMemClass = if (memoryClassMb > 0) memoryClassMb else heapMb
      val lowRam = isLowRamDevice || effectiveMemClass <= 192 || heapMb <= 192
      return DeviceMemoryTier(
        isLowRam = lowRam,
        memoryClassMb = effectiveMemClass,
        maxHeapBytes = maxHeapBytes,
      )
    }
  }
}

object DeviceMemoryProfile {
  @Volatile
  var current: DeviceMemoryTier =
    DeviceMemoryTier.from(
      isLowRamDevice = false,
      memoryClassMb = 0,
      maxHeapBytes = Runtime.getRuntime().maxMemory(),
    )
    private set

  fun init(context: Context) {
    val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
    val isLowRamDevice = am?.isLowRamDevice ?: false
    val memoryClassMb = am?.memoryClass ?: 0
    current =
      DeviceMemoryTier.from(
        isLowRamDevice = isLowRamDevice,
        memoryClassMb = memoryClassMb,
        maxHeapBytes = Runtime.getRuntime().maxMemory(),
      )
  }

  @Suppress("DEPRECATION")
  fun shouldEvictAll(level: Int): Boolean =
    level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL ||
      level == ComponentCallbacks2.TRIM_MEMORY_COMPLETE

  @Suppress("DEPRECATION")
  fun shouldTrimHalf(level: Int): Boolean =
    level >= ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN ||
      level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW
}
