package dev.pankowski.garcon.api.rest

import dev.pankowski.garcon.domain.*
import org.springframework.core.MethodParameter
import org.springframework.web.bind.MissingServletRequestParameterException
import org.springframework.web.bind.ServletRequestBindingException
import org.springframework.web.bind.support.WebDataBinderFactory
import org.springframework.web.context.request.NativeWebRequest
import org.springframework.web.method.support.HandlerMethodArgumentResolver
import org.springframework.web.method.support.ModelAndViewContainer
import java.net.URL

// Note: Thrown exceptions will be subject to Spring's exception to HTTP error mapping - by default
// done in DefaultHandlerExceptionResolver. By selecting types of thrown exceptions implementation
// can choose which HTTP status codes clients will receive.
class SlashCommandMethodArgumentResolver : HandlerMethodArgumentResolver {

  override fun supportsParameter(parameter: MethodParameter) =
    parameter.parameterType == SlashCommand::class.java

  override fun resolveArgument(
    parameter: MethodParameter,
    mavContainer: ModelAndViewContainer?,
    request: NativeWebRequest,
    binderFactory: WebDataBinderFactory?
  ): SlashCommand {

    val command = request.parameter("command", "string", identity(), ::required)!!
    val text = request.parameter("text", "string", identity(), ::required)!!
    val responseUrl = request.parameter("response_url", "URL", ::URL, ::nullIfEmpty)
    val triggerId = request.parameter("trigger_id", "trigger ID", ::TriggerId, ::nullIfEmpty)
    val userId = request.parameter("user_id", "user ID", ::UserId, ::required)!!
    val channelId = request.parameter("channel_id", "channel ID", ::ChannelId, ::required)!!
    val teamId = request.parameter("team_id", "team ID", ::TeamId, ::nullIfEmpty)
    val enterpriseId = request.parameter("enterprise_id", "enterprise ID", ::EnterpriseId, ::nullIfEmpty)

    return SlashCommand(
      command = command,
      text = text,
      responseUrl = responseUrl,
      triggerId = triggerId,
      userId = userId,
      channelId = channelId,
      teamId = teamId,
      enterpriseId = enterpriseId
    )
  }

  private fun <T> identity(): (T) -> T = { it }

  private fun <T> NativeWebRequest.parameter(
    name: String,
    type: String,
    converter: (String) -> T,
    ifEmpty: (String, String) -> T?
  ): T? {
    fun convert(value: String): T =
      try {
        converter(value)
      } catch (e: Exception) {
        throw ServletRequestBindingException(
          "Failed to convert value '$value' of parameter '$name' to $type", e)
      }

    val values = this.getParameterValues(name) ?: emptyArray()
    return when {
      values.isEmpty() || values.first().isNullOrEmpty() -> ifEmpty(name, type)
      else -> convert(values.first())
    }
  }

  private fun <T> required(name: String, type: String): T =
    throw MissingServletRequestParameterException(name, type)

  @Suppress("UNUSED_PARAMETER")
  private fun nullIfEmpty(name: String, type: String) = null
}

