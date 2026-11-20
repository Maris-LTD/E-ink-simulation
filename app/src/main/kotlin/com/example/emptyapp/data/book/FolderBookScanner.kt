package com.example.emptyapp.data.book

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract

object FolderBookScanner {
  private val SUPPORTED_EXTENSIONS = setOf("epub", "pdf", "cbz")
  private val SUPPORTED_MIME_TYPES =
    setOf(
      "application/epub+zip",
      "application/x-epub+zip",
      "application/pdf",
      "application/vnd.comicbook+zip",
      "application/x-cbz",
    )

  fun isSupportedBookDocument(displayName: String?, mimeType: String?): Boolean {
    val normalizedMime = mimeType?.trim()?.lowercase()
    if (normalizedMime == DocumentsContract.Document.MIME_TYPE_DIR) return false
    val ext = displayName?.substringAfterLast('.', "")?.trim()?.lowercase()
    if (!ext.isNullOrEmpty() && ext in SUPPORTED_EXTENSIONS) return true
    return normalizedMime != null && normalizedMime in SUPPORTED_MIME_TYPES
  }

  fun scanTreeUri(
    context: Context,
    treeUri: Uri,
    maxDepth: Int = 4,
    maxFiles: Int = 200,
  ): List<Uri> {
    val rootDocId = runCatching { DocumentsContract.getTreeDocumentId(treeUri) }.getOrNull() ?: return emptyList()
    val resolver = context.contentResolver
    val projection =
      arrayOf(
        DocumentsContract.Document.COLUMN_DOCUMENT_ID,
        DocumentsContract.Document.COLUMN_DISPLAY_NAME,
        DocumentsContract.Document.COLUMN_MIME_TYPE,
      )
    val results = ArrayList<Uri>()
    val visited = HashSet<String>()
    val queue = ArrayDeque<Pair<String, Int>>()
    queue.add(rootDocId to 0)
    visited.add(rootDocId)

    while (queue.isNotEmpty() && results.size < maxFiles) {
      val (docId, depth) = queue.removeFirst()
      val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, docId)
      runCatching {
        resolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
          val idCol = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
          val nameCol = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
          val mimeCol = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_MIME_TYPE)
          if (idCol < 0) return@use
          while (cursor.moveToNext() && results.size < maxFiles) {
            val childId = cursor.getString(idCol) ?: continue
            if (!visited.add(childId)) continue
            val name = if (nameCol >= 0) cursor.getString(nameCol) else null
            val mime = if (mimeCol >= 0) cursor.getString(mimeCol) else null
            if (mime == DocumentsContract.Document.MIME_TYPE_DIR) {
              if (depth < maxDepth) {
                queue.add(childId to (depth + 1))
              }
            } else if (isSupportedBookDocument(name, mime)) {
              results.add(DocumentsContract.buildDocumentUriUsingTree(treeUri, childId))
            }
          }
        }
      }
    }
    return results
  }
}
