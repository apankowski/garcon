package dev.pankowski.garcon.infrastructure

import com.slack.api.Slack
import com.slack.api.methods.request.chat.ChatPostMessageRequest
import dev.pankowski.garcon.domain.PageName
import dev.pankowski.garcon.domain.SlackMessageId
import dev.pankowski.garcon.domain.somePost
import dev.pankowski.garcon.domain.someSlackConfig
import io.kotest.assertions.assertSoftly
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import java.net.URL

class SdkBasedSlackTest : FreeSpec({

  "reposts synchronized post via Slack API Client" {
    // given
    val pageName = PageName("Some page name")
    val post = somePost(
      url = URL("https://www.facebook.com/post"),
      content = "Some post content",
    )

    // and
    val slackConfig = someSlackConfig(token = "xoxb-token", channel = "#channel")
    val requestSlot = slot<ChatPostMessageRequest>()
    val slackApi = mockk<Slack> {
      every { methods("xoxb-token").chatPostMessage(capture(requestSlot)).ts } returns "some-message-id"
    }
    val slack = SdkBasedSlack(slackConfig, slackApi)

    // when
    val slackMessageId = slack.repost(post, pageName)

    // then
    assertSoftly {
      requestSlot.captured.channel shouldBe "#channel"
      requestSlot.captured.text shouldBe
        """
        |New <https://www.facebook.com/post|lunch post> from *Some page name* :tada:
        |
        |>>>Some post content
        """.trimMargin()

      slackMessageId shouldBe SlackMessageId("some-message-id")
    }
  }
})
