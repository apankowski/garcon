package dev.pankowski.garcon.infrastructure.facebook

import dev.pankowski.garcon.domain.*
import org.jsoup.helper.HttpConnection
import org.jsoup.nodes.Document
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.net.URL
import kotlin.random.Random

interface FacebookPostExtractionStrategy {
  fun extractPosts(document: Document): Sequence<Post>
}

@Component
class JsoupFacebookPageClient(
  private val clientConfig: ClientConfig,
  private val strategies: List<FacebookPostExtractionStrategy>
) : FacebookPageClient {

  private val log = LoggerFactory.getLogger(javaClass)

  override fun fetch(pageConfig: PageConfig): PageOfPosts {
    val document = fetchDocument(pageConfig.url)
    val pageName = extractPageName(document, pageConfig) ?: PageName(pageConfig.id.value)
    val posts = strategies.asSequence()
      .map { it.extractPosts(document) }
      .firstNonEmpty()
      .onEmpty { log.warn("None of the Facebook post extraction strategies was able to extract post data") }
    return PageOfPosts(pageName, posts)
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

  private fun extractPageName(document: Document, pageConfig: PageConfig): PageName? {
    val pageName = document.select("head meta[property=og:title]")
      .attr("content")
      .takeUnless(String::isEmpty)
      ?.let(::PageName)

    if (pageName == null) {
      log.warn("Couldn't get facebook page name for ${pageConfig.url}")
    }

    return pageName
  }

  private fun <T> Sequence<Sequence<T>>.firstNonEmpty() =
    Sequence {
      // Access iterators lazily, at most once per invocation in case any of the sequences
      // doesn't support iterating over it multiple times (see Sequence documentation).
      this@firstNonEmpty
        .map { it.iterator() }
        .firstOrNull { it.hasNext() }
        ?: emptySequence<T>().iterator()
    }

  private fun <T> Sequence<T>.onEmpty(action: () -> Unit) =
    Sequence {
      this@onEmpty.iterator()
        .apply { if (!hasNext()) action.invoke() }
    }
}
