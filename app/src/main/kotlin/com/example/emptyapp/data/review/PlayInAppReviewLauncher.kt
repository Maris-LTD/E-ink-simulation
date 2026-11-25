package com.example.emptyapp.data.review

import android.app.Activity
import com.google.android.play.core.review.ReviewManagerFactory

object PlayInAppReviewLauncher {
  fun requestAndLaunch(activity: Activity, store: ReviewPromptStore) {
    if (activity.isFinishing || activity.isDestroyed || store.hasPrompted()) return
    val manager = ReviewManagerFactory.create(activity)
    manager.requestReviewFlow().addOnCompleteListener { requestTask ->
      if (!requestTask.isSuccessful) return@addOnCompleteListener
      if (activity.isFinishing || activity.isDestroyed || store.hasPrompted()) return@addOnCompleteListener
      val reviewInfo = requestTask.result
      store.markPrompted()
      manager.launchReviewFlow(activity, reviewInfo)
    }
  }
}
