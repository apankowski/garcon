package dev.pankowski.garcon.infrastructure.facebook

import dev.pankowski.garcon.domain.*
import org.jsoup.helper.HttpConnection
import org.jsoup.nodes.Document
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.net.URL
import kotlin.random.Random

interface PostExtractionStrategy {
  fun extractPosts(document: Document): Posts
}

@Component
class JsoupPageClient(
  private val clientConfig: ClientConfig,
  private val strategies: List<PostExtractionStrategy>
) : PageClient {

  private val log = LoggerFactory.getLogger(javaClass)

  override fun load(pageConfig: PageConfig): Page {
    val document = fetchDocument(pageConfig.url)
    val pageName = extractPageName(document, pageConfig)
    return strategies.asSequence()
      .map { it.extractPosts(document) }
      .firstOrNull { it.isNotEmpty() }
      .orEmpty()
      .apply {
        if (isEmpty()) log.warn("None of the Facebook post extraction strategies was able to extract post data")
      }
      .let { Page(pageName, it) }
  }

  private fun fetchDocument(url: URL): Document {
    fun fetch() =
      HttpConnection.connect(url)
        .userAgent(clientConfig.userAgent)
        .header("Accept", "text/html,application/xhtml+xml")
        .header("Accept-Language", "pl,en;q=0.5")
        .header("Cache-Control", "no-cache")
        .header("Pragma", "no-cache")
        .header("DNT", "1")
        .header("Sec-Fetch-Dest", "document")
        .header("Sec-Fetch-Mode", "navigate")
        .header("Sec-Fetch-Site", "none")
        .header("Sec-Fetch-User", "?1")
        .timeout(clientConfig.timeout.toMillis().toInt())
        .get()

    // We use retries as Facebook seems to be responding with 500 from time to time
    repeat(clientConfig.retryCount) {
      try {
        return fetch()
      } catch (_: Exception) {
        Thread.sleep(Random.nextLong(clientConfig.retryMinJitter.toMillis(), clientConfig.retryMaxJitter.toMillis()))
      }
    }
    return fetch()
  }

  private fun extractPageName(document: Document, pageConfig: PageConfig): PageName {
    val pageNameCandidates = sequence {
      yield(document.select("head meta[property=og:title]").attr("content"))
      yield(document.select("head meta[name=twitter:title]").attr("content"))
      yieldAll(document.select("h1").eachText())
    }

    pageNameCandidates
      .firstOrNull { it.isNotEmpty() }
      ?.let { return PageName(it) }

    log.warn("Couldn't extract page name from document at ${pageConfig.url}. Structure of the page might have changed")
    throw IllegalArgumentException("Page name couldn't be extracted")
  }
}
