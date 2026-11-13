package com.example.emptyapp.reader

import org.readium.r2.navigator.OverflowableNavigator
import org.readium.r2.navigator.VisualNavigator
import org.readium.r2.navigator.input.InputListener
import org.readium.r2.navigator.input.TapEvent
import org.readium.r2.navigator.util.DirectionalNavigationAdapter
import org.readium.r2.shared.ExperimentalReadiumApi

private class CenterTapListener(private val onCenterTap: () -> Unit) : InputListener {
  override fun onTap(event: TapEvent): Boolean {
    onCenterTap()
    return true
  }
}

@OptIn(ExperimentalReadiumApi::class)
fun <T> T.attachReaderInput(onCenterTap: () -> Unit) where T : VisualNavigator, T : OverflowableNavigator {
  addInputListener(DirectionalNavigationAdapter(this, animatedTransition = false))
  addInputListener(CenterTapListener(onCenterTap))
}
