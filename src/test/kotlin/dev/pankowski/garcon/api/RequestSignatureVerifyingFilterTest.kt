package dev.pankowski.garcon.api

import dev.pankowski.garcon.api.RequestSignatureVerifyingFilter.InMemoryRequest
import io.kotest.core.spec.IsolationMode
import io.kotest.core.spec.style.FreeSpec
import io.kotest.datatest.withData
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.mockk.Called
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.mock.web.MockFilterChain
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import java.util.*
import javax.servlet.Servlet

class RequestSignatureVerifyingFilterTest : FreeSpec({

  isolationMode = IsolationMode.InstancePerLeaf

  val request = MockHttpServletRequest()
  val response = MockHttpServletResponse()
  val servlet = mockk<Servlet>()
  val verifier = mockk<SlackSignatureVerifier>()
  val filter = RequestSignatureVerifyingFilter(verifier)

  fun runFilterChain() = MockFilterChain(servlet, filter).doFilter(request, response)

  val verifiedMethods = EnumSet.of(HttpMethod.POST, HttpMethod.PUT, HttpMethod.PATCH)

  "verifies some methods" - {
    withData<HttpMethod>({ "verifies $it method" }, verifiedMethods) { method ->
      // given
      request.method = method.name
      every { verifier.verify(any()) } returns false

      // when
      runFilterChain()

      // then
      verify {
        verifier.verify(any())
      }
    }
  }

  "doesn't verify some methods" - {
    withData<HttpMethod>({ "doesn't verify $it method" }, EnumSet.complementOf(verifiedMethods)) { method ->
      // given
      request.method = method.name
      every { servlet.service(any(), any()) } returns Unit

      // when
      runFilterChain()

      // then
      verify {
        verifier wasNot Called
        servlet.service(request, response)
      }
    }
  }

  "rejects request with too large body" {
    // given
    request.method = HttpMethod.POST.name
    request.setContent(ByteArray(100_001))

    // when
    runFilterChain()

    // then
    response.status shouldBe HttpStatus.BAD_REQUEST.value()
    response.errorMessage shouldContain "Request body exceeds maximum allowed length"

    verify {
      verifier wasNot Called
      servlet wasNot Called
    }
  }

  "rejects request with incorrect signature" {
    // given
    request.method = HttpMethod.POST.name
    every { verifier.verify(any()) } returns false

    // when
    runFilterChain()

    // then
    response.status shouldBe HttpStatus.UNAUTHORIZED.value()
    response.errorMessage shouldContain "Invalid request signature"

    verify {
      verifier.verify(any())
      servlet wasNot Called
    }
  }

  "accepts request with correct signature" {
    // given
    request.method = HttpMethod.POST.name
    every { verifier.verify(any()) } returns true
    every { servlet.service(any(), any()) } returns Unit

    // when
    runFilterChain()

    // then
    verify {
      servlet.service(match { it is InMemoryRequest }, response)
    }
  }
})
