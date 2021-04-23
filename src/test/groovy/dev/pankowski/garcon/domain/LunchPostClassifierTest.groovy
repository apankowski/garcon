package dev.pankowski.garcon.domain

import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Unroll

import java.time.Instant

class LunchPostClassifierTest extends Specification {

  def postConfig = new LunchPostConfig(
    new Locale("pl", "PL"),
    [new Keyword("lunch", 1), new Keyword("lunchowa", 2)]
  )

  @Subject
  def classifier = new LunchPostClassifier(postConfig)

  private static def somePost(String content) {
    new Post(
      new ExternalId("SomeId"),
      URI.create("https://www.facebook.com/"),
      Instant.now(),
      content
    )
  }

  @Unroll
  def 'should reject post having content without lunch keyword: #content'() {
    given:
    def post = somePost(content)

    when:
    def result = classifier.classify(post)

    then:
    result == Classification.MissingKeywords.INSTANCE

    where:
    content << ["Some text", "Zapraszamy na pyszną świeżą sielawę"]
  }

  @Unroll
  def 'should accept post having content with lunch keyword: #content'() {
    given:
    def post = somePost(content)

    when:
    def result = classifier.classify(post)

    then:
    result == Classification.LunchPost.INSTANCE

    where:
    content << ["Lunch wtorek", "jemy lunch", "dzisiejsza oferta lunchowa", "lunch!!!", "**Lunch**", "😆😆😆lunch😆😆😆"]
  }

  @Unroll
  def 'should accept post having content with misspelled lunch keyword: #content'() {
    given:
    def post = somePost(content)

    when:
    def result = classifier.classify(post)

    then:
    result == Classification.LunchPost.INSTANCE

    where:
    content << ["luunch", "Lnuch", "dzisiejsza oferta lunhcowa"]
  }
}
