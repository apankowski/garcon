package dev.pankowski.garcon.api

import dev.pankowski.garcon.domain.SlackConfig
import org.slf4j.LoggerFactory.getLogger
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.stereotype.Component
import org.springframework.web.method.support.HandlerMethodArgumentResolver
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

@Configuration
class ApiConfiguration {

  private val log = getLogger("ApiConfiguration")

  @Bean
  fun webMvcConfigurer(): WebMvcConfigurer =
    @Component
    object : WebMvcConfigurer {
      override fun addArgumentResolvers(resolvers: MutableList<HandlerMethodArgumentResolver>) {
        resolvers.add(SlashCommandMethodArgumentResolver())
      }
    }

  @Bean
  @ConditionalOnProperty("lunch.slack.signing-secret")
  fun requestSignatureVerifyingFilter(slackConfig: SlackConfig): RequestSignatureVerifyingFilter {
    log.info("Signing secret is set - incoming requests will be verified")
    val signatureVerifier = SlackSignatureVerifier(slackConfig.signingSecret!!)
    return RequestSignatureVerifyingFilter(signatureVerifier)
  }
}
