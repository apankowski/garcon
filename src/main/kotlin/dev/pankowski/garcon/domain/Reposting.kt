package dev.pankowski.garcon.domain

import com.google.common.annotations.VisibleForTesting
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
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
        else lastAttemptAt + exponentialBackoff(baseDelay, attempt)
      return Failed(attempt, lastAttemptAt, nextAttemptAt)
    }

    @VisibleForTesting
    fun exponentialBackoff(baseDelay: Duration, attempt: Int): Duration =
      // baseDelay * 2 ^ (attempt - 1)
      baseDelay.multipliedBy(1L shl (attempt - 1))
  }
}

@Component
class Reposter(
  private val retryConfig: RepostRetryConfig,
  private val repository: SynchronizedPostRepository,
  private val slack: Slack,
) {

  private val log = LoggerFactory.getLogger(javaClass)

  @Transactional
  fun repost(post: SynchronizedPost) =
    Mdc.extendedWith(post.id) {
      when (post.repost) {
        is Repost.Skip,
        is Repost.Success ->
          log.warn("Ignoring request to repost $post because of its repost decision")

        is Repost.Pending,
        is Repost.Failed ->
          doRepost(post)
      }
    }

  private fun doRepost(post: SynchronizedPost) =
    try {
      slack.repost(post.post, post.pageName)
      post.update(Repost.Success(Instant.now()))
      log.info("Post ${post.post.url} reposted on Slack")
    } catch (e: Exception) {
      log.error("Failed to repost post ${post.post.url} on Slack", e)
      when (post.repost) {
        is Repost.Pending -> post.update(failedRepost(1))
        is Repost.Failed -> post.update(failedRepost(post.repost.attempts + 1))
        else -> throw IllegalStateException("Unhandled repost kind ${post.repost}")
      }
    }

  private fun SynchronizedPost.update(repost: Repost) =
    repository.updateExisting(id, version, repost)

  private fun failedRepost(attempt: Int) =
    Repost.failedWithExponentialBackoff(attempt, retryConfig.maxAttempts, retryConfig.baseDelay)
}

