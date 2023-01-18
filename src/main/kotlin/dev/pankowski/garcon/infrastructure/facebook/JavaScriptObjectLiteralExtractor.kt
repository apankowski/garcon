package dev.pankowski.garcon.infrastructure.facebook

import com.fasterxml.jackson.databind.json.JsonMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import com.google.common.annotations.VisibleForTesting
import org.mozilla.javascript.Parser
import org.mozilla.javascript.ast.AstNode
import org.mozilla.javascript.ast.ObjectLiteral
import org.slf4j.LoggerFactory

@VisibleForTesting
object JavaScriptObjectLiteralExtractor {

  private val log = LoggerFactory.getLogger(javaClass)
  private val mapper = JsonMapper()

  fun extractFrom(javascript: String) =
    // Is the script a JSON object literal by itself? If so, return it.
    toJsonObject(javascript)?.let { listOf(it) }
      ?:
      // No. Let's try parsing as JavaScript and extracting object literals.
      runCatching { collectObjectLiteralsFrom(javascript) }
        .onFailure { log.warn("Error parsing as JavaScript: {}", javascript, it) }
        .getOrDefault(emptyList())

  private fun toJsonObject(string: String) =
    runCatching { mapper.readValue(string, ObjectNode::class.java) }
      .getOrNull()

  private fun collectObjectLiteralsFrom(javascript: String): List<ObjectNode> {
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
        val nodePreview = node.code().take(80)
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
