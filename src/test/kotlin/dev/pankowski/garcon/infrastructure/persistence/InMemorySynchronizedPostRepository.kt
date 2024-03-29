package dev.pankowski.garcon.infrastructure.persistence

import dev.pankowski.garcon.domain.*
import java.util.*

open class InMemorySynchronizedPostRepository : SynchronizedPostRepository {

  private val posts = mutableMapOf<SynchronizedPostId, SynchronizedPost>()

  fun put(synchronizedPost: SynchronizedPost) {
    posts[synchronizedPost.id] = synchronizedPost
  }

  override fun store(data: SynchronizedPostStoreData): SynchronizedPostId {
    if (findBy(data.post.externalId) != null)
      throw SynchronizedPostHasDuplicateExternalId(
        "Failed to store synchronized post due to duplicate external ID ${data.post.externalId}"
      )

    val synchronizedPostId = SynchronizedPostId(UUID.randomUUID().toString())
    val now = now()
    val synchronizedPost = SynchronizedPost(
      synchronizedPostId,
      Version.first(),
      now,
      now,
      data.pageKey,
      data.pageName,
      data.post,
      data.classification,
      data.repost
    )

    posts[synchronizedPost.id] = synchronizedPost

    return synchronizedPost.id
  }

  override fun updateExisting(id: SynchronizedPostId, version: Version, repost: Repost) =
    doUpdateExisting(id, version) { it.copy(repost = repost) }

  override fun updateExisting(id: SynchronizedPostId, version: Version, post: Post, classification: Classification) =
    doUpdateExisting(id, version) { it.copy(post = post, classification = classification) }

  private fun doUpdateExisting(
    id: SynchronizedPostId,
    version: Version,
    updater: (SynchronizedPost) -> (SynchronizedPost),
  ) {

    val synchronizedPost = findExisting(id)
    if (synchronizedPost.version != version)
      throw SynchronizedPostModifiedConcurrently(
        "Synchronized post with ID ${id.value} was modified concurrently by another client"
      )

    posts[synchronizedPost.id] = updater(synchronizedPost)
  }

  override fun findExisting(id: SynchronizedPostId): SynchronizedPost =
    posts[id] ?: throw SynchronizedPostNotFound("Could not find synchronized post with ID ${id.value}")

  override fun findBy(externalId: ExternalPostId): SynchronizedPost? =
    posts.values.find { it.post.externalId == externalId }

  override fun getLastSeen(limit: Int): SynchronizedPosts =
    TODO("Not implemented")

  override fun streamRetryable(block: (SynchronizedPost) -> Unit) {
    val now = now()
    posts.values
      .filter {
        val repost = it.repost
        repost is Repost.Failed && (repost.nextAttemptAt?.isBefore(now) ?: false)
      }
      .sortedBy { it.post.publishedAt }
      .onEach(block)
  }
}
