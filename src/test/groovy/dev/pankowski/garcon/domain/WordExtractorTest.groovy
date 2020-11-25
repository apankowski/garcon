package dev.pankowski.garcon.domain

import spock.lang.Specification
import spock.lang.Subject

class WordExtractorTest extends Specification {

  @Subject
  WordExtractor extractor = new WordExtractor(TextsKt.polishLocale)

  def 'should extract words'() {
    given:
    // text

    expect:
    extractor.extract(text) == words

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
    extractor.extract(text) == words

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
    extractor.extract(text) == words

    where:
    text                      | words
    " Cześć! ! "              | ["Cześć"]
    "Zażółć +gęślą? Jaźń %% " | ["Zażółć", "gęślą", "Jaźń"]
  }
}
