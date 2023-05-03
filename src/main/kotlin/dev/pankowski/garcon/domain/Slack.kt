package dev.pankowski.garcon.domain

data class SlackMessageId(val value: String)

interface Slack {

  fun repost(post: Post, pageName: PageName): SlackMessageId
}
