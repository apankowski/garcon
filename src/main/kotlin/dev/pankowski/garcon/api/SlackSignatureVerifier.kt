package dev.pankowski.garcon.api

import com.google.common.hash.Hashing

class SlackSignatureVerifier(signingSecret: String) {

  private val hashFunction = Hashing.hmacSha256(signingSecret.toByteArray())

  interface Request {

    val body: ByteArray

    fun headerValue(name: String): String?
  }

  // See https://api.slack.com/authentication/verifying-requests-from-slack
  fun verify(request: Request): Boolean {
    val version = "v0"
    val timestamp = request.headerValue("X-Slack-Request-Timestamp") ?: ""
    val payload = request.body.toString(Charsets.UTF_8)

    val message = "$version:$timestamp:$payload"

    val actualSignature = request.headerValue("X-Slack-Signature") ?: ""
    val expectedSignature = "v0=" + hashFunction.hashString(message, Charsets.UTF_8).toString()
    return actualSignature.equals(expectedSignature, ignoreCase = true)
  }
}
