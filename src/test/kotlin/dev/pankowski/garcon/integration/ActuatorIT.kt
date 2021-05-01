package dev.pankowski.garcon.integration

import org.hamcrest.Matchers.*
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType

class ActuatorIT : CommonIT() {

  fun notEmpty() = not(emptyString())!!

  init {
    "Actuator's info endpoint should contain git info" {
      // given
      val specification = request()
        .accept(MediaType.APPLICATION_JSON_VALUE)

      // when
      val response = specification
        .get("/internal/info")

      // then
      response
        .then()
        .log().all()
        .assertThat()
        .statusCode(HttpStatus.OK.value())
        .contentType(MediaType.APPLICATION_JSON_VALUE)
        .and()
        .body("git.branch", notEmpty())
        .body("git.commit.id", notEmpty())
        .body("git.commit.time", notEmpty())
    }

    "Actuator's info endpoint should contain build info" {
      // given
      val specification = request()
        .accept(MediaType.APPLICATION_JSON_VALUE)

      // when
      val response = specification
        .get("/internal/info")

      // then
      response
        .then()
        .log().all()
        .assertThat()
        .statusCode(HttpStatus.OK.value())
        .contentType(MediaType.APPLICATION_JSON_VALUE)
        .and()
        .body("build.name", notEmpty())
        .body("build.artifact", notEmpty())
        .body("build.group", notEmpty())
        .body("build.version", notEmpty())
        .body("build.time", notEmpty())
    }

    "Actuator's health endpoint should contain health details" {
      // given
      val specification = request()
        .accept(MediaType.APPLICATION_JSON_VALUE)

      // when
      val response = specification
        .get("/internal/health")

      // then
      response
        .then()
        .log().all()
        .assertThat()
        .statusCode(HttpStatus.OK.value())
        .contentType(MediaType.APPLICATION_JSON_VALUE)
        .and()
        .body("status", equalTo("UP"))
        .body("components.diskSpace.status", equalTo("UP"))
    }
  }
}
