package dev.pankowski.garcon.infrastructure

import dev.pankowski.garcon.domain.*
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

  override fun repost(post: Post, pageName: PageName) {
    log.debug("Reposting on Slack: {}", post)
    val text =
      """
      |New <${post.link}|lunch post> from ${pageName.value} :tada:
      |
      |>>>${post.content}
      """.trimMargin()

    val message = SlackMessage(text = text)

    restTemplate.postForLocation(lunchConfig.slackWebhookUrl.toURI(), message)
  }
}
