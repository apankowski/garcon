package dev.pankowski.garcon.integration

import dev.pankowski.garcon.domain.LunchService
import io.kotest.assertions.timing.eventually
import io.kotest.matchers.ints.beGreaterThan
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.test.context.TestPropertySource
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Duration.Companion.seconds

@TestPropertySource(
  properties = [
    "lunch.sync.interval = PT0.5S",
    "lunch.repost.retry.interval = PT0.5S",
  ]
)
class ScheduledTaskIT : CommonIT() {

  // Maybe use https://github.com/Ninja-Squad/springmockk ?
  @TestConfiguration
  class Mocks {

    @Bean
    fun lunchService() = mockk<LunchService>()
  }

  @Autowired
  private lateinit var lunchService: LunchService

  init {
    "post synchronization scheduled task runs regularly" {
      // given
      val counter = AtomicInteger(0)

      every { lunchService.synchronizeAll() } answers { counter.incrementAndGet() }

      // expect
      eventually(10.seconds) {
        counter shouldBe beGreaterThan(2)
      }
    }

    "failed repost retrying scheduled task runs regularly" {
      // given
      val counter = AtomicInteger(0)

      every { lunchService.retryFailedReposts() } answers { counter.incrementAndGet() }

      // expect
      eventually(10.seconds) {
        counter shouldBe beGreaterThan(2)
      }
    }
  }
}
