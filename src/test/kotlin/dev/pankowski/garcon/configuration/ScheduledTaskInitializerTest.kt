package dev.pankowski.garcon.configuration

import dev.pankowski.garcon.domain.LunchService
import dev.pankowski.garcon.domain.someRetryConfig
import dev.pankowski.garcon.domain.someSyncConfig
import io.kotest.core.spec.style.FreeSpec
import io.mockk.*
import org.springframework.scheduling.TaskScheduler
import java.time.Duration

class ScheduledTaskInitializerTest : FreeSpec({

  "schedules synchronization of posts when sync interval is set" {
    // given
    val syncInterval = Duration.ofSeconds(17)
    val syncConfig = someSyncConfig(interval = syncInterval)

    val taskScheduler = mockk<TaskScheduler>()
    val service = mockk<LunchService>()
    val initializer = ScheduledTaskInitializer(taskScheduler, service, syncConfig, someRetryConfig(interval = null))

    every { service.synchronizeAll() } returns Unit
    every { taskScheduler.scheduleWithFixedDelay(any(), syncConfig.interval!!) } answers {
      firstArg<Runnable>().run()
      mockk()
    }

    // when
    initializer.run()

    // then
    verify(exactly = 1) {
      taskScheduler.scheduleWithFixedDelay(any(), any<Duration>())
      service.synchronizeAll()
    }
  }

  "doesn't schedule synchronization of posts when sync interval is not set" {
    // given
    val syncConfig = someSyncConfig(interval = null)

    val taskScheduler = mockk<TaskScheduler>()
    val service = mockk<LunchService>()
    val initializer = ScheduledTaskInitializer(taskScheduler, service, syncConfig, someRetryConfig(interval = null))

    // when
    initializer.run()

    // then
    verify {
      taskScheduler wasNot Called
      service wasNot Called
    }
  }

  "schedules retrying of failed reposts when retry interval is set" {
    // given
    val retryConfig = someRetryConfig(interval = Duration.ofSeconds(42))

    val taskScheduler = mockk<TaskScheduler>()
    val service = mockk<LunchService>()
    val initializer = ScheduledTaskInitializer(taskScheduler, service, someSyncConfig(interval = null), retryConfig)

    every { service.retryFailedReposts() } returns Unit
    every { taskScheduler.scheduleWithFixedDelay(any(), retryConfig.interval!!) } answers {
      firstArg<Runnable>().run()
      mockk()
    }

    // when
    initializer.run()

    // then
    verify(exactly = 1) {
      taskScheduler.scheduleWithFixedDelay(any(), any<Duration>())
      service.retryFailedReposts()
    }
  }

  "doesn't schedule retrying of failed reposts when retry interval is not set" {
    // given
    val retryConfig = someRetryConfig(interval = null)

    val taskScheduler = mockk<TaskScheduler>()
    val service = mockk<LunchService>()
    val initializer = ScheduledTaskInitializer(taskScheduler, service, someSyncConfig(interval = null), retryConfig)

    // when
    initializer.run()

    // then
    verify {
      taskScheduler wasNot Called
      service wasNot Called
    }
  }
})
