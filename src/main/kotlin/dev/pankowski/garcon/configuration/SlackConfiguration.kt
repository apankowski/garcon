package dev.pankowski.garcon.configuration

import dev.pankowski.garcon.api.RequestSignatureVerifyingFilter
import dev.pankowski.garcon.api.SlackSignatureVerifier
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
  fun requestSignatureVerifyingFilter(slackConfig: SlackConfig): RequestSignatureVerifyingFilter {
    log.info("Signing secret is set - incoming requests will be verified")
    val signatureVerifier = SlackSignatureVerifier(slackConfig.signingSecret!!)
    return RequestSignatureVerifyingFilter(signatureVerifier)
  }
}
