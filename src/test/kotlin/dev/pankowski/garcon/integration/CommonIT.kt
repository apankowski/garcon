package dev.pankowski.garcon.integration

import com.google.common.hash.Hashing
import dev.pankowski.garcon.domain.SlackConfig
import io.kotest.core.extensions.Extension
import io.kotest.core.spec.Spec
import io.kotest.core.spec.style.FreeSpec
import io.kotest.core.test.TestCase
import io.kotest.extensions.spring.SpringExtension
import io.restassured.RestAssured
import io.restassured.RestAssured.config
import io.restassured.config.HttpClientConfig.httpClientConfig
import org.apache.http.HttpEntity
import org.apache.http.HttpEntityEnclosingRequest
import org.apache.http.HttpRequest
import org.apache.http.HttpRequestInterceptor
import org.apache.http.client.methods.HttpRequestWrapper
import org.apache.http.impl.client.DefaultHttpClient
import org.apache.http.protocol.HttpContext
import org.flywaydb.core.Flyway
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalManagementPort
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import java.time.Instant
import java.util.*

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("no-scheduled-tasks")
class CommonIT(body: CommonIT.() -> Unit = {}) : FreeSpec() {

  @LocalServerPort
  protected var serverPort: Int = 0

  @LocalManagementPort
  protected var managementPort: Int = 0

  @Autowired
  private lateinit var flyway: Flyway

  @Autowired
  private lateinit var slackConfig: SlackConfig

  init {
    body()
  }

  override fun extensions(): List<Extension> {
    return listOf(SpringExtension)
  }

  override suspend fun beforeSpec(spec: Spec) {
    RestAssured.enableLoggingOfRequestAndResponseIfValidationFails()
  }

  override suspend fun beforeEach(testCase: TestCase) {
    flyway.clean()
    flyway.migrate()
  }

  fun request() =
    RestAssured
      .given()
      .port(serverPort)
      .log().all()!!

  fun managementRequest() =
    request()
      .port(managementPort)!!

  fun slackRequest() =
    request()
      .config(
        config().httpClient(
          httpClientConfig().httpClientFactory {
            // We can't use HttpClientBuilder as clients built with it are not supported by RestAssured yet
            DefaultHttpClient().apply {
              addRequestInterceptor(SlackRequestSigningInterceptor(slackConfig.signingSecret!!))
            }
          }
        )
      )
      .contentType(MediaType.APPLICATION_FORM_URLENCODED_VALUE)!!
}

class SlackRequestSigningInterceptor(signingSecret: String) : HttpRequestInterceptor {

  private val supportedHttpMethods = EnumSet.of(HttpMethod.POST, HttpMethod.PUT, HttpMethod.PATCH)
  private val hashFunction = Hashing.hmacSha256(signingSecret.toByteArray())

  override fun process(request: HttpRequest, context: HttpContext) {
    if (request.requestLine.method in supportedHttpMethods.map { it.name }) {
      val maybeEntity = findEntity(request)
      val body = maybeEntity?.content?.readBytes() ?: ByteArray(0)

      val version = "v0"
      val timestamp = Instant.now().epochSecond.toString()
      val signature = hashFunction.newHasher()
        .putString("$version:$timestamp:", Charsets.UTF_8)
        .putBytes(body)
        .hash()
        .toString()

      request.addHeader("X-Slack-Request-Timestamp", timestamp)
      request.addHeader("X-Slack-Signature", "v0=$signature")
    }
  }

  private tailrec fun findEntity(request: HttpRequest): HttpEntity? =
    when (request) {
      is HttpEntityEnclosingRequest -> request.entity
      is HttpRequestWrapper -> findEntity(request.original)
      else -> null
    }
}
