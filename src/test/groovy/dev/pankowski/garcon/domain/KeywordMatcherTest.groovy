package dev.pankowski.garcon.domain

import spock.lang.Specification
import spock.lang.Unroll

class KeywordMatcherTest extends Specification {

  def locale = TextsKt.polishLocale

  @Unroll
  def '"some text" matches keyword "#keywordText" with edit distance #editDistance: #match'() {
    given:
    def text = "some text"
    def matcher = KeywordMatcher.onWordsOf(text, locale)
    def keyword = new Keyword(keywordText, editDistance)

    expect:
    matcher.matches(keyword) == match

    where:
    keywordText | editDistance | match
    "some"      | 0            | true
    "smoe"      | 1            | true
    "pxet"      | 2            | true
    "so"        | 2            | true
    "pxet"      | 1            | false
    "smoa"      | 1            | false
  }
}
