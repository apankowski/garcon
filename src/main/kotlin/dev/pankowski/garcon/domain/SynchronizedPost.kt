package dev.pankowski.garcon.domain

import java.time.Duration
import java.time.Instant

enum class RepostStatus {
  SKIP,
  PENDING,
  SUCCESS,
  FAILED,
}

sealed class Repost(val status: RepostStatus) {
  object Skip : Repost(status = RepostStatus.SKIP) {
    override fun toString() = "Skip"
  }

  object Pending : Repost(status = RepostStatus.PENDING) {
    override fun toString() = "Pending"
  }

  data class Success(val repostedAt: Instant) : Repost(status = RepostStatus.SUCCESS)

  data class Failed(
    val attempts: Int,
    val lastAttemptAt: Instant,
    val nextAttemptAt: Instant?,
  ) : Repost(status = RepostStatus.FAILED)

  companion object {

    fun failedWithExponentialBackoff(attempt: Int, maxAttempts: Int, baseDelay: Duration): Failed {
      require(attempt >= 1) { "Attempt number must be positive integer" }
      val lastAttemptAt = Instant.now()
      val nextAttemptAt =
        if (attempt >= maxAttempts) null
        else lastAttemptAt + baseDelay.multipliedBy(1L shl (attempt - 1)) // baseDelay * 2 ^ (attempt - 1)
      return Failed(attempt, lastAttemptAt, nextAttemptAt)
    }
  }
}

data class SynchronizedPostId(val value: String)

data class SynchronizedPost(
  val id: SynchronizedPostId,
  val version: Version,
  val createdAt: Instant,
  val updatedAt: Instant,
  val pageId: PageId,
  val pageName: PageName,
  val post: Post,
  val classification: Classification,
  val repost: Repost,
)

typealias SynchronizedPosts = List<SynchronizedPost>
