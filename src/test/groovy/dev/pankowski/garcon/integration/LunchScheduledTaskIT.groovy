package dev.pankowski.garcon.integration


import dev.pankowski.garcon.domain.LunchService
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.test.context.TestPropertySource
import spock.mock.DetachedMockFactory
import spock.util.concurrent.PollingConditions

import java.util.concurrent.atomic.AtomicInteger

@TestPropertySource(
  properties = [
    "lunch.sync-interval: PT0.2S",
    "spring.main.allow-bean-definition-overriding: true"
  ]
)
class LunchScheduledTaskIT extends CommonGroovyIT {

  @Autowired
  LunchService service

  @TestConfiguration
  static class Mocks {

    def mockFactory = new DetachedMockFactory()

    @Bean
    LunchService lunchService() {
      return mockFactory.Mock(LunchService)
    }
  }

  def "should regularly trigger checking for lunch post"() {
    given:
    def conditions = new PollingConditions(timeout: 10)
    def counter = new AtomicInteger(0)

    and:
    service.synchronizeAll() >> {
      counter.incrementAndGet()
    }

    expect:
    conditions.eventually {
      assert counter.get() >= 3
    }
  }
}
