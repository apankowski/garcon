package dev.pankowski.garcon.integration

import dev.pankowski.garcon.api.SlackMessage
import dev.pankowski.garcon.domain.*
import dev.pankowski.garcon.infrastructure.persistence.someFailedRepost
import io.kotest.assertions.timing.eventually
import io.kotest.core.test.TestCase
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.restassured.response.Response
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import java.time.Instant
import kotlin.time.Duration.Companion.seconds

class LunchIT : CommonIT() {

  // Maybe use https://github.com/Ninja-Squad/springmockk ?
  @TestConfiguration
  class Mocks {

    @Bean
    fun slack() = mockk<Slack>()

    @Bean
    fun pageClient() = mockk<PageClient>()
  }

  @Autowired
  private lateinit var lunchService: LunchService

  @Autowired
  private lateinit var slack: Slack

  @Autowired
  private lateinit var pageClient: PageClient

  @Autowired
  private lateinit var repository: SynchronizedPostRepository

  override suspend fun beforeTest(testCase: TestCase) {
    super.beforeTest(testCase)
    clearAllMocks()
  }

  private fun slashCommandRequest(command: String, text: String) =
    slackRequest()
      .accept(MediaType.APPLICATION_JSON_VALUE)
      .formParam("command", command)
      .formParam("text", text)
      .formParam("user_id", "U1234")
      .formParam("channel_id", "C1234")

  private fun Response.containsSlackMessage() =
    then()
      .log().all()
      .assertThat()
      .statusCode(HttpStatus.OK.value())
      .contentType(MediaType.APPLICATION_JSON_VALUE)
      .and()
      .extract()
      .body()
      .`as`(SlackMessage::class.java)

  private data class Error(
    val timestamp: Instant,
    val status: Int,
    val error: String,
    val message: String?,
    val trace: String?,
  )

  private fun Response.containsError(status: HttpStatus) =
    then()
      .log().all()
      .assertThat()
      .statusCode(status.value())
      .contentType(MediaType.APPLICATION_JSON_VALUE)
      .and()
      .extract()
      .body()
      .`as`(Error::class.java)

  init {
    "command handler only handles lunch command" {
      // given
      val request = slashCommandRequest("/wrong", "log")

      // when
      val response = request.post("/commands/lunch")

      // then
      response.containsError(HttpStatus.BAD_REQUEST)
    }

    "lunch help works" {
      // given
      val request = slashCommandRequest("/lunch", "help")

      // when
      val response = request.post("/commands/lunch")

      // then
      response.containsSlackMessage()
    }

    "lunch log works" {
      // given
      val request = slashCommandRequest("/lunch", "log")

      // when
      val response = request.post("/commands/lunch")

      // then
      response.containsSlackMessage()
    }

    "lunch check works" {
      // given
      val pageName = somePageName()
      val post = somePost(content = "some lunch offer") // Note the keyword

      every { pageClient.load(any()) } returns Page(pageName, listOf(post))
      every { slack.repost(post, pageName) } returns SlackMessageId("some-slack-id")

      val request = slashCommandRequest("/lunch", "check")

      // when
      val response = request.post("/commands/lunch")

      // then
      response.containsSlackMessage()

      eventually(10.seconds) {
        verify { slack.repost(any(), any()) }
      }
    }

    "command handler handles unrecognized text" {
      // given
      val request = slashCommandRequest("/lunch", "something")

      // when
      val response = request.post("/commands/lunch")

      // then
      response.containsSlackMessage()
    }

    "post synchronization scheduled task handler works" {
      // given
      val pageName = somePageName()
      val post = somePost(content = "some lunch offer") // Note the keyword

      every { pageClient.load(any()) } returns Page(pageName, listOf(post))
      every { slack.repost(any(), any()) } returns SlackMessageId("some-slack-id")

      // when
      lunchService.synchronizeAll()

      // then
      verify { slack.repost(any(), any()) }
    }

    "failed repost retrying scheduled task handler works" {
      // given
      every { slack.repost(any(), any()) } returns SlackMessageId("some-slack-id")

      repository.store(
        SynchronizedPostStoreData(
          pageId = somePageConfig().id,
          pageName = somePageName(),
          post = somePost(),
          classification = Classification.LUNCH_POST,
          repost = someFailedRepost(nextAttemptAt = Instant.now()),
        )
      )

      // when
      lunchService.retryFailedReposts()

      // then
      verify { slack.repost(any(), any()) }
    }
  }
}
