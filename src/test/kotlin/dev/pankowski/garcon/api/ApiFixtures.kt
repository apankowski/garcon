package dev.pankowski.garcon.api

import java.net.URL

fun someLunchCommand(
  command: String = "/lunch",
  text: String = "some text",
  responseUrl: URL? = null,
  triggerId: TriggerId? = null,
  userId: UserId = UserId("Some UID"),
  channelId: ChannelId = ChannelId("Some Channel ID"),
  teamId: TeamId? = null,
  enterpriseId: EnterpriseId? = null,
) = SlashCommand(command, text, responseUrl, triggerId, userId, channelId, teamId, enterpriseId)

