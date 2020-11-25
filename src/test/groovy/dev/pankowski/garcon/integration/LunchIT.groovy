package dev.pankowski.garcon.integration


import org.springframework.http.HttpStatus
import org.springframework.http.MediaType

import static org.hamcrest.Matchers.equalTo
import static org.hamcrest.Matchers.notNullValue

class LunchIT extends CommonIT {

  def "should respond to /lunch log"() {
    given:
    def specification = request()
      .contentType(MediaType.APPLICATION_FORM_URLENCODED_VALUE)
      .accept(MediaType.APPLICATION_JSON_VALUE)
      .formParam("command", "/lunch")
      .formParam("text", "log")
      .formParam("user_id", "U1234")
      .formParam("channel_id", "C1234")

    when:
    def response = specification
      .post("/commands/lunch")

    then:
    response
      .then()
      .log().all()
      .assertThat()
      .statusCode(HttpStatus.OK.value())
      .contentType(MediaType.APPLICATION_JSON_VALUE)
      .and()
      .body('text', equalTo("No posts seen so far"))
      .body('response_type', equalTo('ephemeral'))
  }

  def "should return error after receiving wrong command"() {
    given:
    def specification = request()
      .contentType(MediaType.APPLICATION_FORM_URLENCODED_VALUE)
      .accept(MediaType.APPLICATION_JSON_VALUE)
      .formParam('command', '/wrong')
      .formParam('text', 'log')
      .formParam('user_id', 'U1234')
      .formParam('channel_id', 'C1234')

    when:
    def response = specification
      .post('/commands/lunch')

    then:
    response
      .then()
      .log().all()
      .assertThat()
      .statusCode(HttpStatus.BAD_REQUEST.value())
      .contentType(MediaType.APPLICATION_JSON_VALUE)
      .and()
      .body('timestamp', notNullValue())
      .body('status', equalTo(400))
  }
}
