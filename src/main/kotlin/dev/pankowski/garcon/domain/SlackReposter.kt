package dev.pankowski.garcon.domain

import dev.pankowski.garcon.domain.LunchPageId
import dev.pankowski.garcon.domain.Post

interface SlackReposter {

  fun repost(post: Post, pageId: LunchPageId)
}
