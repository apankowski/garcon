package dev.pankowski.garcon.domain

import java.util.*

data class Keyword(val text: String, val editDistance: Int)

class KeywordMatcher private constructor(
  val words: List<String>,
  private val locale: Locale,
) {

  companion object {
    @JvmStatic
    fun onWordsOf(text: String, locale: Locale) =
      KeywordMatcher(text.toLowerCase(locale).extractWords(locale), locale)
  }

  fun matches(keyword: Keyword) =
    words.any {
      damerauLevenshtein(it, keyword.text.toLowerCase(locale)) <= keyword.editDistance
    }
}
