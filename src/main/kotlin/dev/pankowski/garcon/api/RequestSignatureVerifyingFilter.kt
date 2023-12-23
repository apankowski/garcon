package dev.pankowski.garcon.api

import com.google.common.annotations.VisibleForTesting
import org.slf4j.LoggerFactory.getLogger
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.converter.support.AllEncompassingFormHttpMessageConverter
import org.springframework.http.server.ServletServerHttpRequest
import org.springframework.util.LinkedMultiValueMap
import org.springframework.util.MultiValueMap
import org.springframework.web.filter.OncePerRequestFilter
import java.io.BufferedReader
import java.io.ByteArrayInputStream
import java.io.InputStreamReader
import java.io.OutputStream
import java.util.*
import java.util.Collections.enumeration
import jakarta.servlet.FilterChain
import jakarta.servlet.ReadListener
import jakarta.servlet.ServletInputStream
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletRequestWrapper
import jakarta.servlet.http.HttpServletResponse

class RequestSignatureVerifyingFilter(private val signatureVerifier: SlackSignatureVerifier) : OncePerRequestFilter() {

  companion object {
    private const val MAX_BODY_LENGTH = 100_000
    private val VERIFIED_HTTP_METHODS = setOf(HttpMethod.POST, HttpMethod.PUT, HttpMethod.PATCH)
  }

  private val log = getLogger(javaClass)

  override fun shouldNotFilter(request: HttpServletRequest) =
    request.method !in VERIFIED_HTTP_METHODS.map { it.name() }

  override fun doFilterInternal(request: HttpServletRequest, response: HttpServletResponse, filterChain: FilterChain) {
    val body = request.inputStream.readNBytes(MAX_BODY_LENGTH)
    if (!request.inputStream.isFinished) {
      log.trace("Rejecting request to ${request.requestURI} due to body exceeding $MAX_BODY_LENGTH bytes")
      response.sendError(
        HttpStatus.BAD_REQUEST.value(),
        "Request body exceeds maximum allowed length of $MAX_BODY_LENGTH"
      )
      response.flushBuffer()
      return
    }

    val hasCorrectSignature = signatureVerifier.verify(object : SlackSignatureVerifier.Request {
      override val body = body
      override fun headerValue(name: String) = request.getHeader(name)
    })
    if (!hasCorrectSignature) {
      log.trace("Rejecting request to ${request.requestURI} due to invalid signature")
      response.sendError(HttpStatus.UNAUTHORIZED.value(), "Invalid request signature")
      response.flushBuffer()
      return
    }

    filterChain.doFilter(InMemoryRequest(request, body), response)
  }

  @VisibleForTesting
  class InMemoryRequest(request: HttpServletRequest, body: ByteArray) : HttpServletRequestWrapper(request) {

    companion object {
      private val FormDataReader = AllEncompassingFormHttpMessageConverter()
    }

    private val characterEncoding = super.getCharacterEncoding() ?: Charsets.UTF_8.name()
    private val inputStream = ByteArrayInputStream(body)
    private val reader = BufferedReader(InputStreamReader(inputStream, characterEncoding))

    private val servletInputStream = object : ServletInputStream() {
      // Unfortunately we don't have the ability to compose implementations, so we have to delegate
      override fun close() = inputStream.close()
      override fun read() = inputStream.read()
      override fun read(b: ByteArray) = inputStream.read(b)
      override fun read(b: ByteArray, off: Int, len: Int) = inputStream.read(b, off, len)
      override fun readAllBytes() = inputStream.readAllBytes()
      override fun readNBytes(len: Int) = inputStream.readNBytes(len)
      override fun readNBytes(b: ByteArray, off: Int, len: Int) = inputStream.readNBytes(b, off, len)
      override fun skip(n: Long) = inputStream.skip(n)
      override fun skipNBytes(n: Long) = inputStream.skipNBytes(n)
      override fun available() = inputStream.available()
      override fun mark(readLimit: Int) = inputStream.mark(readLimit)
      override fun reset() = inputStream.reset()
      override fun markSupported() = inputStream.markSupported()
      override fun transferTo(out: OutputStream) = inputStream.transferTo(out)

      override fun isFinished() = inputStream.available() < 1
      override fun isReady() = true
      override fun setReadListener(listener: ReadListener) {
        throw IllegalStateException("In memory request doesn't support setting read listener")
      }
    }

    override fun getCharacterEncoding(): String = characterEncoding
    override fun getReader() = reader
    override fun getInputStream() = servletInputStream

    // Note: Since we've eaten the internal input stream of the delegate, super.getParameter* methods will only
    // return the query parameters. We have to supplement them with form parameters read from the (memorized) body
    // to fulfill their contract specified in ServletRequest.
    private val formData: MultiValueMap<String, String> by lazy {

      val isFormMediaType =
        runCatching { MediaType.parseMediaType(request.contentType) }
          .map { MediaType.APPLICATION_FORM_URLENCODED.includes(it) }
          .getOrDefault(false)

      if (isFormMediaType) {
        val message = object : ServletServerHttpRequest(request) {
          override fun getBody() = inputStream
        }
        FormDataReader.read(null, message)
      } else {
        LinkedMultiValueMap()
      }
    }

    override fun getParameter(name: String): String? =
      super.getParameter(name) ?: formData.getFirst(name)

    override fun getParameterNames(): Enumeration<String> =
      enumeration(super.getParameterNames().toList().toSet() + formData.keys)

    override fun getParameterValues(name: String): Array<String> =
      (super.getParameterValues(name) ?: emptyArray()) + (formData[name] ?: emptyList())

    override fun getParameterMap(): Map<String, Array<String>> =
      (super.getParameterMap().keys + formData.keys).associateWith { getParameterValues(it) }
  }
}
