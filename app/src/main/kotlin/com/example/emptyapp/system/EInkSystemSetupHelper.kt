package com.example.emptyapp.system

import android.app.WallpaperManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings

object EInkSystemSetupHelper {
  fun openHomeLauncherSettings(context: Context) {
    val intent = Intent(Settings.ACTION_HOME_SETTINGS)
    runCatching { context.startActivity(intent) }
  }

  fun openLiveWallpaperPicker(context: Context) {
    val direct =
      Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER).apply {
        putExtra(
          WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT,
          ComponentName(context, EInkWallpaperService::class.java),
        )
      }
    val launched = runCatching { context.startActivity(direct) }.isSuccess
    if (!launched) {
      runCatching { context.startActivity(Intent(WallpaperManager.ACTION_LIVE_WALLPAPER_CHOOSER)) }
    }
  }

  fun isLiveWallpaperActive(context: Context): Boolean =
    runCatching {
      val info = WallpaperManager.getInstance(context).wallpaperInfo
      info?.component == ComponentName(context, EInkWallpaperService::class.java)
    }.getOrDefault(false)

  fun isInkifyDefaultLauncher(context: Context): Boolean =
    runCatching {
      val homeIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
      val resolved = context.packageManager.resolveActivity(homeIntent, PackageManager.MATCH_DEFAULT_ONLY)
      resolved?.activityInfo?.packageName == context.packageName
    }.getOrDefault(false)
}
