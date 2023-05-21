package dev.pankowski.garcon.configuration

import com.slack.api.Slack
import com.slack.api.methods.SlackApiException
import com.slack.api.util.http.listener.HttpResponseListener
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import com.slack.api.SlackConfig

// Declared Slack SDK beans are AutoCloseable. Therefore, Spring will close them on context shutdown.
@Configuration
class SlackConfiguration(
  // Base URL of Slack's Methods API. Not in application configuration as it's only used for testing.
  @Value("\${lunch.slack.api.methods-base-url:#{null}}") private val slackApiMethodsBaseUrl: String?,
) {

  @Bean
  fun slackConfig() =
    SlackConfig().apply {
      httpClientResponseHandlers.add(OkNotOkResponseHandler())
      if (slackApiMethodsBaseUrl != null) methodsEndpointUrlPrefix = slackApiMethodsBaseUrl
    }

  @Bean
  fun slackApi(): Slack =
    Slack.getInstance(slackConfig())

  /**
   * Handler of HTTP OK-but-not-"OK" Slack API responses, converting them to proper exceptions.
   *
   * There are cases when the Slack API returns HTTP OK 200 status code, but the `ok` flag in the
   * response is false and `error` field is set. These don't seem to be handled by
   * [com.slack.api.methods.impl.MethodsClientImpl.parseJsonResponseAndRunListeners], which kicks off response to
   * exception conversion only for _unsuccessful_ HTTP responses. Therefore, we detect cases of OK-not-OK responses
   * by ourselves and convert them to the usual Slack API exceptions.
   */
  private inner class OkNotOkResponseHandler : HttpResponseListener() {
    override fun accept(state: State) {
      if (state.response.isSuccessful) {
        val exception = SlackApiException(state.config, state.response, state.parsedResponseBody)
        if (!exception.error.isOk) throw exception
      }
    }
  }
}
