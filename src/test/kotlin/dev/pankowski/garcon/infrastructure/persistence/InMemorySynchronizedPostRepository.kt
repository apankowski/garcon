package dev.pankowski.garcon.infrastructure.persistence

import dev.pankowski.garcon.domain.*
import java.time.Duration
import java.util.*

open class InMemorySynchronizedPostRepository : SynchronizedPostRepository {

  private val posts = mutableMapOf<SynchronizedPostId, SynchronizedPost>()

  fun put(synchronizedPost: SynchronizedPost) {
    posts[synchronizedPost.id] = synchronizedPost
  }

  override fun store(data: StoreData): SynchronizedPostId {
    if (findByExternalId(data.post.externalId) != null)
      throw SynchronizedPostHasDuplicateExternalId(
        "Failed to store synchronized post due to duplicate external ID ${data.post.externalId.value}"
      )

    val synchronizedPostId = SynchronizedPostId(UUID.randomUUID().toString())
    val now = now()
    val synchronizedPost = SynchronizedPost(
      synchronizedPostId,
      Version.first(),
      now,
      now,
      data.pageId,
      data.pageName,
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
      throw SynchronizedPostModifiedConcurrently(
        "Synchronized post with ID ${data.id.value} was modified concurrently by another client"
      )
    }

    posts[synchronizedPost.id] = synchronizedPost.copy(repost = data.repost)
  }

  override fun findExisting(id: SynchronizedPostId): SynchronizedPost =
    posts[id] ?: throw SynchronizedPostNotFound("Could not find synchronized post with ID ${id.value}")

  override fun findByExternalId(externalId: ExternalId): SynchronizedPost? =
    posts.values.find { it.post.externalId == externalId }

  override fun findLastSeen(pageId: PageId): SynchronizedPost? =
    posts.values.filter { it.pageId == pageId }.maxByOrNull { it.post.publishedAt }

  override fun getLastSeen(limit: Int): SynchronizedPosts =
    TODO("Not implemented")

  override fun streamRetryable(baseDelay: Duration, maxAttempts: Int, block: (SynchronizedPost) -> Unit) =
    TODO("Not implemented")
}
