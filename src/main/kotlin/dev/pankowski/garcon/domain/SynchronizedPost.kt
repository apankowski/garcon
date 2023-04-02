package dev.pankowski.garcon.domain

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

  data class Failed(val attempts: Int, val lastAttemptAt: Instant) : Repost(status = RepostStatus.FAILED)

  data class Success(val repostedAt: Instant) : Repost(status = RepostStatus.SUCCESS)
}

data class SynchronizedPostId(val value: String)

data class SynchronizedPost(
  val id: SynchronizedPostId,
  val version: Version,
  val createdAt: Instant,
  val updatedAt: Instant,
  val pageId: PageId,
  val pageName: PageName?,
  val post: Post,
  val classification: Classification,
  val repost: Repost,
)

typealias SynchronizedPosts = List<SynchronizedPost>
