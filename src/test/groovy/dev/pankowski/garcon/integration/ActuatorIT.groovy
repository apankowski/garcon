package dev.pankowski.garcon.integration

import org.springframework.http.HttpStatus
import org.springframework.http.MediaType

import static org.hamcrest.Matchers.emptyString
import static org.hamcrest.Matchers.equalTo
import static org.hamcrest.Matchers.not

class ActuatorIT extends CommonIT {

  def notEmpty() {
    return not(emptyString())
  }

  def "Actuator's info endpoint should contain git info"() {
    given:
    def specification = request()
      .accept(MediaType.APPLICATION_JSON_VALUE)

    when:
    def response = specification
      .get('/internal/info')

    then:
    response
      .then()
      .log().all()
      .assertThat()
      .statusCode(HttpStatus.OK.value())
      .contentType(MediaType.APPLICATION_JSON_VALUE)
      .and()
      .body('git.branch', notEmpty())
      .body('git.commit.id', notEmpty())
      .body('git.commit.time', notEmpty())
  }

  def "Actuator's info endpoint should contain build info"() {
    given:
    def specification = request()
      .accept(MediaType.APPLICATION_JSON_VALUE)

    when:
    def response = specification
      .get('/internal/info')

    then:
    response
      .then()
      .log().all()
      .assertThat()
      .statusCode(HttpStatus.OK.value())
      .contentType(MediaType.APPLICATION_JSON_VALUE)
      .and()
      .body('build.name', notEmpty())
      .body('build.artifact', notEmpty())
      .body('build.group', notEmpty())
      .body('build.version', notEmpty())
      .body('build.time', notEmpty())
  }

  def "Actuator's health endpoint should contain health details"() {
    given:
    def specification = request()
      .accept(MediaType.APPLICATION_JSON_VALUE)

    when:
    def response = specification
      .get('/internal/health')

    then:
    response
      .then()
      .log().all()
      .assertThat()
      .statusCode(HttpStatus.OK.value())
      .contentType(MediaType.APPLICATION_JSON_VALUE)
      .and()
      .body('status', equalTo('UP'))
      .body('components.diskSpace.status', equalTo('UP'))
  }
}
