package dev.pankowski.garcon.integration

import io.kotest.core.extensions.Extension
import io.kotest.core.spec.Spec
import io.kotest.core.spec.style.FreeSpec
import io.kotest.core.test.TestCase
import io.kotest.extensions.spring.SpringExtension
import io.restassured.RestAssured
import org.flywaydb.core.Flyway
import org.junit.experimental.categories.Category
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.web.server.LocalServerPort
import org.springframework.test.context.ActiveProfiles

interface IntegrationTest

@Category(IntegrationTest::class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("no-scheduled-tasks")
class CommonIT(body: FreeSpec.() -> Unit = {}) : FreeSpec(body) {

  @LocalServerPort
  protected var serverPort: Int = 0

  @Autowired
  private lateinit var flyway: Flyway

  override fun extensions(): List<Extension> {
    return listOf(SpringExtension)
  }

  override fun beforeSpec(spec: Spec) {
    RestAssured.enableLoggingOfRequestAndResponseIfValidationFails()
  }

  override fun beforeEach(testCase: TestCase) {
    flyway.clean()
    flyway.migrate()
  }

  fun request() =
    RestAssured
      .given()
      .port(serverPort)
      .log().all()!!
}
