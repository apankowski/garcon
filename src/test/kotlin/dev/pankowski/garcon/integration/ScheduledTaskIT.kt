package dev.pankowski.garcon.integration

import dev.pankowski.garcon.domain.LunchService
import io.kotest.assertions.timing.eventually
import io.kotest.matchers.ints.beGreaterThan
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.test.context.TestPropertySource
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.ExperimentalTime
import kotlin.time.seconds

@TestPropertySource(
  properties = [
    "lunch.sync-interval: PT0.2S",
    "spring.main.allow-bean-definition-overriding: true"
  ]
)
@ExperimentalTime
class ScheduledTaskIT(lunchService: LunchService) : CommonIT() {

  // Maybe use https://github.com/Ninja-Squad/springmockk ?
  @TestConfiguration
  class Mocks {
    @Bean
    fun lunchService() = mockk<LunchService>()
  }

  init {
    "post synchronization is called regularly" {
      // given
      val counter = AtomicInteger(0)

      every { lunchService.synchronizeAll() } answers { counter.incrementAndGet() }

      // expect
      eventually(10.seconds) {
        counter shouldBe beGreaterThan(2)
      }
    }
  }
}
