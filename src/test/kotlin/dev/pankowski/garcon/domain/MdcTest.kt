package dev.pankowski.garcon.domain

import io.kotest.assertions.throwables.shouldThrowAny
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.nulls.beNull
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import org.slf4j.MDC

class MdcTest : FreeSpec({

  "allows setting page ID for a closure" {
    // given
    val id = PageId("1234")
    MDC.get("pageId") should beNull()

    // when
    val capturedValue = Mdc.PageId.having(id) { MDC.get("pageId") }

    // then
    capturedValue shouldBe id.value
    MDC.get("pageId") should beNull()
  }

  "clears page ID when closure fails" {
    // when
    shouldThrowAny {
      Mdc.PageId.having(PageId("1234")) {
        throw RuntimeException("Something went wrong")
      }
    }

    // then
    MDC.get("pageId") should beNull()
  }

  "allows setting synchronized post ID for a closure" {
    // given
    val id = SynchronizedPostId("1234")
    MDC.get("synchronizedPostId") should beNull()

    // when
    val capturedValue = Mdc.SynchronizedPostId.having(id) { MDC.get("synchronizedPostId") }

    // then
    capturedValue shouldBe id.value
    MDC.get("synchronizedPostId") should beNull()
  }

  "clears PageId when closure fails" {
    // when
    shouldThrowAny {
      Mdc.SynchronizedPostId.having(SynchronizedPostId("1234")) {
        throw RuntimeException("Something went wrong")
      }
    }

    // then
    MDC.get("synchronizedPostId") should beNull()
  }
})
