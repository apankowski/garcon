package dev.pankowski.garcon.configuration

import dev.pankowski.garcon.api.MessagePayload
import dev.pankowski.garcon.domain.LunchConfig
import dev.pankowski.garcon.domain.LunchPageId
import dev.pankowski.garcon.domain.Post
import dev.pankowski.garcon.domain.SlackReposter
import org.slf4j.LoggerFactory.getLogger
import org.springframework.boot.web.client.RestTemplateBuilder
import org.springframework.stereotype.Component
import org.springframework.web.client.RestTemplate

@Component
class RestTemplateSlackReposter(private val lunchConfig: LunchConfig, restTemplateBuilder: RestTemplateBuilder) :
  SlackReposter {

  private val log = getLogger(javaClass)

  // Visible for testing
  val restTemplate: RestTemplate = restTemplateBuilder.build()

  override fun repost(post: Post, pageId: LunchPageId) {
    log.debug("Reposting on Slack: {}", post)
    val text =
      """
      |New <${post.link}|lunch post> from ${pageId.value} :tada:
      |
      |>>>${post.content}
      """.trimMargin()

    val message = MessagePayload(text = text)

    restTemplate.postForLocation(lunchConfig.slackWebhookUrl.toURI(), message)
  }
}
