package dev.pankowski.garcon.domain

import com.google.common.util.concurrent.MoreExecutors
import spock.lang.Specification
import spock.lang.Subject

import java.time.Duration
import java.time.ZonedDateTime
import java.util.concurrent.Executor

class LunchServiceTest extends Specification {

  def config = new LunchConfig(
    new URL("https://slack/webhook"),
    Duration.ofMinutes(5),
    new LunchClientConfig("Some User Agent", Duration.ofSeconds(5)),
    [new LunchPageConfig(new LunchPageId("Test"), new URL("https://some/url"))],
  )

  LunchSubcommandParser parser = Mock()
  Executor executor = MoreExecutors.directExecutor()
  LunchSynchronizer synchronizer = Mock()
  SynchronizedPostRepository repository = Mock()

  @Subject
  LunchService service = new LunchService(config, parser, executor, synchronizer, repository)

  def someCommand() {
    new SlashCommand("/command", "text", null, null, new UserId("U1234"), new ChannelId("C1234"), null, null)
  }

  def "should handle 'help' subcommand"() {
    given:
    def command = someCommand()
    parser.parse(command) >> LunchSubcommand.Help.INSTANCE

    when:
    def result = service.handle(command)

    then:
    result.responseType == ResponseType.EPHEMERAL
    result.text ==
      """\
      |Recognized subcommands are:
      |• `/lunch` or `/lunch check` - manually triggers checking for lunch post
      |• `/lunch status` - displays status of lunch feature
      |• `/lunch help` - displays this message""".stripMargin()
  }

  def "should handle unrecognized subcommand"() {
    given:
    def command = someCommand()
    parser.parse(command) >> new LunchSubcommand.Unrecognized(["a", "b", "c"])

    when:
    def result = service.handle(command)

    then:
    result.responseType == ResponseType.EPHEMERAL
    result.text ==
      """\
      |Unrecognized subcommand: `/lunch a b c`
      |
      |Recognized subcommands are:
      |• `/lunch` or `/lunch check` - manually triggers checking for lunch post
      |• `/lunch status` - displays status of lunch feature
      |• `/lunch help` - displays this message""".stripMargin()
  }

  def "should handle 'check' subcommand"() {
    given:
    def command = someCommand()
    parser.parse(command) >> LunchSubcommand.CheckForLunchPost.INSTANCE

    when:
    def result = service.handle(command)

    then:
    1 * synchronizer.synchronize(config.pages.first())

    result.responseType == ResponseType.EPHEMERAL
    result.text == "Checking..."
  }

  def "should respond with error when scheduling of checking for lunch posts fails"() {
    given:
    Executor executor = Mock()
    executor.execute(_) >> { throw new RuntimeException("No threads available") }

    def service = new LunchService(config, parser, executor, synchronizer, repository)

    def command = someCommand()
    parser.parse(command) >> LunchSubcommand.CheckForLunchPost.INSTANCE

    when:
    def result = service.handle(command)

    then:
    result.responseType == ResponseType.EPHEMERAL
    result.text == "Error while scheduling synchronization :frowning:"
  }

  def "should handle 'log' subcommand - no synchronized posts"() {
    given:
    def command = someCommand()
    parser.parse(command) >> LunchSubcommand.Log.INSTANCE

    repository.getLog(_) >> []

    when:
    def result = service.handle(command)

    then:
    result.responseType == ResponseType.EPHEMERAL
    result.text == "No posts seen so far"
  }

  def "should handle 'log' subcommand - some synchronized posts"() {
    given:
    def command = someCommand()
    parser.parse(command) >> LunchSubcommand.Log.INSTANCE

    def baseDateTime = ZonedDateTime.parse("2000-01-01T00:00:00Z")

    repository.getLog(_) >> [
      new SynchronizedPost(
        new SynchronizedPostId("P1"),
        new Version(1),
        baseDateTime.plusDays(0).toInstant(),
        baseDateTime.plusDays(1).toInstant(),
        new LunchPageId("LP1"),
        new Post(
          new ExternalId("FB1"),
          new URI("https://facebook/1"),
          baseDateTime.plusDays(2).toInstant(),
          "Some post content 1"
        ),
        Classification.LunchPost.INSTANCE,
        Repost.Skip.INSTANCE
      ),
      new SynchronizedPost(
        new SynchronizedPostId("P2"),
        new Version(2),
        baseDateTime.plusDays(5).toInstant(),
        baseDateTime.plusDays(6).toInstant(),
        new LunchPageId("LP2"),
        new Post(
          new ExternalId("FB2"),
          new URI("https://facebook/2"),
          baseDateTime.plusDays(7).toInstant(),
          "Some post content 2"
        ),
        Classification.MissingKeywords.INSTANCE,
        Repost.Pending.INSTANCE
      ),
      new SynchronizedPost(
        new SynchronizedPostId("P3"),
        new Version(3),
        baseDateTime.plusDays(10).toInstant(),
        baseDateTime.plusDays(11).toInstant(),
        new LunchPageId("LP3"),
        new Post(
          new ExternalId("FB3"),
          new URI("https://facebook/3"),
          baseDateTime.plusDays(12).toInstant(),
          "Some post content 3"
        ),
        Classification.LunchPost.INSTANCE,
        new Repost.Error(13, baseDateTime.plusDays(13).toInstant())
      ),
      new SynchronizedPost(
        new SynchronizedPostId("P4"),
        new Version(4),
        baseDateTime.plusDays(15).toInstant(),
        baseDateTime.plusDays(16).toInstant(),
        new LunchPageId("LP4"),
        new Post(
          new ExternalId("FB4"),
          new URI("https://facebook/4"),
          baseDateTime.plusDays(17).toInstant(),
          "Some post content 4"
        ),
        Classification.MissingKeywords.INSTANCE,
        new Repost.Success(baseDateTime.plusDays(18).toInstant())
      ),
    ]

    when:
    def result = service.handle(command)

    then:
    result.responseType == ResponseType.EPHEMERAL
    result.text ==
      """\
      |Last post seen *<!date^946857600^{date_num} {time}|2000-01-03T00:00:00Z>* (timestamp `946857600`)
      |
      |Last synchronized posts:
      |
      |• *<!date^946857600^{date_num} {time}^https://facebook/1|2000-01-03T00:00:00Z>*
      |Preview: Some post content 1
      |Lunch post: :heavy_check_mark:
      |Repost: :heavy_minus_sign:
      |
      |• *<!date^947289600^{date_num} {time}^https://facebook/2|2000-01-08T00:00:00Z>*
      |Preview: Some post content 2
      |Lunch post: :heavy_minus_sign:
      |Repost: :heavy_plus_sign:
      |
      |• *<!date^947721600^{date_num} {time}^https://facebook/3|2000-01-13T00:00:00Z>*
      |Preview: Some post content 3
      |Lunch post: :heavy_check_mark:
      |Repost: 13:heavy_multiplication_x: last attempt at <!date^947808000^{date_num} {time}|2000-01-14T00:00:00Z>
      |
      |• *<!date^948153600^{date_num} {time}^https://facebook/4|2000-01-18T00:00:00Z>*
      |Preview: Some post content 4
      |Lunch post: :heavy_minus_sign:
      |Repost: :heavy_check_mark: <!date^948240000^{date_num} {time}|2000-01-19T00:00:00Z>
      |""".stripMargin()
  }
}
