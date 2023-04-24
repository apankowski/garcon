package dev.pankowski.garcon.domain

class SynchronizedPostNotFound(message: String) : RuntimeException(message)
class SynchronizedPostModifiedConcurrently(message: String) : RuntimeException(message)
class SynchronizedPostHasDuplicateExternalId(message: String) : RuntimeException(message)

data class SynchronizedPostStoreData(
  val pageId: PageId,
  val pageName: PageName,
  val post: Post,
  val classification: Classification,
  val repost: Repost,
)

interface SynchronizedPostRepository {

  fun store(data: SynchronizedPostStoreData): SynchronizedPostId

  fun updateExisting(id: SynchronizedPostId, version: Version, repost: Repost)

  fun updateExisting(id: SynchronizedPostId, version: Version, post: Post, classification: Classification)

  fun findExisting(id: SynchronizedPostId): SynchronizedPost

  fun findBy(externalId: ExternalId): SynchronizedPost?

  fun getLastSeen(limit: Int): SynchronizedPosts

  fun streamRetryable(block: (SynchronizedPost) -> Unit)
}
