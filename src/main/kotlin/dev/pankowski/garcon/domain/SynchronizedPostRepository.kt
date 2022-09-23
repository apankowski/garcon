package dev.pankowski.garcon.domain

import java.time.Duration

class SynchronizedPostNotFound(message: String) : RuntimeException(message)
class SynchronizedPostModifiedConcurrently(message: String) : RuntimeException(message)

data class StoreData(
  val pageId: PageId,
  val pageName: PageName?,
  val post: Post,
  val classification: Classification,
  val repost: Repost,
)

data class UpdateData(
  val id: SynchronizedPostId,
  val version: Version,
  val repost: Repost,
)

interface SynchronizedPostRepository {

  fun store(data: StoreData): SynchronizedPostId

  fun updateExisting(data: UpdateData)

  fun findExisting(id: SynchronizedPostId): SynchronizedPost

  fun findLastSeen(pageId: PageId): SynchronizedPost?

  fun getLastSeen(limit: Int): SynchronizedPosts

  fun streamRetryable(baseDelay: Duration, maxAttempts: Int, block: (SynchronizedPost) -> Unit)
}
