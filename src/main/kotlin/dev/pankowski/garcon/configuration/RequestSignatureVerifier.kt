package dev.pankowski.garcon.configuration

import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.web.filter.OncePerRequestFilter
import org.springframework.web.util.ContentCachingRequestWrapper
import java.security.Key
import java.util.*
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import javax.servlet.FilterChain
import javax.servlet.annotation.WebFilter
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse

@WebFilter
class RequestSignatureVerifier : OncePerRequestFilter() {

  val signingSecret = ""
  val key = SecretKeySpec(signingSecret.toByteArray(), "HmacSHA256")

  override fun shouldNotFilter(request: HttpServletRequest) =
    !HttpMethod.POST.matches(request.method)

  override fun doFilterInternal(request: HttpServletRequest, response: HttpServletResponse, filterChain: FilterChain) {
    var overflowed = false

    val contentCachedRequest = object : ContentCachingRequestWrapper(request, 100_000) {
      override fun handleContentOverflow(contentCacheLimit: Int) {
        response.sendError(HttpStatus.BAD_REQUEST.value(), "Request body exceeds maximum allowed length")
        response.flushBuffer()
        overflowed = true;
      }
    }

    val version = "v0"
    val timestamp = contentCachedRequest.getHeader("X-Slack-Request-Timestamp") ?: ""
    val payload = contentCachedRequest.reader.readText()
    if (overflowed) {
      return
    }

    val message = "$version:$timestamp:$payload"

    val expectedSignature = calculateMac(key, message)
    val actualSignature = contentCachedRequest.getHeader("X-Slack-Signature") ?: ""
    if (actualSignature != expectedSignature) {
      response.sendError(HttpStatus.UNAUTHORIZED.value(), "Invalid request signature")
      response.flushBuffer()
      return
    }

    filterChain.doFilter(contentCachedRequest, response)
  }

  private fun calculateMac(key: Key, message: String): String {
    val mac = Mac.getInstance("HmacSHA256")
    mac.init(key)
    mac.update(message.toByteArray())
    return Base64.getUrlEncoder().encodeToString(mac.doFinal())
  }
}
