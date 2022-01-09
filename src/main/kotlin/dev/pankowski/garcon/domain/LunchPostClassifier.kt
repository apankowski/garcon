package dev.pankowski.garcon.domain

import org.slf4j.LoggerFactory.getLogger
import org.springframework.stereotype.Component

@Component
class LunchPostClassifier(private val postConfig: PostConfig) {

  private val log = getLogger(javaClass)

  fun classify(post: Post): Classification {
    val matcher = KeywordMatcher.onWordsOf(post.content, postConfig.locale)
    log.debug("Words extracted for post {}: {}", post, matcher.words)
    return if (postConfig.keywords.any { matcher.matches(it) }) Classification.LunchPost
    else Classification.MissingKeywords
  }
}
