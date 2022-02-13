package dev.pankowski.garcon.api

import com.google.common.hash.Hashing
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.web.filter.OncePerRequestFilter
import org.springframework.web.util.ContentCachingRequestWrapper
import javax.servlet.FilterChain
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse

class RequestSignatureVerifier(signingSecret: String) : OncePerRequestFilter() {

  private val hashFunction = Hashing.hmacSha256(signingSecret.toByteArray())

  override fun shouldNotFilter(request: HttpServletRequest) =
    !HttpMethod.POST.matches(request.method)

  override fun doFilterInternal(request: HttpServletRequest, response: HttpServletResponse, filterChain: FilterChain) {
    var overflowed = false

    val contentCachedRequest = object : ContentCachingRequestWrapper(request, 100_000) {
      override fun handleContentOverflow(contentCacheLimit: Int) {
        overflowed = true;
      }
    }

    // See https://api.slack.com/authentication/verifying-requests-from-slack
    val version = "v0"
    val timestamp = contentCachedRequest.getHeader("X-Slack-Request-Timestamp") ?: ""
    val payload = contentCachedRequest.reader.readText()
    if (overflowed) {
      response.sendError(HttpStatus.BAD_REQUEST.value(), "Request body exceeds maximum allowed length")
      response.flushBuffer()
      return
    }

    val message = "$version:$timestamp:$payload"

    val actualSignature = contentCachedRequest.getHeader("X-Slack-Signature") ?: ""
    val expectedSignature = "v0=" + hashFunction.hashString(message, Charsets.UTF_8).toString()
    if (!actualSignature.equals(expectedSignature, ignoreCase = true)) {
      response.sendError(HttpStatus.UNAUTHORIZED.value(), "Invalid request signature")
      response.flushBuffer()
      return
    } else {
      filterChain.doFilter(contentCachedRequest, response)
    }
  }
}
