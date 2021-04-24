package dev.pankowski.garcon.domain


import org.slf4j.MDC
import spock.lang.Specification

class MdcTest extends Specification {

  def "should allow setting page ID for a closure"() {
    given:
    def id = new PageId("1234")

    expect:
    MDC.get("pageId") == null

    when:
    def capturedValue = Mdc.PageId.INSTANCE.having(id, { MDC.get("pageId") })

    then:
    capturedValue == id.value
    MDC.get("pageId") == null
  }

  def "should clear page ID when closure fails"() {
    when:
    Mdc.PageId.INSTANCE.having(
      new PageId("1234"),
      { throw new RuntimeException("Something went wrong") }
    )

    then:
    thrown(RuntimeException)
    MDC.get("pageId") == null
  }

  def "should allow setting synchronized post ID for a closure"() {
    given:
    def id = new SynchronizedPostId("1234")

    expect:
    MDC.get("synchronizedPostId") == null

    when:
    def capturedValue = Mdc.SynchronizedPostId.INSTANCE.having(id, { MDC.get("synchronizedPostId") })

    then:
    capturedValue == id.value
    MDC.get("synchronizedPostId") == null
  }

  def "should clear PageId when closure fails"() {
    when:
    Mdc.SynchronizedPostId.INSTANCE.having(
      new SynchronizedPostId("1234"),
      { throw new RuntimeException("Something went wrong") }
    )

    then:
    thrown(RuntimeException)
    MDC.get("synchronizedPostId") == null
  }
}
