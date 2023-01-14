package dev.pankowski.garcon.infrastructure.facebook

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.*
import com.github.tomakehurst.wiremock.stubbing.Scenario.STARTED
import dev.pankowski.garcon.domain.PageName
import dev.pankowski.garcon.domain.someClientConfig
import dev.pankowski.garcon.domain.somePageConfig
import dev.pankowski.garcon.domain.somePost
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FreeSpec
import io.kotest.extensions.wiremock.ListenerMode
import io.kotest.extensions.wiremock.WireMockListener
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import org.jsoup.HttpStatusException
import org.springframework.http.MediaType
import java.net.SocketTimeoutException
import java.net.URL
import java.time.Duration

class JsoupFacebookPostClientTest : FreeSpec({

  val server = WireMockServer(4321)
  listener(WireMockListener(server, ListenerMode.PER_SPEC))

  fun okHtml(html: String) =
    okForContentType(MediaType.TEXT_HTML.toString(), html)

  fun htmlFrom(file: String) =
    when (val url = javaClass.getResource(file)) {
      null -> throw IllegalArgumentException("$file classpath resource doesn't exist")
      else -> url.readText()
    }

  "retrieves given page" {
    // given
    val clientConfig = someClientConfig(userAgent = "Some User Agent")
    val client = JsoupFacebookPostClient(clientConfig, listOf())
    val pageConfig = somePageConfig(url = URL(server.url("/posts")))

    // and
    server.givenThat(
      get("/posts")
        .willReturn(okHtml("<html><body>Some body</body></html>"))
    )

    // when
    client.fetch(pageConfig)

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
    val client = JsoupFacebookPostClient(clientConfig, listOf())
    val pageConfig = somePageConfig(url = URL(server.url("/posts")))

    // and
    server.givenThat(
      get("/posts")
        .willReturn(ok().withFixedDelay(200))
    )

    // expect
    shouldThrow<SocketTimeoutException> {
      client.fetch(pageConfig)
    }
  }

  "retries given page in case of failure" {
    // given
    val client = JsoupFacebookPostClient(someClientConfig(retries = 2), listOf())
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
        .willReturn(okHtml("<html><body>Some body</body></html>"))
    )

    // when
    val result = client.fetch(pageConfig)

    // then
    result shouldBe Pair(PageName(pageConfig.id.value), emptyList())
  }

  "fails when all attempts to retrieve given page fail" {
    // given
    val client = JsoupFacebookPostClient(someClientConfig(retries = 2), listOf())
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
        .willReturn(okHtml("<html><body>Some body</body></html>"))
    )

    // expect
    shouldThrow<HttpStatusException> {
      client.fetch(pageConfig)
    }
  }

  "extracts name from given page" {
    // given
    val clientConfig = someClientConfig(userAgent = "Some User Agent")
    val client = JsoupFacebookPostClient(clientConfig, listOf())
    val pageConfig = somePageConfig(url = URL(server.url("/posts")))

    // and
    server.givenThat(
      get("/posts")
        .willReturn(okHtml(htmlFrom("/lunch/facebook/v1/page-name-extraction-test.html")))
    )

    // when
    val result = client.fetch(pageConfig)

    // then
    result.first shouldBe PageName("Some Lunch Page Name")
  }

  "falls back to page ID in case name can't be extracted" {
    // given
    val clientConfig = someClientConfig(userAgent = "Some User Agent")
    val client = JsoupFacebookPostClient(clientConfig, listOf())
    val pageConfig = somePageConfig(url = URL(server.url("/posts")))

    // and
    server.givenThat(
      get("/posts")
        .willReturn(okHtml(htmlFrom("/lunch/facebook/v1/page-name-unextractable-test.html")))
    )

    // when
    val result = client.fetch(pageConfig)

    // then
    result.first shouldBe PageName(pageConfig.id.value)
  }

  "passes retrieved document to extraction strategies" {
    // given
    val pageConfig = somePageConfig(url = URL(server.url("/posts")))
    val strategy = mockk<FacebookPostExtractionStrategy>()
    val result = listOf(somePost())
    every { strategy.extractPosts(any()) } returns result

    val client = JsoupFacebookPostClient(someClientConfig(), listOf(strategy))

    // and
    server.givenThat(
      get("/posts")
        .willReturn(okHtml("<html><body>Some body</body></html>"))
    )

    // when
    val actualResult = client.fetch(pageConfig)

    // then
    actualResult.second shouldBe result
  }
})
