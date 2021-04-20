package dev.pankowski.garcon.domain

interface SlackReposter {

  fun repost(post: Post, pageId: LunchPageId)
}
