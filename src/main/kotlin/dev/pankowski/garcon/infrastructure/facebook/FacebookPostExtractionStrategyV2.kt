package dev.pankowski.garcon.infrastructure.facebook

import com.fasterxml.jackson.databind.node.ArrayNode
import com.fasterxml.jackson.databind.node.ObjectNode
import dev.pankowski.garcon.domain.ExternalId
import dev.pankowski.garcon.domain.Post
import dev.pankowski.garcon.domain.Posts
import net.thisptr.jackson.jq.BuiltinFunctionLoader
import net.thisptr.jackson.jq.JsonQuery
import net.thisptr.jackson.jq.Scope
import net.thisptr.jackson.jq.Version
import org.jsoup.nodes.Document
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.net.URL
import java.time.Instant

/**
 * Post extraction strategy suitable for pages returned H2+ 2022.
 *
 * See `${REPOSITORY_ROOT}/docs/facebook/post-extraction-strategy-v2.md` for a description of how strategy works.
 */
@Component
class FacebookPostExtractionStrategyV2 : FacebookPostExtractionStrategy {

  private val log = LoggerFactory.getLogger(javaClass)
  private val rootScope = Scope.newEmptyScope()
  private val postsQuery: JsonQuery

  init {
    BuiltinFunctionLoader.getInstance().loadFunctions(Version.LATEST, rootScope)

    // The ${'$'} interpolation is used solely to produce a $ character in the query string since
    // Kotlin doesn't provide character escapes for multi-line strings.
    // The alternative would be to use single-line string which would make the query unreadable 🙄
    postsQuery = JsonQuery.compile(
      """
      [.. | objects | select(.__typename == "Story")] |
          (.. | objects | select(has("content")) | .content) as ${'$'}content |
              (${'$'}content | .. | objects | select(has("creation_time"))) as ${'$'}metadata |
                  map({
                      "id": .post_id,
                      "published_at": ${'$'}metadata.creation_time,
                      "url": ${'$'}metadata.url,
                      "content": ${'$'}content | .. | objects | select(.__typename == "TextWithEntities") | .text
                  })
      """.trimIndent(),
      Version.LATEST,
    )
  }

  override fun extractPosts(document: Document) =
    document
      .select("script")
      .map { it.data() }
      .flatMap { JavaScriptObjectLiteralExtractor.extractFrom(it) }
      .flatMap { extractPostsFromObjectLiteral(it) }
      .sortedBy { it.publishedAt }
      .apply { if (isEmpty()) log.warn("No posts found. Returned representation might have changed.") }

  private fun extractPostsFromObjectLiteral(objectLiteral: ObjectNode): Posts {
    var result = emptyList<Post>()
    postsQuery.apply(rootScope, objectLiteral) { output ->
      if (output is ArrayNode)
        result = extractPostsFromQueryOutput(output)
    }
    return result
  }

  private fun extractPostsFromQueryOutput(output: ArrayNode): Posts {
    return output.mapNotNull map@{ node ->
      Post(
        // TODO: Better edge-case handling (missing properties, properties of wrong type) + warnings in logs
        externalId = node.get("id").textValue()?.let { ExternalId(it) } ?: return@map null,
        link = node.get("url").textValue()?.let { URL(it) } ?: return@map null,
        publishedAt = node.get("published_at").longValue().let { Instant.ofEpochSecond(it) } ?: return@map null,
        content = node.get("content").textValue()?.sanitizeContent() ?: return@map null,
      )
    }
  }

  private fun String.sanitizeContent() =
    lineSequence()
      .map { it.trimEnd() }
      .joinToString("\n")
}
