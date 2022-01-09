package dev.pankowski.garcon.configuration

import dev.pankowski.garcon.domain.LunchService
import dev.pankowski.garcon.domain.SyncConfig
import dev.pankowski.garcon.domain.RepostRetryConfig
import org.springframework.boot.CommandLineRunner
import org.springframework.scheduling.TaskScheduler
import org.springframework.stereotype.Component

@Component
class ScheduledTaskInitializer(
  private val taskScheduler: TaskScheduler,
  private val service: LunchService,
  private val syncConfig: SyncConfig,
  private val repostRetryConfig: RepostRetryConfig,
) : CommandLineRunner {

  override fun run(vararg args: String?) {
    if (syncConfig.interval != null)
      taskScheduler.scheduleWithFixedDelay(service::synchronizeAll, syncConfig.interval)

    if (repostRetryConfig.interval != null)
      taskScheduler.scheduleWithFixedDelay(service::retryFailedReposts, repostRetryConfig.interval)
  }
}
