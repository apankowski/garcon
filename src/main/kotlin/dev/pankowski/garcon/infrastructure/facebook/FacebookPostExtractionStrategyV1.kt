package dev.pankowski.garcon.infrastructure.facebook

import dev.pankowski.garcon.domain.ExternalId
import dev.pankowski.garcon.domain.Post
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.safety.Safelist
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.net.URI
import java.net.URL
import java.time.Instant

/**
 * Post extraction strategy viable for pages returned before H1- 2022.
 *
 * Until then Facebook pages were server-side rendered. Extracting posts from a page meant
 * (more or less) searching for DOM elements with appropriate CSS selectors.
 */
@Component
class FacebookPostExtractionStrategyV1 : FacebookPostExtractionStrategy {

  private val log = LoggerFactory.getLogger(javaClass)

  override fun extractPosts(document: Document) =
    document
      .select(".userContentWrapper")
      .mapNotNull(::processContentWrapper)
      .sortedBy { it.publishedAt }

  private fun processContentWrapper(e: Element): Post? {
    // Wrap content wrapper element in a document shell to limit parent traversal.
    Document.createShell(e.baseUri()).appendChild(e)

    val timestampElement = e.selectFirst(".timestampContent")

    val link = timestampElement
      ?.parents()
      ?.mapNotNull(::getLink)
      ?.firstOrNull()
    val facebookId = link?.let(FacebookIdExtractor::extractFacebookId)
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

  private fun getLink(e: Element) =
    e.absUrl("href")
      .takeUnless(String::isEmpty)
      ?.let(URI::create)

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
