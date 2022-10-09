package dev.pankowski.garcon.infrastructure.facebook

import dev.pankowski.garcon.domain.*
import org.jsoup.helper.HttpConnection
import org.jsoup.nodes.Document
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.net.URL
import kotlin.random.Random

interface FacebookPostExtractionStrategy {
  fun extractPosts(document: Document): Posts
}

@Component
class JsoupFacebookPostClient(
  private val clientConfig: ClientConfig,
  private val strategies: List<FacebookPostExtractionStrategy>
) : FacebookPostClient {

  private val log = LoggerFactory.getLogger(javaClass)

  override fun fetch(pageConfig: LunchPageConfig): Pair<PageName?, Posts> {
    val document = fetchDocument(pageConfig.url)
    val pageName = extractPageName(document, pageConfig) ?: PageName(pageConfig.id.value)
    for (s in strategies) {
      val posts = s.extractPosts(document)
      if (posts.isNotEmpty()) return Pair(pageName, posts)
    }
    log.warn("None of the Facebook post extraction strategies was able to extract post data")
    return Pair(pageName, emptyList())
  }

  private fun extractPageName(document: Document, pageConfig: LunchPageConfig): PageName? {
    val pageName = document.select("head meta[property=og:title]")
      .attr("content")
      .takeUnless(String::isEmpty)
      ?.let(::PageName)

    if (pageName == null) {
      log.warn("Couldn't get facebook page name for ${pageConfig.url}")
    }

    return pageName
  }

  private fun fetchDocument(url: URL): Document {
    fun fetch() =
      HttpConnection.connect(url)
        .userAgent(clientConfig.userAgent)
        .header("Accept", "text/html,application/xhtml+xml")
        .header("Accept-Language", "pl,en;q=0.5")
        .header("Cache-Control", "no-cache")
        .header("Pragma", "no-cache")
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
}
