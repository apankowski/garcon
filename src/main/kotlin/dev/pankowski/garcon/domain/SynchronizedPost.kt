package dev.pankowski.garcon.domain

import java.time.Instant

enum class RepostStatus {
  SKIP,
  PENDING,
  SUCCESS,
  ERROR,
}

sealed class Repost(val status: RepostStatus) {
  object Skip : Repost(status = RepostStatus.SKIP) {
    override fun toString() = "Skip"
  }

  object Pending : Repost(status = RepostStatus.PENDING) {
    override fun toString() = "Pending"
  }

  data class Error(val errorCount: Int, val lastAttemptAt: Instant) : Repost(status = RepostStatus.ERROR)

  data class Success(val repostedAt: Instant) : Repost(status = RepostStatus.SUCCESS)
}

data class SynchronizedPostId(val value: String)

enum class ClassificationStatus {
  LUNCH_POST,
  MISSING_KEYWORDS,
}

sealed class Classification(val status: ClassificationStatus) {
  object LunchPost : Classification(status = ClassificationStatus.LUNCH_POST) {
    override fun toString() = "LunchPost"
  }

  object MissingKeywords : Classification(status = ClassificationStatus.MISSING_KEYWORDS) {
    override fun toString() = "MissingKeywords"
  }
}

data class SynchronizedPost(
  val id: SynchronizedPostId,
  val version: Version,
  val createdAt: Instant,
  val updatedAt: Instant,
  val pageId: LunchPageId,
  val post: Post,
  val classification: Classification,
  val repost: Repost,
)

typealias SynchronizationLog = List<SynchronizedPost>
