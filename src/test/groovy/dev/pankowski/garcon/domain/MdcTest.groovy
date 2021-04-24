package dev.pankowski.garcon.domain


import org.slf4j.MDC
import spock.lang.Specification

class MdcTest extends Specification {

  def "should allow setting PageId for a closure"() {
    given:
    def pageId = new PageId("P1234")

    expect:
    MDC.get("pageId") == null

    when:
    def capturedPageId = Mdc.PageId.INSTANCE.having(pageId, { MDC.get("pageId") })

    then:
    capturedPageId == pageId.value
    MDC.get("pageId") == null
  }

  def "should clear PageId when closure fails"() {
    when:
    Mdc.PageId.INSTANCE.having(
      new PageId("P1234"),
      { throw new RuntimeException("Something went wrong") }
    )

    then:
    thrown(RuntimeException)
    MDC.get("pageId") == null
  }
}
