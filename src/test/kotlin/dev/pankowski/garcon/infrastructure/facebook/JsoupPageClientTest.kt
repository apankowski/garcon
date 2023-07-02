package dev.pankowski.garcon.infrastructure.facebook

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.*
import com.github.tomakehurst.wiremock.stubbing.Scenario.STARTED
import dev.pankowski.garcon.domain.PageName
import dev.pankowski.garcon.domain.someClientConfig
import dev.pankowski.garcon.domain.somePageConfig
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.assertions.throwables.shouldThrowAny
import io.kotest.assertions.throwables.shouldThrowExactly
import io.kotest.core.spec.style.FreeSpec
import io.kotest.datatest.withData
import io.kotest.extensions.wiremock.ListenerMode
import io.kotest.extensions.wiremock.WireMockListener
import io.kotest.matchers.collections.beEmpty
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.containIgnoringCase
import io.kotest.matchers.string.shouldNotBeEmpty
import org.jsoup.HttpStatusException
import org.springframework.http.MediaType
import java.net.SocketTimeoutException
import java.net.URL
import java.time.Duration

class JsoupPageClientTest : FreeSpec({

  val server = WireMockServer(4321)
  listener(WireMockListener(server, ListenerMode.PER_SPEC))

  fun okAndHtml(html: String) =
    okForContentType(MediaType.TEXT_HTML.toString(), html)

  fun htmlFrom(file: String) =
    when (val url = javaClass.getResource(file)) {
      null -> throw IllegalArgumentException("$file classpath resource doesn't exist")
      else -> url.readText()
    }

  "requests page with expected headers" {
    // given
    val clientConfig = someClientConfig(userAgent = "Some User Agent")
    val client = JsoupPageClient(clientConfig, listOf())
    val pageConfig = somePageConfig(url = URL(server.url("/posts")))

    // and
    server.givenThat(
      get("/posts").willReturn(okAndHtml("<html><body>Some body</body></html>"))
    )

    // when
    shouldThrowAny {
      client.load(pageConfig)
    }

    // then
    server.verify(
      getRequestedFor(urlEqualTo("/posts"))
        .withHeader("User-Agent", equalTo(clientConfig.userAgent))
        .withHeader("Accept", equalTo("text/html,application/xhtml+xml"))
        .withHeader("Accept-Language", equalTo("pl,en;q=0.5"))
        .withHeader("Cache-Control", equalTo("no-cache"))
        .withHeader("Pragma", equalTo("no-cache"))
        .withHeader("DNT", equalTo("1"))
        .withHeader("Sec-Fetch-Dest", equalTo("document"))
        .withHeader("Sec-Fetch-Mode", equalTo("navigate"))
        .withHeader("Sec-Fetch-Site", equalTo("none"))
        .withHeader("Sec-Fetch-User", equalTo("?1"))
    )
  }

  "honors specified timeout" {
    // given
    val clientConfig = someClientConfig(timeout = Duration.ofMillis(100))
    val client = JsoupPageClient(clientConfig, listOf())
    val pageConfig = somePageConfig(url = URL(server.url("/posts")))

    // and
    server.givenThat(
      get("/posts").willReturn(ok().withFixedDelay(200))
    )

    // expect
    shouldThrow<SocketTimeoutException> {
      client.load(pageConfig)
    }
  }

  "retries given page in case of failure" {
    // given
    val client = JsoupPageClient(someClientConfig(retries = 2), listOf())
    val pageConfig = somePageConfig(url = URL(server.url("/posts")))

    // and
    server.resetAll()

    server.givenThat(
      get("/posts")
        .inScenario("retry")
        .whenScenarioStateIs(STARTED)
        .willReturn(badRequest())
        .willSetStateTo("attempt #2")
    )

    server.givenThat(
      get("/posts")
        .inScenario("retry")
        .whenScenarioStateIs("attempt #2")
        .willReturn(notFound())
        .willSetStateTo("attempt #3")
    )

    server.givenThat(
      get("/posts")
        .inScenario("retry")
        .whenScenarioStateIs("attempt #3")
        .willReturn(okAndHtml(htmlFrom("/lunch/facebook/v1/page-name-extraction-test-heading.html")))
    )

    // when
    val result = client.load(pageConfig)

    // then
    result.name.value.shouldNotBeEmpty()
    result.posts should beEmpty()
  }

  "fails when all attempts to retrieve given page fail" {
    // given
    val client = JsoupPageClient(someClientConfig(retries = 2), listOf())
    val pageConfig = somePageConfig(url = URL(server.url("/posts")))

    // and
    server.resetAll()

    server.givenThat(
      get("/posts")
        .inScenario("retry")
        .whenScenarioStateIs(STARTED)
        .willReturn(badRequest())
        .willSetStateTo("attempt #2")
    )

    server.givenThat(
      get("/posts")
        .inScenario("retry")
        .whenScenarioStateIs("attempt #2")
        .willReturn(notFound())
        .willSetStateTo("attempt #3")
    )

    server.givenThat(
      get("/posts")
        .inScenario("retry")
        .whenScenarioStateIs("attempt #3")
        .willReturn(serviceUnavailable())
        .willSetStateTo("attempt #4")
    )

    server.givenThat(
      get("/posts")
        .inScenario("retry")
        .whenScenarioStateIs("attempt #4")
        .willReturn(okAndHtml(htmlFrom("/lunch/facebook/v1/page-name-extraction-test-heading.html")))
    )

    // expect
    shouldThrow<HttpStatusException> {
      client.load(pageConfig)
    }
  }

  data class PageNameExtractionTestCase(
    val locationDescription: String,
    val exampleFile: String,
    val expectedName: String,
  )

  "extracts page name" - {
    withData<PageNameExtractionTestCase>(
      { "from ${it.locationDescription}" },
      PageNameExtractionTestCase(
        "og:title meta tag",
        "/lunch/facebook/v1/page-name-extraction-test-og-title.html",
        "Some Lunch Page Name"
      ),
      PageNameExtractionTestCase(
        "twitter:title meta tag",
        "/lunch/facebook/v1/page-name-extraction-test-twitter-title.html",
        "Some Lunch Page Name"
      ),
      PageNameExtractionTestCase(
        "h1 heading",
        "/lunch/facebook/v1/page-name-extraction-test-heading.html",
        "Some Lunch Page Name"
      ),
    ) {

      // given
      val clientConfig = someClientConfig()
      val client = JsoupPageClient(clientConfig, listOf())
      val pageConfig = somePageConfig(url = URL(server.url("/posts")))

      // and
      server.givenThat(
        get("/posts").willReturn(okAndHtml(htmlFrom(it.exampleFile)))
      )

      // when
      val result = client.load(pageConfig)

      // then
      result.name shouldBe PageName(it.expectedName)
    }
  }

  "fails when page name can't be extracted" {
    // given
    val clientConfig = someClientConfig(userAgent = "Some User Agent")
    val client = JsoupPageClient(clientConfig, listOf())
    val pageConfig = somePageConfig(url = URL(server.url("/posts")))

    // and
    server.givenThat(
      get("/posts").willReturn(okAndHtml(htmlFrom("/lunch/facebook/v1/page-name-unextractable-test.html")))
    )

    // when
    val exception = shouldThrowExactly<IllegalArgumentException> {
      client.load(pageConfig)
    }

    // then
    exception.message should containIgnoringCase("page name")
  }
})
