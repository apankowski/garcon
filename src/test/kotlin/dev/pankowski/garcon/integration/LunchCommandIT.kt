package dev.pankowski.garcon.integration

import dev.pankowski.garcon.api.SlackMessage
import io.restassured.response.Response
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import java.time.Instant

class LunchCommandIT : CommonIT({

  fun slashCommandRequest(command: String, text: String) =
    slackRequest()
      .accept(MediaType.APPLICATION_JSON_VALUE)
      .formParam("command", command)
      .formParam("text", text)
      .formParam("user_id", "U1234")
      .formParam("channel_id", "C1234")

  fun Response.containsSlackMessage() =
    then()
      .log().all()
      .assertThat()
      .statusCode(HttpStatus.OK.value())
      .contentType(MediaType.APPLICATION_JSON_VALUE)
      .and()
      .extract()
      .body()
      .`as`(SlackMessage::class.java)

  data class Error(
    val timestamp: Instant,
    val status: Int,
    val error: String,
    val message: String?,
    val trace: String?,
  )

  fun Response.containsError(status: HttpStatus) =
    then()
      .log().all()
      .assertThat()
      .statusCode(status.value())
      .contentType(MediaType.APPLICATION_JSON_VALUE)
      .and()
      .extract()
      .body()
      .`as`(Error::class.java)

  "lunch endpoint only handles lunch command" {
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

  "handles unrecognized lunch command text" {
    // given
    val request = slashCommandRequest("/lunch", "something")

    // when
    val response = request.post("/commands/lunch")

    // then
    response.containsSlackMessage()
  }
})
