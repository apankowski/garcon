package dev.pankowski.garcon.configuration


import dev.pankowski.garcon.domain.LunchClientConfig
import dev.pankowski.garcon.domain.LunchConfig
import dev.pankowski.garcon.domain.LunchSynchronizer
import org.springframework.scheduling.TaskScheduler
import spock.lang.Specification
import spock.lang.Subject

import java.time.Duration

class ScheduledTaskInitializerTest extends Specification {

  def "should schedule synchronization of posts when sync interval is set"() {
    given:
    def syncInterval = Duration.ofSeconds(17)
    def config = new LunchConfig(
      new URL("https://slack/webhook"),
      syncInterval,
      new LunchClientConfig("Some User Agent", Duration.ofSeconds(5)),
      [],
    )

    def taskScheduler = Mock(TaskScheduler)
    def service = Mock(LunchSynchronizer)

    @Subject
    def initializer = new ScheduledTaskInitializer(taskScheduler, service, config)

    when:
    initializer.run()

    then:
    // TODO: This is ugly.
    // Since Groovy doesn't support getting method reference using double colon operator
    // (Groovy 3 does, but it's in beta), we can't just write:
    //
    // 1 * taskScheduler.scheduleWithFixedDelay(service::checkForLunchPost, syncInterval)
    //
    // Instead we capture the arguments passed, assert on the second argument, invoke the method
    // reference and find out if it passed through all the way to the underlying service.
    1 * taskScheduler.scheduleWithFixedDelay(_, _) >> { arguments ->
      def runnable = arguments[0]
      (runnable as Runnable).run()

      def delay = arguments[1]
      assert delay == syncInterval
    }

    1 * service.synchronizeAll()
  }

  def "should *not* schedule synchronization of posts when sync interval is *not* set"() {
    given:
    def config = new LunchConfig(
      new URL("https://slack/webhook"),
      null,
      new LunchClientConfig("Some User Agent", Duration.ofSeconds(5)),
      [],
    )

    def taskScheduler = Mock(TaskScheduler)
    def service = Mock(LunchSynchronizer)

    @Subject
    def initializer = new ScheduledTaskInitializer(taskScheduler, service, config)

    when:
    initializer.run()

    then:
    0 * taskScheduler._
    0 * service._
  }
}
