package dev.pankowski.garcon.configuration

import dev.pankowski.garcon.domain.LunchService
import dev.pankowski.garcon.domain.LunchSyncConfig
import dev.pankowski.garcon.domain.RetryConfig
import org.springframework.boot.CommandLineRunner
import org.springframework.scheduling.TaskScheduler
import org.springframework.stereotype.Component

@Component
class ScheduledTaskInitializer(
  private val taskScheduler: TaskScheduler,
  private val service: LunchService,
  private val syncConfig: LunchSyncConfig,
  private val retryConfig: RetryConfig,
) : CommandLineRunner {

  override fun run(vararg args: String?) {
    if (syncConfig.interval != null)
      taskScheduler.scheduleWithFixedDelay(service::synchronizeAll, syncConfig.interval)

    if (retryConfig.interval != null)
      taskScheduler.scheduleWithFixedDelay(service::retryFailedReposts, retryConfig.interval)
  }
}
