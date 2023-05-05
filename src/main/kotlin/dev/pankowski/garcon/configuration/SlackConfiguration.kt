package dev.pankowski.garcon.configuration

import com.google.common.annotations.VisibleForTesting
import com.slack.api.Slack
import com.slack.api.SlackConfig
import com.slack.api.methods.SlackApiException
import com.slack.api.util.http.listener.HttpResponseListener
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class SlackConfiguration(@VisibleForTesting baseSlackConfig: SlackConfig = SlackConfig()) {

  private val config = baseSlackConfig.apply {
    httpClientResponseHandlers.add(OkNotOkResponseHandler())
  }

  @Bean
  fun slackApi(): Slack =
    Slack.getInstance(config)

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
