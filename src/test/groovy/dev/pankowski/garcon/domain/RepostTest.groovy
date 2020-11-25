package dev.pankowski.garcon.domain

import spock.lang.Specification

import java.time.Instant

class RepostTest extends Specification {

  def "skipped repost should define its string representation"() {
    expect:
    Repost.Skip.INSTANCE.toString() == "Skip"
  }

  def "skipped repost should have skipped status"() {
    expect:
    Repost.Skip.INSTANCE.status == RepostStatus.SKIP
  }

  def "pending repost should define its string representation"() {
    expect:
    Repost.Pending.INSTANCE.toString() == "Pending"
  }

  def "pending repost should have pending status"() {
    expect:
    Repost.Pending.INSTANCE.status == RepostStatus.PENDING
  }

  def "error repost should have error status"() {
    given:
    def error = new Repost.Error(1, Instant.now())

    expect:
    error.status == RepostStatus.ERROR
  }

  def "successful repost should have success status"() {
    given:
    def success = new Repost.Success(Instant.now())

    expect:
    success.status == RepostStatus.SUCCESS
  }
}
