package dev.pankowski.garcon.domain

enum class ResponseType(private val apiName: String) {
  EPHEMERAL("ephemeral"),
  IN_CHANNEL("in_channel"),
  ;

  override fun toString(): String = apiName
}

data class Attachment(
  val text: String
)

/**
 * Slack's [Message Payload](https://api.slack.com/reference/messaging/payload).
 */
data class SlackMessage(
  val text: String,
  val responseType: ResponseType? = null,
  val attachments: List<Attachment>? = null
)
