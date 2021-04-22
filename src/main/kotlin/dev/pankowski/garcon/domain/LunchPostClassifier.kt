package dev.pankowski.garcon.domain

import org.slf4j.LoggerFactory.getLogger
import org.springframework.stereotype.Component

@Component
class LunchPostClassifier {

  private val log = getLogger(javaClass)

  // TODO: Make locale & keywords configurable
  private val locale = PolishLocale
  private val lunchKeywords = listOf(
    Keyword("lunch", 1),
    Keyword("lunchowa", 2)
  )

  fun classify(post: Post): Classification {
    val matcher = KeywordMatcher.onWordsOf(post.content, locale)
    log.debug("Words extracted for post {}: {}", post, matcher.words)
    return if (lunchKeywords.any { matcher.matches(it) }) Classification.LunchPost
    else Classification.MissingKeywords
  }
}
