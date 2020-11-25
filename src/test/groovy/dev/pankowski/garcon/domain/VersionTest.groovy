package dev.pankowski.garcon.domain

import dev.pankowski.garcon.domain.Version
import spock.lang.Specification

class VersionTest extends Specification {

  def "first version should be 1"() {
    given:
    def version = Version.first()

    expect:
    version.value == 1
  }

  def "next version should increment by 1"() {
    given:
    def version = new Version(2)

    expect:
    version.next().value == 3
  }
}
