package dev.pankowski.garcon.integration

import com.fasterxml.jackson.databind.ObjectMapper
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

@SpringBootTest(
  webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
  properties = [
    "spring.main.allow-bean-definition-overriding = true",
    // Normally, Spring Boot detects the cloud environment it is in, enabling its specific features. Since we have
    // integration tests verifying kubernetes-specific features, let's override the cloud platform auto-detection.
    "spring.main.cloud-platform = kubernetes",
    // By default, Spring Boot disables metric exporters. This is because most of them are push based and thus require
    // a running metrics server. Since it is typically unavailable during the tests, it breaks application startup.
    // We're enabling the exporters since we're using pull based Prometheus and have integration tests around it.
    // See `ObservabilityContextCustomizerFactory` for the code responsible for this feature.
    "spring.test.observability.auto-configure = true",
  ],
)
@ActiveProfiles("no-scheduled-tasks")
class CommonIT(body: CommonIT.() -> Unit = {}) : FreeSpec() {

  @LocalServerPort
  private var serverPort: Int = 0

  @LocalManagementPort
  private var managementPort: Int = 0

  @Autowired
  private lateinit var flyway: Flyway

  @Autowired
  private lateinit var slackConfig: SlackConfig

  @Autowired
  private lateinit var objectMapper: ObjectMapper

  init {
    body()
  }

  override fun extensions(): List<Extension> =
    listOf(SpringExtension)

  override suspend fun beforeSpec(spec: Spec) {
    RestAssured.enableLoggingOfRequestAndResponseIfValidationFails()
    // Make RestAssured use Spring-configured ObjectMapper for (de)serialization.
    config = config().objectMapperConfig(
      config.objectMapperConfig.jackson2ObjectMapperFactory { _, _ -> objectMapper }
    )
  }

  override suspend fun beforeTest(testCase: TestCase) {
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

/**
 * Interceptor mimicking [Slack's request signing](https://api.slack.com/authentication/verifying-requests-from-slack)
 * by adding appropriate headers to the outgoing requests issued by Apache HTTP client.
 */
private class SlackRequestSigningInterceptor(signingSecret: String) : HttpRequestInterceptor {

  private val supportedHttpMethods = setOf(HttpMethod.POST, HttpMethod.PUT, HttpMethod.PATCH)
  private val hashFunction = Hashing.hmacSha256(signingSecret.toByteArray())

  override fun process(request: HttpRequest, context: HttpContext) {
    if (request.requestLine.method in supportedHttpMethods.map { it.name() }) {
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
