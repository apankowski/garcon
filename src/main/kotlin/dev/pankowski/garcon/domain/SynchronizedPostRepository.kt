package dev.pankowski.garcon.domain

class SynchronizedPostNotFound(message: String) : RuntimeException(message)
class SynchronizedPostModifiedConcurrently(message: String) : RuntimeException(message)

data class StoreData(
  val pageId: LunchPageId,
  val post: Post,
  val classification: Classification,
  val repost: Repost
)

data class UpdateData(
  val id: SynchronizedPostId,
  val version: Version,
  val repost: Repost
)

interface SynchronizedPostRepository {

  fun store(data: StoreData): SynchronizedPostId

  fun updateExisting(data: UpdateData)

  fun findExisting(id: SynchronizedPostId): SynchronizedPost

  fun findLastSeen(pageId: LunchPageId): SynchronizedPost?

  fun getLog(count: Int): SynchronizationLog
}
