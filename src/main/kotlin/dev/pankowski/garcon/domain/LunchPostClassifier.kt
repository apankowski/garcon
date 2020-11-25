package dev.pankowski.garcon.domain

import org.slf4j.LoggerFactory.getLogger
import org.springframework.stereotype.Component

@Component
class LunchPostClassifier {

  private val log = getLogger(javaClass)
  private val locale = PolishLocale
  private val wordExtractor = WordExtractor(locale)
  private val lunchKeywords = listOf("lunch", "lunchowa")
  private val maxEditDistance = 1

  fun classify(post: FacebookPost): Classification {
    val words = wordExtractor.extract(post.content.toLowerCase(locale))
    log.debug("Finished word extraction for text: {}. Extracted words: {}", post.content, words)
    return if (words.any(::isConsideredLunchKeyword)) Classification.LunchPost
    else Classification.MissingKeywords
  }

  private fun isConsideredLunchKeyword(w: String) =
    lunchKeywords.any { k -> damerauLevenshtein(k, w) <= maxEditDistance }
}
