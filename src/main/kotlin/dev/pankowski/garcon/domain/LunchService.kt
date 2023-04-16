package dev.pankowski.garcon.domain

import com.google.common.annotations.VisibleForTesting
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.time.Instant

@Component
class LunchService(
  private val lunchConfig: LunchConfig,
  private val repostRetryConfig: RepostRetryConfig,
  private val repository: SynchronizedPostRepository,
  private val pageSynchronizer: PageSynchronizer,
  private val slack: Slack,
) {

  private val log = LoggerFactory.getLogger(javaClass)

  @Synchronized
  fun synchronizeAll() {
    log.info("Checking for lunch posts")
    lunchConfig.pages.forEach { pageConfig ->
      try {
        synchronize(pageConfig)
      } catch (e: Exception) {
        log.error("Error while synchronizing posts of page $pageConfig", e)
      }
    }
  }

  @VisibleForTesting
  fun synchronize(page: PageConfig) =
    Mdc.PageId.having(page.id) {
      pageSynchronizer.synchronize(page).forEach {
        if (it.lunchPostAppeared) repost(it.new)
      }
    }

  private fun repost(p: SynchronizedPost) =
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

  fun getLog() =
    repository.getLastSeen(20)

  fun retryFailedReposts() =
    repository.streamRetryable(::repost)
}
