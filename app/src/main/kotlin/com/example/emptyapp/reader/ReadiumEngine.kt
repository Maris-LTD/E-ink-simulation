package com.example.emptyapp.reader

import android.content.Context
import java.io.File
import org.readium.adapter.pdfium.document.PdfiumDocumentFactory
import org.readium.r2.shared.publication.Publication
import org.readium.r2.shared.util.asset.AssetRetriever
import org.readium.r2.shared.util.getOrElse
import org.readium.r2.shared.util.http.DefaultHttpClient
import org.readium.r2.shared.util.toUrl
import org.readium.r2.streamer.PublicationOpener
import org.readium.r2.streamer.parser.DefaultPublicationParser

class ReadiumEngine(context: Context) {
  private val httpClient = DefaultHttpClient()
  private val assetRetriever = AssetRetriever(context.contentResolver, httpClient)
  private val publicationOpener =
    PublicationOpener(
      publicationParser =
        DefaultPublicationParser(
          context = context,
          httpClient = httpClient,
          assetRetriever = assetRetriever,
          pdfFactory = PdfiumDocumentFactory(context),
        )
    )

  suspend fun open(file: File): Result<Publication> {
    val asset =
      assetRetriever.retrieve(file.toUrl()).getOrElse {
        return Result.failure(IllegalStateException(it.message))
      }
    val publication =
      publicationOpener.open(asset, allowUserInteraction = false).getOrElse {
        return Result.failure(IllegalStateException(it.message))
      }
    return Result.success(publication)
  }
}
