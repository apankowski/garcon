package dev.pankowski.garcon.integration

import org.hamcrest.Matchers.equalTo
import org.hamcrest.Matchers.notNullValue
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType

class LunchIT : CommonIT({

  "lunch endpoint is responding" {
    // given
    val specification = slackRequest()
      .accept(MediaType.APPLICATION_JSON_VALUE)
      .formParam("command", "/lunch")
      .formParam("text", "log")
      .formParam("user_id", "U1234")
      .formParam("channel_id", "C1234")

    // when
    val response = specification
      .post("/commands/lunch")

    // then
    response
      .then()
      .log().all()
      .assertThat()
      .statusCode(HttpStatus.OK.value())
      .contentType(MediaType.APPLICATION_JSON_VALUE)
      .and()
      .body("text", equalTo("No posts seen so far"))
      .body("response_type", equalTo("ephemeral"))
  }

  "lunch endpoint handles errors" {
    // given
    val specification = slackRequest()
      .accept(MediaType.APPLICATION_JSON_VALUE)
      .formParam("command", "/wrong")
      .formParam("text", "log")
      .formParam("user_id", "U1234")
      .formParam("channel_id", "C1234")

    // when
    val response = specification
      .post("/commands/lunch")

    // then
    response
      .then()
      .log().all()
      .assertThat()
      .statusCode(HttpStatus.BAD_REQUEST.value())
      .contentType(MediaType.APPLICATION_JSON_VALUE)
      .and()
      .body("timestamp", notNullValue())
      .body("status", equalTo(400))
  }
})
