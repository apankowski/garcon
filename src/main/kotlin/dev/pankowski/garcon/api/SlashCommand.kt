package dev.pankowski.garcon.api

import java.net.URL

// Inline classes might be a better fit but they are experimental.
data class TriggerId(val id: String)

data class UserId(val id: String)

data class ChannelId(val id: String)

data class TeamId(val id: String)

data class EnterpriseId(val id: String)

data class SlashCommand(
  val command: String,
  val text: String,
  val responseUrl: URL?,
  val triggerId: TriggerId?,
  val userId: UserId,
  val channelId: ChannelId,
  val teamId: TeamId?,
  val enterpriseId: EnterpriseId?
)
