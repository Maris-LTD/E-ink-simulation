package com.example.emptyapp.data.annotation

import android.content.Context
import android.content.Intent

object HighlightExporter {
  fun toMarkdown(highlights: List<HighlightEntry>): String =
    buildString {
      appendLine("# Inkify Highlights & Notes")
      appendLine()
      highlights.groupBy { it.bookTitle.ifBlank { "Untitled" } }.forEach { (title, items) ->
        appendLine("## $title")
        appendLine()
        items.forEach { entry ->
          val pct = (entry.progress * 100).toInt()
          appendLine("> ${entry.quote.trim()}")
          if (entry.note.isNotBlank()) {
            appendLine("- **Note ($pct%):** ${entry.note.trim()}")
          } else {
            appendLine("- *Progress: $pct%*")
          }
          appendLine()
        }
      }
    }

  fun toPlainText(highlights: List<HighlightEntry>): String =
    buildString {
      highlights.forEach { entry ->
        val pct = (entry.progress * 100).toInt()
        appendLine("[${entry.bookTitle}] ($pct%)")
        appendLine("\"${entry.quote.trim()}\"")
        if (entry.note.isNotBlank()) {
          appendLine("Note: ${entry.note.trim()}")
        }
        appendLine()
      }
    }

  fun shareAsMarkdown(context: Context, highlights: List<HighlightEntry>) {
    if (highlights.isEmpty()) return
    val content = toMarkdown(highlights)
    val intent =
      Intent(Intent.ACTION_SEND).apply {
        type = "text/markdown"
        putExtra(Intent.EXTRA_SUBJECT, "Inkify Highlights & Notes")
        putExtra(Intent.EXTRA_TEXT, content)
      }
    runCatching { context.startActivity(Intent.createChooser(intent, null)) }
  }
}
