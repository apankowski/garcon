package dev.pankowski.garcon.infrastructure.facebook

import com.google.common.annotations.VisibleForTesting
import dev.pankowski.garcon.domain.FacebookPostId
import dev.pankowski.garcon.domain.Post
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.safety.Safelist
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.web.util.UriComponentsBuilder
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
class PostExtractionStrategyV1 : PostExtractionStrategy {

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

    val url = timestampElement
      ?.parents()
      ?.firstNotNullOfOrNull(::getUrl)
    val facebookId = url?.let(FacebookIdExtractor::extractFacebookId)

    val publishedAt = timestampElement
      ?.parents()
      ?.firstNotNullOfOrNull(::getTimestampData)

    val contentElement = e.selectFirst(".userContent")
    val content = contentElement?.let(::extractContent)

    if (facebookId == null || publishedAt == null || content == null) {
      log.warn(
        "Possible unexpected format of facebook page post. Found .userContentWrapper but "
          + "some of the post parts couldn't be extracted.\ntimestampElement: {}\nURL: {}\n"
          + "externalId: {}\npublishedAt: {}\ncontent: {}\n.userContentWrapper: {}",
        timestampElement, url, facebookId, publishedAt, content, e
      )
      return null
    }

    return Post(facebookId, url, publishedAt, content)
  }

  private fun getTimestampData(e: Element) =
    e.attr("data-utime")
      .toLongOrNull()
      ?.let(Instant::ofEpochSecond)

  private fun getUrl(e: Element) =
    e.absUrl("href")
      .takeUnless(String::isEmpty)
      ?.let { URI(it).toURL() }

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

@VisibleForTesting
object FacebookIdExtractor {

  fun extractFacebookId(url: URL): FacebookPostId? {
    // Regular post
    val postPathRegex = "^/?permalink\\.php".toRegex()
    postPathRegex.find(url.path)?.let {
      return UriComponentsBuilder.fromUri(url.toURI()).build()
        .queryParams["story_fbid"]
        ?.firstOrNull()
        ?.let(::FacebookPostId)
    }

    // Regular post - alternative version
    val altPostPathRegex = "^/?[^/]+/posts/([0-9a-zA-Z_-]+)/?".toRegex()
    altPostPathRegex.find(url.path)?.let {
      return FacebookPostId(it.groupValues[1])
    }

    // Photo
    val photoPathRegex = "^/?[^/]+/photos/[0-9a-zA-Z._-]+/([0-9a-zA-Z_-]+)/?".toRegex()
    photoPathRegex.find(url.path)?.let {
      return FacebookPostId(it.groupValues[1])
    }

    // Photo - alternative version
    val altPhotoPathRegex = "^/?photo/?".toRegex()
    altPhotoPathRegex.find(url.path)?.let {
      return UriComponentsBuilder.fromUri(url.toURI()).build()
        .queryParams["fbid"]
        ?.firstOrNull()
        ?.let(::FacebookPostId)
    }

    // Video
    val videoPathRegex = "^/?watch/?".toRegex()
    videoPathRegex.find(url.path)?.let {
      return UriComponentsBuilder.fromUri(url.toURI()).build()
        .queryParams["v"]
        ?.firstOrNull()
        ?.let(::FacebookPostId)
    }

    // Reel
    val reelPathRegex = "^/?reel/([0-9a-zA-Z_-]+)/?".toRegex()
    reelPathRegex.find(url.path)?.let {
      return FacebookPostId(it.groupValues[1])
    }

    // Dunno
    return null
  }
}
