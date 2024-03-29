package dev.pankowski.garcon.domain

import org.slf4j.LoggerFactory.getLogger
import org.springframework.stereotype.Component

enum class Classification {
  LUNCH_POST,
  REGULAR_POST,
}

data class ClassifiedPost(
  val post: Post,
  val classification: Classification,
)

@Component
class LunchPostClassifier(private val postConfig: PostConfig) {

  private val log = getLogger(javaClass)

  fun classify(post: Post): Classification {
    val matcher = KeywordMatcher.onWordsOf(post.content, postConfig.locale)
    log.debug("Words extracted for post {}: {}", post, matcher.words)
    return if (matcher.matchesAny(postConfig.keywords)) Classification.LUNCH_POST
    else Classification.REGULAR_POST
  }

  fun classified(post: Post) =
    ClassifiedPost(post, classify(post))
}
