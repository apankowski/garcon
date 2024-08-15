package dev.pankowski.garcon.infrastructure.facebook

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.json.JsonMapper
import com.fasterxml.jackson.databind.node.JsonNodeType
import com.fasterxml.jackson.databind.node.JsonNodeType.NUMBER
import com.fasterxml.jackson.databind.node.JsonNodeType.STRING
import com.fasterxml.jackson.databind.node.ObjectNode
import com.google.common.annotations.VisibleForTesting
import dev.pankowski.garcon.domain.*
import net.thisptr.jackson.jq.BuiltinFunctionLoader
import net.thisptr.jackson.jq.JsonQuery
import net.thisptr.jackson.jq.Scope
import net.thisptr.jackson.jq.Version
import org.jsoup.nodes.Document
import org.mozilla.javascript.Parser
import org.mozilla.javascript.ast.AstNode
import org.mozilla.javascript.ast.ObjectLiteral
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.time.Instant

/**
 * Post extraction strategy suitable for pages returned H2+ 2022.
 *
 * See `${REPOSITORY_ROOT}/docs/facebook/post-extraction-strategy-v2.md` for a description of how it works.
 */
@Component
class PostExtractionStrategyV2 : PostExtractionStrategy {

  private val log = LoggerFactory.getLogger(javaClass)
  private val rootScope = Scope.newEmptyScope()
  private final val postsQuery: JsonQuery

  init {
    BuiltinFunctionLoader.getInstance().loadFunctions(Version.LATEST, rootScope)

    // The ${'$'} interpolation is used solely to produce a $ character in the query string since
    // Kotlin doesn't provide character escapes for multi-line strings.
    // The alternative would be to use single-line string which would make the query unreadable 🙄
    postsQuery = JsonQuery.compile(
      """
      .. | objects | select(.__typename == "Story") |
          .post_id as ${'$'}post_id |
          (.. | objects | select(has("content")) | .content) as ${'$'}content |
          ([.. | objects | select(has("creation_time"))][0]) as ${'$'}metadata |
          (${'$'}content | .. | objects | select(.__typename == "TextWithEntities") | .text) as ${'$'}text |
              {
                  id: ${'$'}post_id,
                  published_at: ${'$'}metadata.creation_time,
                  url: ${'$'}metadata.url,
                  content: ${'$'}text
              }
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
      .apply {
        if (isEmpty()) log.warn("No posts found. Returned representation might have changed.")
        else if (size > 1) log.warn("More than one post found. Returned representation might have changed.")
      }

  private fun extractPostsFromObjectLiteral(objectLiteral: ObjectNode): Posts {
    val postData = mutableListOf<ObjectNode>()
    postsQuery.apply(rootScope, objectLiteral) { output ->
      if (output is ObjectNode) postData += output
    }

    val posts = postData.mapNotNull { extractPostFromQueryOutput(it) }
    if (posts.isNotEmpty())
      log.debug("Extracted posts {} from payload {}", posts, objectLiteral.toString())

    return posts
  }

  private fun extractPostFromQueryOutput(output: ObjectNode): Post? {
    val externalId = output.extractProperty("id", STRING) { FacebookPostId(it.textValue()) }
    val url = output.extractProperty("url", STRING) { toURL(it.textValue()) }
    val publishedAt = output.extractProperty("published_at", NUMBER) { Instant.ofEpochSecond(it.longValue()) }
    val content = output.extractProperty("content", STRING) { it.textValue().sanitizeContent() }

    return if (externalId == null || url == null || publishedAt == null || content == null) null
    else Post(
      externalId = externalId,
      url = url,
      publishedAt = publishedAt,
      content = content,
    )
  }

  private fun <T> JsonNode.extractProperty(name: String, expectedType: JsonNodeType, mapper: (JsonNode) -> T): T? {
    val propertyValue = get(name)

    if (propertyValue == null) {
      log.warn("Posts query output doesn't have expected property '{}'", name)
      return null
    } else if (propertyValue.nodeType != expectedType) {
      log.warn(
        "Property '{}' of posts query output is a {}, but {} was expected",
        name, propertyValue.nodeType, expectedType
      )
      return null
    }

    return runCatching { mapper(propertyValue) }
      .onFailure { log.warn("Error mapping property '{}' from value '{}'", name, propertyValue, it) }
      .getOrNull()
  }

  private fun String.sanitizeContent() =
    lineSequence()
      .map { it.trimEnd() }
      .joinToString("\n")
}

@VisibleForTesting
object JavaScriptObjectLiteralExtractor {

  private val log = LoggerFactory.getLogger(javaClass)
  private val mapper = JsonMapper()

  fun extractFrom(javascript: String) =
    // Is the script a JSON object literal by itself? If so, return it.
    toJsonObject(javascript)?.let { listOf(it) }
      ?:
      // No. Let's try parsing as JavaScript and extracting object literals.
      runCatching { objectLiteralsIn(javascript) }
        .onFailure { log.warn("Error parsing as JavaScript: {}", javascript, it) }
        .getOrDefault(emptyList())

  private fun toJsonObject(string: String) =
    runCatching { mapper.readValue(string, ObjectNode::class.java) }
      .getOrNull()

  private fun objectLiteralsIn(javascript: String): List<ObjectNode> {
    val accumulator = mutableListOf<ObjectNode>()
    val detector = objectLiteralDetector(javascript) { accumulator.add(it) }
    traverseAst(javascript, detector)
    return accumulator.toList()
  }

  private fun traverseAst(javascript: String, visitor: (AstNode) -> Boolean) =
    Parser().parse(javascript, null, 0).visit(visitor)

  private fun objectLiteralDetector(javascript: String, onObjectLiteralDetected: (ObjectNode) -> Unit) =
    fun(node: AstNode): Boolean {
      fun AstNode.code() = javascript.substring(absolutePosition, absolutePosition + length)

      if (log.isTraceEnabled) {
        val nodeType = node.javaClass.simpleName
        val nodePreview = node.code().oneLinePreview(80)
        log.trace("Now in {}: {}", nodeType, nodePreview)
      }
      if (node is ObjectLiteral) {
        val candidate = node.code()

        // We're in JavaScript world here, so object properties might have values being functions 🙃
        // We should only short-circuit traversal if the node is a plain JSON object, which we detect by trying
        // to parse the node with Jackson (which will choke on JavaScript extensions over JSON).
        toJsonObject(candidate)?.let {
          log.trace("Found object literal: {}", candidate)
          onObjectLiteralDetected(it)
          // Don't descend to children AST nodes - if we did, we'd find objects nested in the found one
          return false
        }
      }
      // Descend to children AST nodes in search of object literals
      return true
    }
}
