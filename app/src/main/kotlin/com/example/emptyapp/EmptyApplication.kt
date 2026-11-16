package com.example.emptyapp

import android.app.Application
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader

class EmptyApplication : Application() {
  lateinit var container: AppContainer
    private set

  override fun onCreate() {
    super.onCreate()
    PDFBoxResourceLoader.init(applicationContext)
    container = AppContainer(this)
  }
}
