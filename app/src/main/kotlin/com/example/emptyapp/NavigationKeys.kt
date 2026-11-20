package com.example.emptyapp

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

@Serializable data object Library : NavKey

@Serializable data object ReadingStats : NavKey

@Serializable data object AppSettings : NavKey
