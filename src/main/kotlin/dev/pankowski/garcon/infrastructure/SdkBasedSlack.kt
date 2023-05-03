package dev.pankowski.garcon.infrastructure

import com.slack.api.methods.request.chat.ChatPostMessageRequest
import dev.pankowski.garcon.domain.*
import org.slf4j.LoggerFactory.getLogger
import org.springframework.stereotype.Component
import com.slack.api.Slack as SlackApi

@Component
class SdkBasedSlack(private val slackConfig: SlackConfig, slackApi: SlackApi) : Slack {

  private val log = getLogger(javaClass)
  private val methodsApi = slackApi.methods(slackConfig.token)

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

    return methodsApi
      .chatPostMessage(request)
      .let { SlackMessageId(it.ts) }
  }
}
