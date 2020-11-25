package dev.pankowski.garcon.persistence.jooq

import dev.pankowski.garcon.domain.Version
import spock.lang.Specification
import spock.lang.Subject

class VersionConverterTest extends Specification {

  @Subject
  VersionConverter converter = new VersionConverter()

  def "should convert null to null and back"() {
    expect:
    converter.from(null) == null

    and:
    converter.to(null) == null
  }

  def "should convert from number to version and back"() {
    expect:
    converter.from(versionNumber) == version

    and:
    converter.to(version) == versionNumber

    where:
    versionNumber | version
    1             | new Version(1)
    8             | new Version(8)
    0             | new Version(0)
    -27           | new Version(-27)
  }
}
