package com.example.emptyapp

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import com.example.emptyapp.ui.library.LibraryScreen
import com.example.emptyapp.ui.library.LibraryViewModel

@Composable
fun MainNavigation() {
  val context = LocalContext.current
  val container = (context.applicationContext as EmptyApplication).container
  val backStack = rememberNavBackStack(Library)

  NavDisplay(
    backStack = backStack,
    onBack = { backStack.removeLastOrNull() },
    entryProvider =
      entryProvider {
        entry<Library> {
          val viewModel = viewModel { LibraryViewModel(container.library, container.annotations) }
          LibraryScreen(
            viewModel = viewModel,
            onOpenBook = { id, locatorJson ->
              context.startActivity(ReaderActivity.intentFor(context, id, locatorJson))
            },
          )
        }
      },
  )
}
