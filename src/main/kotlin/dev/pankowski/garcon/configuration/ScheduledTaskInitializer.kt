package dev.pankowski.garcon.configuration

import dev.pankowski.garcon.domain.LunchConfig
import dev.pankowski.garcon.domain.LunchSynchronizer
import org.springframework.boot.CommandLineRunner
import org.springframework.scheduling.TaskScheduler
import org.springframework.stereotype.Component

@Component
class ScheduledTaskInitializer(
  private val taskScheduler: TaskScheduler,
  private val service: LunchSynchronizer,
  private val config: LunchConfig,
) : CommandLineRunner {

  override fun run(vararg args: String?) {
    if (config.syncInterval != null)
      taskScheduler.scheduleWithFixedDelay(service::synchronizeAll, config.syncInterval)
  }
}
