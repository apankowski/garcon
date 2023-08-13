package dev.pankowski.garcon.domain

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
    log.info("Synchronizing posts of all pages")
    lunchConfig.pages.forEach { pageConfig ->
      try {
        pageSynchronizer.synchronize(pageConfig)
      } catch (e: Exception) {
        log.error("Error while synchronizing posts of page $pageConfig", e)
      }
    }
  }

  fun getLog() =
    repository.getLastSeen(20)

  fun retryFailedReposts() =
    repository.streamRetryable { reposter.repost(it) }
}
