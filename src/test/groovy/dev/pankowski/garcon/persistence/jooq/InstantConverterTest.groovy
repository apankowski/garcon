package dev.pankowski.garcon.persistence.jooq

import spock.lang.Specification
import spock.lang.Subject

import java.time.Instant
import java.time.LocalDateTime

class InstantConverterTest extends Specification {

  @Subject
  InstantConverter converter = new InstantConverter()

  def "should convert null to null and back"() {
    expect:
    converter.from(null) == null

    and:
    converter.to(null) == null
  }

  def "should convert from local date-time to instant and back assuming UTC"() {
    expect:
    converter.from(LocalDateTime.parse(localDateTime)) == Instant.ofEpochSecond(epochSecond)

    and:
    converter.to(Instant.ofEpochSecond(epochSecond)) == LocalDateTime.parse(localDateTime)

    where:
    localDateTime         | epochSecond
    "2000-01-01T00:00:00" | 946684800
    "2000-01-01T00:00:01" | 946684801
    "2000-01-01T00:01:00" | 946684860
    "2000-01-01T01:00:00" | 946688400
    "2000-01-02T00:00:00" | 946771200
    "2000-02-01T00:00:00" | 949363200
    "2001-01-01T00:00:00" | 978307200
  }
}
