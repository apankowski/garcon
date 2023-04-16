package dev.pankowski.garcon.infrastructure

import com.google.common.annotations.VisibleForTesting
import dev.pankowski.garcon.domain.*
import org.slf4j.LoggerFactory.getLogger
import org.springframework.boot.web.client.RestTemplateBuilder
import org.springframework.stereotype.Component
import org.springframework.web.client.RestTemplate

@Component
class RestTemplateSlack(private val slackConfig: SlackConfig, restTemplateBuilder: RestTemplateBuilder) : Slack {

  private val log = getLogger(javaClass)

  @VisibleForTesting
  val restTemplate: RestTemplate = restTemplateBuilder.build()

  override fun repost(post: Post, pageName: PageName) {
    log.debug("Reposting on Slack: {}", post)
    val text =
      """
      |New <${post.url}|lunch post> from *${pageName.value}* :tada:
      |
      |>>>${post.content}
      """.trimMargin()

    val message = SlackMessage(text = text)

    restTemplate.postForLocation(slackConfig.webhookUrl.toURI(), message)
  }
}
