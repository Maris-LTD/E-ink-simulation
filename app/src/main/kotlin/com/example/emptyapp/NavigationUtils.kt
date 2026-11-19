package com.example.emptyapp

import androidx.navigation3.runtime.NavKey

fun popBackStackSafely(stack: MutableList<NavKey>, expectedTop: NavKey? = null): Boolean {
  if (stack.size <= 1) return false
  if (expectedTop != null && stack.lastOrNull() != expectedTop) return false
  stack.removeLastOrNull()
  return true
}

fun pushNavKeyIfNotTop(stack: MutableList<NavKey>, key: NavKey): Boolean {
  if (stack.lastOrNull() == key) return false
  stack.add(key)
  return true
}
