package dev.pankowski.garcon.domain

import org.slf4j.LoggerFactory.getLogger
import org.springframework.boot.web.client.RestTemplateBuilder
import org.springframework.stereotype.Component
import org.springframework.web.client.RestTemplate

@Component
class LunchReposter(private val lunchConfig: LunchConfig, restTemplateBuilder: RestTemplateBuilder) {

  private val log = getLogger(javaClass)

  // Visible for testing
  val restTemplate: RestTemplate = restTemplateBuilder.build()

  fun repost(post: FacebookPost, pageId: LunchPageId) {
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
