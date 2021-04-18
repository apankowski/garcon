package dev.pankowski.garcon.persistence

import dev.pankowski.garcon.domain.*
import java.time.Instant
import java.util.*

open class InMemorySynchronizedPostRepository : SynchronizedPostRepository {

  private val posts = mutableMapOf<SynchronizedPostId, SynchronizedPost>()

  override fun store(data: StoreData): SynchronizedPostId {
    val synchronizedPostId = SynchronizedPostId(UUID.randomUUID().toString())
    val now = Instant.now()
    val synchronizedPost = SynchronizedPost(
      synchronizedPostId,
      Version.first(),
      now,
      now,
      data.pageId,
      data.post,
      data.classification,
      data.repost
    )

    posts[synchronizedPost.id] = synchronizedPost

    return synchronizedPost.id
  }

  override fun updateExisting(data: UpdateData) {
    val synchronizedPost = findExisting(data.id)

    if (synchronizedPost.version != data.version) {
      throw IllegalArgumentException(
        "Cannot update post with ${data.id} and ${data.version} as it has been already updated"
      )
    }

    posts[synchronizedPost.id] = synchronizedPost.copy(repost = data.repost)
  }

  override fun findExisting(id: SynchronizedPostId): SynchronizedPost =
    posts[id] ?: throw IllegalArgumentException("Post with $id does not exist")

  override fun findLastSeen(pageId: LunchPageId): SynchronizedPost? =
    posts.values.filter { it.pageId == pageId }.maxByOrNull { it.post.publishedAt }

  override fun getLog(count: Int): SynchronizationLog =
    TODO("Not implemented")
}
