package dev.pankowski.garcon.infrastructure.slack

import com.slack.api.methods.request.chat.ChatPostMessageRequest
import dev.pankowski.garcon.domain.*
import org.slf4j.LoggerFactory.getLogger
import org.springframework.stereotype.Component
import com.slack.api.methods.MethodsClient as SlackMethodsApi

@Component
class SdkBasedSlack(
  private val slackConfig: SlackConfig,
  private val slackMethodsApi: SlackMethodsApi,
) : Slack {

  private val log = getLogger(javaClass)

  override fun repost(post: Post, pageName: PageName): SlackMessageId {
    log.debug("Reposting on Slack: {}", post)

    val request = ChatPostMessageRequest.builder()
      .channel(slackConfig.channel)
      .text(
        """
        |New <${post.url}|lunch post> from *${pageName.value}* :tada:
        |
        |>>>${post.content}
        """.trimMargin()
      )
      .build()

    return slackMethodsApi
      .chatPostMessage(request)
      .let { SlackMessageId(it.ts) }
  }
}
