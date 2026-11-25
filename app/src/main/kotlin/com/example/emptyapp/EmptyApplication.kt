package com.example.emptyapp

import android.app.Application
import android.content.ComponentCallbacks2
import com.example.emptyapp.ui.eink.CoverBitmapCache
import com.example.emptyapp.ui.reader.PdfRendererHolderRegistry

class EmptyApplication : Application() {
  lateinit var container: AppContainer
    private set

  override fun onCreate() {
    super.onCreate()
    DeviceMemoryProfile.init(this)
    CoverBitmapCache.configureForDevice()
    LauncherAppCache.configureForDevice()
    container = AppContainer(this)
  }

  override fun onTrimMemory(level: Int) {
    super.onTrimMemory(level)
    CoverBitmapCache.trimMemory(level)
    LauncherAppCache.trimMemory(level)
    PdfRendererHolderRegistry.trimMemory(level)
  }

  @Suppress("DEPRECATION")
  override fun onLowMemory() {
    super.onLowMemory()
    onTrimMemory(ComponentCallbacks2.TRIM_MEMORY_COMPLETE)
  }
}
