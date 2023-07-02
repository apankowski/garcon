package dev.pankowski.garcon.domain

import io.kotest.assertions.throwables.shouldThrowAny
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.nulls.beNull
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import org.slf4j.MDC

class MdcTest : FreeSpec({

  "allows setting page key for a closure" {
    // given
    val id = PageKey("SomePageKey")
    MDC.get("pageKey") should beNull()

    // when
    val capturedValue = Mdc.extendedWith(id) { MDC.get("pageKey") }

    // then
    capturedValue shouldBe id.value
    MDC.get("pageKey") should beNull()
  }

  "clears page key when closure fails" {
    // when
    shouldThrowAny {
      Mdc.extendedWith(PageKey("SomePageKey")) {
        throw RuntimeException("Something went wrong")
      }
    }

    // then
    MDC.get("pageKey") should beNull()
  }

  "allows setting synchronized post ID for a closure" {
    // given
    val id = SynchronizedPostId("1234")
    MDC.get("synchronizedPostId") should beNull()

    // when
    val capturedValue = Mdc.extendedWith(id) { MDC.get("synchronizedPostId") }

    // then
    capturedValue shouldBe id.value
    MDC.get("synchronizedPostId") should beNull()
  }

  "clears synchronized post ID when closure fails" {
    // when
    shouldThrowAny {
      Mdc.extendedWith(SynchronizedPostId("1234")) {
        throw RuntimeException("Something went wrong")
      }
    }

    // then
    MDC.get("synchronizedPostId") should beNull()
  }
})
