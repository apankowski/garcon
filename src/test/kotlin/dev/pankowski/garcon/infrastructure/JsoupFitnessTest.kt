package dev.pankowski.garcon.infrastructure

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.*
import dev.pankowski.garcon.domain.toURL
import io.kotest.core.spec.style.FreeSpec
import io.kotest.extensions.wiremock.ListenerMode
import io.kotest.extensions.wiremock.WireMockListener
import io.kotest.matchers.shouldBe
import org.jsoup.helper.HttpConnection
import org.springframework.http.MediaType
import org.springframework.web.util.UriUtils
import kotlin.text.Charsets.UTF_8

class JsoupFitnessTest : FreeSpec({

  val server = WireMockServer(4321)
  listener(WireMockListener(server, ListenerMode.PER_SPEC))

  fun okHtml(html: String) =
    okForContentType(MediaType.TEXT_HTML.toString(), html)

  // Ensure that JSoup library properly handles URL-encoded redirects, since there were bugs in its
  // handling in some versions of the library, breaking the app. This will allow us to catch them
  // earlier in the future.
  "handles URL-encoded redirects" {
    // given
    val redirectingUrl = toURL(server.url("/redirect"))
    val targetUrl = toURL(server.url(UriUtils.encodePath("/target ąćę ?=&", UTF_8)))

    // and
    server.givenThat(get(redirectingUrl.path).willReturn(permanentRedirect(targetUrl.toString())))
    server.givenThat(get(targetUrl.path).willReturn(okHtml("<html><body>Passed</body></html>")))

    // when
    val document = HttpConnection.connect(redirectingUrl).get()

    // then
    document.text() shouldBe "Passed"
  }
})
