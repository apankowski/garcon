package dev.pankowski.garcon.configuration

import dev.pankowski.garcon.api.RequestSignatureVerifier
import dev.pankowski.garcon.domain.SlackConfig
import org.slf4j.LoggerFactory.getLogger
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class SlackConfiguration {

  private val log = getLogger("SlackConfiguration")

  @Bean
  @ConditionalOnProperty("lunch.slack.signing-secret")
  fun requestSignatureVerifier(slackConfig: SlackConfig): RequestSignatureVerifier {
    log.info("Signing secret is set - incoming requests will be verified")
    return RequestSignatureVerifier(slackConfig.signingSecret!!)
  }
}
