package dev.pankowski.garcon.domain

import spock.lang.Specification

class ClassificationTest extends Specification {

  def "lunch post classification should have proper string representation"() {
    expect:
    Classification.LunchPost.INSTANCE.toString() == "LunchPost"
  }

  def "lunch post classification should have proper status"() {
    expect:
    Classification.LunchPost.INSTANCE.status == ClassificationStatus.LUNCH_POST
  }

  def "missing keywords classification should have proper string representation"() {
    expect:
    Classification.MissingKeywords.INSTANCE.toString() == "MissingKeywords"
  }

  def "missing keywords classification should have proper status"() {
    expect:
    Classification.MissingKeywords.INSTANCE.status == ClassificationStatus.MISSING_KEYWORDS
  }
}
