package dev.pankowski.garcon.api

import com.google.common.util.concurrent.MoreExecutors
import dev.pankowski.garcon.domain.*
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.spyk
import io.mockk.verify
import java.net.URL
import java.time.Instant
import java.time.temporal.ChronoUnit.DAYS
import java.util.concurrent.Executor

class LunchControllerTest : FreeSpec({

  "handles 'help' subcommand" {
    // given
    val command = someLunchCommand()

    val parser = mockk<LunchSubcommandParser>()
    val executor = mockk<Executor>()
    val service = mockk<LunchService>()
    val controller = LunchController(parser, executor, service)

    every { parser.parse(command) } returns LunchSubcommand.Help

    // when
    val result = controller.handle(command)

    // then
    result.responseType shouldBe ResponseType.EPHEMERAL
    result.text shouldBe
      """
      |Recognized subcommands are:
      |• `/lunch` or `/lunch check` - manually triggers checking for lunch post
      |• `/lunch status` - displays status of lunch feature
      |• `/lunch help` - displays this message
      """.trimMargin()
  }

  "handles unrecognized subcommand" {
    // given
    val command = someLunchCommand(text = "some command text")

    val parser = mockk<LunchSubcommandParser>()
    val executor = mockk<Executor>()
    val service = mockk<LunchService>()
    val controller = LunchController(parser, executor, service)

    every { parser.parse(command) } returns LunchSubcommand.Unrecognized

    // when
    val result = controller.handle(command)

    // then
    result.responseType shouldBe ResponseType.EPHEMERAL
    result.text shouldBe
      """
      |Unrecognized subcommand: `/lunch some command text`
      |
      |Recognized subcommands are:
      |• `/lunch` or `/lunch check` - manually triggers checking for lunch post
      |• `/lunch status` - displays status of lunch feature
      |• `/lunch help` - displays this message
      """.trimMargin()
  }

  "handles 'check' subcommand" {
    // given
    val command = someLunchCommand()

    val parser = mockk<LunchSubcommandParser>()
    val executor = spyk(MoreExecutors.directExecutor())
    val service = mockk<LunchService>()
    val controller = LunchController(parser, executor, service)

    every { parser.parse(command) } returns LunchSubcommand.CheckForLunchPost
    every { service.synchronizeAll() } returns Unit

    // when
    val result = controller.handle(command)

    // then
    verify {
      executor.execute(any())
      service.synchronizeAll()
    }

    result.responseType shouldBe ResponseType.EPHEMERAL
    result.text shouldBe "Checking..."
  }

  "responds with error when scheduling of checking for lunch posts fails" {
    // given
    val command = someLunchCommand()

    val parser = mockk<LunchSubcommandParser>()
    val executor = mockk<Executor>()
    val service = mockk<LunchService>()
    val controller = LunchController(parser, executor, service)

    every { executor.execute(any()) } throws RuntimeException("No threads available")
    every { parser.parse(command) } returns LunchSubcommand.CheckForLunchPost

    // when
    val result = controller.handle(command)

    // then
    result.responseType shouldBe ResponseType.EPHEMERAL
    result.text shouldBe "Error while scheduling synchronization :frowning:"
  }

  "handles 'log' subcommand - no synchronized posts" {
    // given
    val command = someLunchCommand()

    val parser = mockk<LunchSubcommandParser>()
    val executor = mockk<Executor>()
    val service = mockk<LunchService>()
    val controller = LunchController(parser, executor, service)

    every { parser.parse(command) } returns LunchSubcommand.Log
    every { service.getLog() } returns emptyList()

    // when
    val result = controller.handle(command)

    // then
    result.responseType shouldBe ResponseType.EPHEMERAL
    result.text shouldBe "No posts seen so far"
  }

  "handles 'log' subcommand - some synchronized posts" {
    // given
    val command = someLunchCommand()

    val parser = mockk<LunchSubcommandParser>()
    val executor = mockk<Executor>()
    val service = mockk<LunchService>()
    val controller = LunchController(parser, executor, service)

    every { parser.parse(command) } returns LunchSubcommand.Log

    val somePointInTime = Instant.parse("2000-01-01T00:00:00Z")

    every { service.getLog() } returns listOf(
      someSynchronizedPost(
        pageName = PageName("Page name 1"),
        post = somePost(
          url = URL("https://facebook/1"),
          publishedAt = somePointInTime.plus(2, DAYS),
          content = "Some post content 1"
        ),
        classification = Classification.LUNCH_POST,
        repost = Repost.Skip
      ),
      someSynchronizedPost(
        pageName = PageName("Page name 2"),
        post = somePost(
          url = URL("https://facebook/2"),
          publishedAt = somePointInTime.plus(7, DAYS),
          content = "Some post content 2"
        ),
        classification = Classification.REGULAR_POST,
        repost = Repost.Pending
      ),
      someSynchronizedPost(
        pageName = PageName("Page name 3"),
        post = somePost(
          url = URL("https://facebook/3"),
          publishedAt = somePointInTime.plus(12, DAYS),
          content = "Some post content 3"
        ),
        classification = Classification.LUNCH_POST,
        repost = Repost.Failed(
          attempts = 13,
          lastAttemptAt = somePointInTime.plus(13, DAYS),
          nextAttemptAt = somePointInTime.plus(14, DAYS),
        )
      ),
      someSynchronizedPost(
        pageName = PageName("Page name 4"),
        post = somePost(
          url = URL("https://facebook/4"),
          publishedAt = somePointInTime.plus(17, DAYS),
          content = "Some post content 4"
        ),
        classification = Classification.REGULAR_POST,
        repost = Repost.Success(somePointInTime.plus(18, DAYS))
      ),
    )

    // when
    val result = controller.handle(command)

    // then
    result.responseType shouldBe ResponseType.EPHEMERAL
    result.text shouldBe
      """
      |Last post seen *<!date^946857600^{date_num} {time}|2000-01-03T00:00:00Z>* (timestamp `946857600`)
      |
      |Last synchronized posts:
      |
      |• *<!date^946857600^{date_num} {time}^https://facebook/1|2000-01-03T00:00:00Z>* from *Page name 1*
      |Preview: Some post content 1
      |Lunch post: :heavy_check_mark:
      |Repost: :heavy_minus_sign:
      |
      |• *<!date^947289600^{date_num} {time}^https://facebook/2|2000-01-08T00:00:00Z>* from *Page name 2*
      |Preview: Some post content 2
      |Lunch post: :heavy_minus_sign:
      |Repost: :heavy_plus_sign:
      |
      |• *<!date^947721600^{date_num} {time}^https://facebook/3|2000-01-13T00:00:00Z>* from *Page name 3*
      |Preview: Some post content 3
      |Lunch post: :heavy_check_mark:
      |Repost: 13:heavy_multiplication_x: last attempt at <!date^947808000^{date_num} {time}|2000-01-14T00:00:00Z>
      |
      |• *<!date^948153600^{date_num} {time}^https://facebook/4|2000-01-18T00:00:00Z>* from *Page name 4*
      |Preview: Some post content 4
      |Lunch post: :heavy_minus_sign:
      |Repost: :heavy_check_mark: <!date^948240000^{date_num} {time}|2000-01-19T00:00:00Z>
      |
      """.trimMargin()
  }
})
