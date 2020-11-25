package dev.pankowski.garcon.domain

import org.springframework.boot.CommandLineRunner
import org.springframework.scheduling.TaskScheduler
import org.springframework.stereotype.Component

@Component
class ScheduledTaskInitializer(
  private val taskScheduler: TaskScheduler,
  private val service: LunchService,
  private val config: LunchConfig
) : CommandLineRunner {

  override fun run(vararg args: String?) {
    if (config.syncInterval != null)
      taskScheduler.scheduleWithFixedDelay(service::checkForLunchPosts, config.syncInterval)
  }
}
