package dev.pankowski.garcon.domain

import com.google.common.annotations.VisibleForTesting
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
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
  private val repostRetryConfig: RepostRetryConfig,
  private val repository: SynchronizedPostRepository,
  private val slack: Slack,
) {

  private val log = LoggerFactory.getLogger(javaClass)

  fun repost(p: SynchronizedPost) =
    Mdc.SynchronizedPostId.having(p.id) {
      when (p.repost) {
        is Repost.Skip,
        is Repost.Success ->
          log.warn("Ignoring request to repost $p because of its repost decision")

        is Repost.Pending,
        is Repost.Failed -> {
          fun updateWith(r: Repost) =
            repository.updateExisting(p.id, p.version, r)
          try {
            doRepost(p)
            updateWith(Repost.Success(Instant.now()))
          } catch (e: Exception) {
            val newRepost = when (p.repost) {
              is Repost.Pending -> Repost.failedWithExponentialBackoff(
                1,
                repostRetryConfig.maxAttempts,
                repostRetryConfig.baseDelay
              )

              is Repost.Failed -> Repost.failedWithExponentialBackoff(
                p.repost.attempts + 1,
                repostRetryConfig.maxAttempts,
                repostRetryConfig.baseDelay
              )

              else -> throw IllegalStateException("Unhandled repost ${p.repost}")
            }
            updateWith(newRepost)
          }
        }
      }
    }

  private fun doRepost(p: SynchronizedPost) =
    try {
      slack.repost(p.post, p.pageName)
      log.info("Post ${p.post.url} reposted on Slack")
    } catch (e: Exception) {
      log.error("Failed to repost post ${p.post.url} on Slack", e)
      throw e
    }
}

