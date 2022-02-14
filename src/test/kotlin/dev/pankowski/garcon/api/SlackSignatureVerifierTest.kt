package dev.pankowski.garcon.api

import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe

class SlackSignatureVerifierTest : FreeSpec({

  fun fakeRequest(timestamp: String, signature: String, body: String) =
    object : SlackSignatureVerifier.Request {
      private val fakeHeaders = mapOf(
        "X-Slack-Request-Timestamp" to timestamp,
        "X-Slack-Signature" to signature,
      )
      override val body = body.toByteArray()
      override fun headerValue(name: String) = fakeHeaders[name]
    }

  "is aligned with example from Slack API documentation" {
    // given
    val signingSecret = "8f742231b10e8888abcd99yyyzzz85a5"
    val timestamp = "1531420618"
    val body = "token=xyzz0WbapA4vBCDEFasx0q6G&team_id=T1DC2JH3J&team_domain=testteamnow&channel_id=G8PSS9T3V&channel_name=foobar&user_id=U2CERLKJA&user_name=roadrunner&command=%2Fwebhook-collect&text=&response_url=https%3A%2F%2Fhooks.slack.com%2Fcommands%2FT1DC2JH3J%2F397700885554%2F96rGlfmibIGlgcZRskXaIFfN&trigger_id=398738663015.47445629121.803a0bc887a14d10d2c447fce8b6703c"
    val signature = "v0=a2114d57b48eac39b9ad189dd8316235a7b4a8d21a10bd27519666489c69b503"

    // and
    val request = fakeRequest(timestamp, signature, body)
    val verifier = SlackSignatureVerifier(signingSecret)

    // expect
    verifier.verify(request) shouldBe true
  }

  fun verify(
    signingSecret: String = "8f742231b10e8888abcd99yyyzzz85a5",
    timestamp: String = "1531420618",
    signature: String = "v0=8a2b7124f7f40357c2227bb1175c525fc1840d77da1a54b37028e17d1dc33503",
    body: String = "token=xyzz0WbapA4vBCDEFasx0q6G",
  ) =
    SlackSignatureVerifier(signingSecret).verify(fakeRequest(timestamp, signature, body))

  "approves properly signed request" { verify() shouldBe true }

  "rejects when" - {
    "signing secret doesn't match" { verify(signingSecret = "x") shouldBe false }
    "timestamp doesn't match" { verify(timestamp = "x") shouldBe false }
    "body doesn't match" { verify(body = "x") shouldBe false }
    "signature doesn't match" { verify(signature = "x") shouldBe false }
  }
})
