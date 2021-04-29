package dev.pankowski.garcon.configuration

import dev.pankowski.garcon.domain.LunchService
import dev.pankowski.garcon.domain.someLunchConfig
import io.kotest.core.spec.style.FreeSpec
import io.mockk.*
import org.springframework.scheduling.TaskScheduler
import java.time.Duration

class ScheduledTaskInitializerTest : FreeSpec({

  "should schedule synchronization of posts when sync interval is set" {
    // given
    val syncInterval = Duration.ofSeconds(17)
    val config = someLunchConfig(syncInterval = syncInterval)

    val taskScheduler = mockk<TaskScheduler>()
    val service = mockk<LunchService>()
    val initializer = ScheduledTaskInitializer(taskScheduler, service, config)

    every { taskScheduler.scheduleWithFixedDelay(any(), any<Duration>()) } returns mockk()
    every { service.synchronizeAll() } returns Unit

    // when
    initializer.run()

    val scheduledRunnableSlot = slot<Runnable>()
    verify {
      taskScheduler.scheduleWithFixedDelay(capture(scheduledRunnableSlot), syncInterval)
      service wasNot Called
    }

    // Comparison of lambdas doesn't work: https://stackoverflow.com/a/24098805/1820695 so we can't compare
    // it with service::synchronizeAll. Instead we stick to invoking it and observing the effects.
    scheduledRunnableSlot.captured.run()
    verify {
      service.synchronizeAll()
    }
  }

  "should *not* schedule synchronization of posts when sync interval is *not* set" {
    // given
    val config = someLunchConfig(syncInterval = null)

    val taskScheduler = mockk<TaskScheduler>()
    val service = mockk<LunchService>()
    val initializer = ScheduledTaskInitializer(taskScheduler, service, config)

    // when
    initializer.run()

    // then
    verify {
      taskScheduler wasNot Called
      service wasNot Called
    }
  }
})
