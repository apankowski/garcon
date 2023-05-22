package dev.pankowski.garcon.configuration

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.ok
import com.github.tomakehurst.wiremock.client.WireMock.post
import com.github.tomakehurst.wiremock.matching.UrlPattern
import com.slack.api.methods.SlackApiException
import com.slack.api.methods.request.chat.ChatPostMessageRequest
import dev.pankowski.garcon.domain.someSlackConfig
import io.kotest.assertions.assertSoftly
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FreeSpec
import io.kotest.extensions.wiremock.ListenerMode
import io.kotest.extensions.wiremock.WireMockListener
import io.kotest.matchers.shouldBe

class SlackConfigurationTest : FreeSpec({

  val server = WireMockServer(4321)
  listener(WireMockListener(server, ListenerMode.PER_SPEC))

  "converts OK-not-OK responses to Slack API exceptions" {
    // given
    server.givenThat(
      post(UrlPattern.ANY)
        .willReturn(ok("""{"ok": false, "error": "some_error"}"""))
    )

    // and
    val slackConfig = someSlackConfig(methodsApiBaseUrl = server.url("/"))
    val slackMethodsApi = SlackConfiguration(slackConfig).slackMethodsApi()
    val request = ChatPostMessageRequest.builder().text("some text").build()

    // when
    val exception = shouldThrow<SlackApiException> {
      slackMethodsApi.chatPostMessage(request)
    }

    // then
    assertSoftly(exception) {
      exception.error.isOk shouldBe false
      exception.error.error shouldBe "some_error"
    }
  }
})
