package dev.pankowski.garcon.api

import dev.pankowski.garcon.api.RequestSignatureVerifyingFilter.InMemoryRequest
import io.kotest.core.spec.style.FreeSpec
import io.kotest.datatest.withData
import io.kotest.matchers.shouldBe
import org.springframework.http.MediaType
import org.springframework.mock.web.MockHttpServletRequest
import java.nio.charset.Charset

class InMemoryRequestTest : FreeSpec({

  "provides body via input stream" {
    // given
    val body = "some test body".toByteArray()
    val originalRequest = MockHttpServletRequest()
    val inMemoryRequest = InMemoryRequest(originalRequest, body)

    // expect
    inMemoryRequest.inputStream.readBytes() shouldBe body
  }

  "provides body via reader" {
    // given
    val body = "some test body"
    val originalRequest = MockHttpServletRequest()
    val inMemoryRequest = InMemoryRequest(originalRequest, body.toByteArray())

    // expect
    inMemoryRequest.reader.readText() shouldBe body
  }

  "ensures reader consumes input stream" {
    // given
    val body = "some test body"
    val originalRequest = MockHttpServletRequest()
    val inMemoryRequest = InMemoryRequest(originalRequest, body.toByteArray())
    inMemoryRequest.inputStream.readNBytes("some test ".length)

    // expect
    inMemoryRequest.reader.readText() shouldBe "body"
  }

  "handles charsets" {
    withData<Charset>({ "handles $it charset" }, listOf(Charsets.UTF_8, Charsets.ISO_8859_1)) { charset ->
      // given
      val body = "ÄËÖäëö"
      val originalRequest = MockHttpServletRequest()
      originalRequest.contentType = MediaType.TEXT_PLAIN_VALUE + ";charset=" + charset.name()
      val inMemoryRequest = InMemoryRequest(originalRequest, body.toByteArray())

      // expect
      inMemoryRequest.reader.readText() shouldBe body
    }
  }

  "exposes query parameters" {
    // given
    val originalRequest = MockHttpServletRequest()
    originalRequest.setParameters(mapOf("param1" to arrayOf("abc", "def")))
    val inMemoryRequest = InMemoryRequest(originalRequest, ByteArray(0))

    // expect
    inMemoryRequest.parameterNames.toList() shouldBe setOf("param1")
    inMemoryRequest.parameterMap shouldBe mapOf("param1" to arrayOf("abc", "def"))
    inMemoryRequest.getParameter("param1") shouldBe "abc"
    inMemoryRequest.getParameterValues("param1") shouldBe arrayOf("abc", "def")
  }

  "exposes form parameters for form content type" {
    // given
    val body = "param1=value1&param3=value3"
    val originalRequest = MockHttpServletRequest()
    originalRequest.setParameters(mapOf("param1" to arrayOf("abc", "def")))
    originalRequest.setParameters(mapOf("param2" to arrayOf("xyz")))
    originalRequest.contentType = MediaType.APPLICATION_FORM_URLENCODED_VALUE
    val inMemoryRequest = InMemoryRequest(originalRequest, body.toByteArray())

    // expect
    inMemoryRequest.parameterNames.toList() shouldBe setOf("param1", "param2", "param3")
    inMemoryRequest.parameterMap shouldBe mapOf(
      "param1" to arrayOf("abc", "def", "value1"),
      "param2" to arrayOf("xyz"),
      "param3" to arrayOf("value3"),
    )
    inMemoryRequest.getParameter("param1") shouldBe "abc"
    inMemoryRequest.getParameterValues("param1") shouldBe arrayOf("abc", "def", "value1")
  }
})
