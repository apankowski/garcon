package dev.pankowski.garcon.domain

import spock.lang.Specification

import static dev.pankowski.garcon.domain.TextsKt.extractWords

class WordExtractorTest extends Specification {

  def locale = TextsKt.polishLocale

  def 'should extract words'() {
    given:
    // text

    expect:
    extractWords(text, locale) == words

    where:
    text                | words
    "This is some text" | ["This", "is", "some", "text"]
    " "                 | []
    "\n"                | []
    " Abc "             | ["Abc"]
  }

  def 'should ignore punctuation'() {
    given:
    // text

    expect:
    extractWords(text, locale) == words

    where:
    text            | words
    "Hello!"        | ["Hello"]
    "Hello, sir!"   | ["Hello", "sir"]
    "...what, now?" | ["what", "now"]
  }

  def 'should handle language-specific characters'() {
    given:
    // text

    expect:
    extractWords(text, locale) == words

    where:
    text                      | words
    " Cześć! ! "              | ["Cześć"]
    "Zażółć +gęślą? Jaźń %% " | ["Zażółć", "gęślą", "Jaźń"]
  }
}
