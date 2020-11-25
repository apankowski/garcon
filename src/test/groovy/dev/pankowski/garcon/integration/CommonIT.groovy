package dev.pankowski.garcon.integration

import com.github.tomakehurst.wiremock.common.Slf4jNotifier
import com.github.tomakehurst.wiremock.junit.WireMockRule
import io.restassured.RestAssured
import io.restassured.specification.RequestSpecification
import org.junit.Rule
import org.junit.experimental.categories.Category
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.web.server.LocalServerPort
import org.springframework.test.context.ActiveProfiles
import spock.lang.Specification

import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT

@Category(IntegrationTest)
@SpringBootTest(webEnvironment = RANDOM_PORT)
@ActiveProfiles("no-scheduled-tasks")
abstract class CommonIT extends Specification {

  @LocalServerPort
  int serverPort

  @Rule
  WireMockRule wireMockRule = new WireMockRule(
    wireMockConfig()
      .bindAddress("localhost")
      .port(9876)
      .needClientAuth(false)
      .notifier(new Slf4jNotifier(true))
  )

  def setupSpec() {
    RestAssured.enableLoggingOfRequestAndResponseIfValidationFails()
  }

  RequestSpecification request() {
    return RestAssured
      .given()
      .port(serverPort)
      .log().all()
  }
}
