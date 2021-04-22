package dev.pankowski.garcon.domain

import spock.lang.Specification

import static dev.pankowski.garcon.domain.TextsKt.extractWords

class WordExtractionTest extends Specification {

  def locale = new Locale("pl", "PL")

  def 'should extract words'() {
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
    expect:
    extractWords(text, locale) == words

    where:
    text            | words
    "Hello!"        | ["Hello"]
    "Hello, sir!"   | ["Hello", "sir"]
    "...what, now?" | ["what", "now"]
  }

  def 'should handle language-specific characters'() {
    expect:
    extractWords(text, locale) == words

    where:
    text                      | words
    " Cześć! ! "              | ["Cześć"]
    "Zażółć +gęślą? Jaźń %% " | ["Zażółć", "gęślą", "Jaźń"]
  }
}
