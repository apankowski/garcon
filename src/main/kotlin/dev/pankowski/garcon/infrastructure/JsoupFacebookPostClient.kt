package dev.pankowski.garcon.infrastructure

import dev.pankowski.garcon.domain.*
import org.jsoup.Jsoup
import org.jsoup.helper.HttpConnection
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.safety.Safelist
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.web.util.UriComponentsBuilder
import java.net.URI
import java.net.URL
import java.time.Instant
import kotlin.random.Random

@Component
class JsoupFacebookPostClient(private val clientConfig: ClientConfig) : FacebookPostClient {

  private val log: Logger = LoggerFactory.getLogger(javaClass)

  override fun fetch(pageConfig: LunchPageConfig): Pair<PageName?, Posts> {
    val document = fetchDocument(pageConfig.url)
    val pageName = extractPageName(document, pageConfig)
    val posts = extractPosts(document).sortedBy { it.publishedAt }
    return pageName to posts
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

  private fun extractPageName(document: Document, pageConfig: LunchPageConfig): PageName? {
    val pageName = document.select("head meta[property=og:title]")
      .attr("content")
      .emptyToNull()
      ?.let(::PageName)

    if (pageName == null) {
      log.warn("Couldn't get facebook page name for ${pageConfig.url}")
    }

    return pageName
  }

  private fun extractPosts(document: Document) =
    document.select(".userContentWrapper").mapNotNull(::processContentWrapper)

  private fun processContentWrapper(e: Element): Post? {
    // Wrap content wrapper element in a document shell to limit parent traversal.
    Document.createShell(e.baseUri()).appendChild(e)

    val timestampElement = e.selectFirst(".timestampContent")

    val link = timestampElement
      ?.parents()
      ?.mapNotNull(::getLink)
      ?.firstOrNull()
    val facebookId = link?.let(::extractFacebookId)
    val facebookLink = facebookId?.let(::buildFacebookLink)

    val publishedAt = timestampElement
      ?.parents()
      ?.mapNotNull(::getTimestampData)
      ?.firstOrNull()

    val contentElement = e.selectFirst(".userContent")
    val content = contentElement?.let(::extractContent)

    if (facebookId == null || publishedAt == null || facebookLink == null || content == null) {
      log.warn(
        "Possible unexpected format of facebook page post. Found .userContentWrapper but "
          + "some of the post parts couldn't be extracted.\ntimestampElement: {}\nlink: {}\n"
          + "externalId: {}\nfacebookLink: {}\npublishedAt: {}\ncontent: {}\n.userContentWrapper: {}",
        timestampElement, link, facebookId, facebookLink, publishedAt, content, e
      )
      return null
    }

    return Post(facebookId, facebookLink, publishedAt, content)
  }

  private fun getTimestampData(e: Element) =
    e.attr("data-utime")
      .toLongOrNull()
      ?.let(Instant::ofEpochSecond)

  private fun String.emptyToNull() = takeUnless(String::isEmpty)

  private fun getLink(e: Element) =
    e.absUrl("href")
      .emptyToNull()
      ?.let(URI::create)

  private fun extractFacebookId(uri: URI): ExternalId? {
    // Regular post
    val postPathRegex = "^/?permalink\\.php".toRegex()
    postPathRegex.find(uri.path)?.let {
      return UriComponentsBuilder.fromUri(uri).build()
        .queryParams["story_fbid"]
        ?.firstOrNull()
        ?.let(::ExternalId)
    }

    // Regular post - alternative version
    val altPostPathRegex = "^/?[^/]+/posts/(\\d+)/?$".toRegex()
    altPostPathRegex.find(uri.path)?.let {
      return ExternalId(it.groupValues[1])
    }

    // Photo
    val photoPathRegex = "^/?[^/]+/photos/[a-z.\\d]+/(\\d+)/?$".toRegex()
    photoPathRegex.find(uri.path)?.let {
      return ExternalId(it.groupValues[1])
    }

    // Dunno
    return null
  }

  private fun buildFacebookLink(id: ExternalId) =
    URL("https://www.facebook.com/${id.id}")

  private fun extractContent(e: Element): String {
    // Remove the ellipsis & "show more" link from post's content.
    e.select(".text_exposed_hide").remove()
    e.select(".text_exposed_show").unwrap()
    e.select("br").after("\n")
    e.select("p").before("\n\n")

    // Switch off pretty-printing to preserve manually added newlines.
    val outputSettings = Document.OutputSettings().prettyPrint(false)
    e.ownerDocument()?.outputSettings(outputSettings)

    val dirty = e.html()
    val clean = Jsoup.clean(dirty, e.baseUri(), Safelist.none(), outputSettings)

    // Remove surrounding newlines & whitespace surrounding each individual line.
    return clean.trim().lines().joinToString(transform = String::trim, separator = "\n")
  }
}
