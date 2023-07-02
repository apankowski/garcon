package dev.pankowski.garcon.domain

import com.google.common.annotations.VisibleForTesting
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@Component
class LunchService(
  private val lunchConfig: LunchConfig,
  private val repository: SynchronizedPostRepository,
  private val pageSynchronizer: PageSynchronizer,
  private val reposter: Reposter,
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
    Mdc.extendedWith(page.key) {
      pageSynchronizer.synchronize(page).forEach {
        if (it.lunchPostAppeared) reposter.repost(it.new)
      }
    }

  fun getLog() =
    repository.getLastSeen(20)

  fun retryFailedReposts() =
    repository.streamRetryable { reposter.repost(it) }
}
