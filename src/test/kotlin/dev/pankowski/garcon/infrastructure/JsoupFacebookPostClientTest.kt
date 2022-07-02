package dev.pankowski.garcon.infrastructure

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.*
import com.github.tomakehurst.wiremock.stubbing.Scenario.STARTED
import dev.pankowski.garcon.domain.*
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FreeSpec
import io.kotest.extensions.wiremock.ListenerMode
import io.kotest.extensions.wiremock.WireMockListener
import io.kotest.matchers.shouldBe
import org.jsoup.HttpStatusException
import org.springframework.http.MediaType
import java.net.SocketTimeoutException
import java.net.URL
import java.time.Duration
import java.time.Instant

class JsoupFacebookPostClientTest : FreeSpec({

  val server = WireMockServer(4321)
  listener(WireMockListener(server, ListenerMode.PER_SPEC))

  fun okHtml(html: String) =
    okForContentType(MediaType.TEXT_HTML.toString(), html)

  "retrieves given page" {
    // given
    val clientConfig = someClientConfig(userAgent = "Some User Agent")
    val client = JsoupFacebookPostClient(clientConfig)
    val pageConfig = somePageConfig(url = URL(server.url("/posts")))

    // and
    server.givenThat(
      get("/posts")
        .willReturn(okHtml("<html><body>Some body</body></html>"))
    )

    // when
    client.fetch(pageConfig, null)

    // then
    server.verify(
      getRequestedFor(urlEqualTo("/posts"))
        .withHeader("User-Agent", equalTo(clientConfig.userAgent))
        .withHeader("Accept", equalTo("text/html,application/xhtml+xml"))
        .withHeader("Accept-Language", equalTo("pl,en;q=0.5"))
        .withHeader("Cache-Control", equalTo("no-cache"))
        .withHeader("Pragma", equalTo("no-cache"))
    )
  }

  "honors specified timeout" {
    // given
    val clientConfig = someClientConfig(timeout = Duration.ofMillis(100))
    val client = JsoupFacebookPostClient(clientConfig)
    val pageConfig = somePageConfig(url = URL(server.url("/posts")))

    // and
    server.givenThat(
      get("/posts")
        .willReturn(ok().withFixedDelay(200))
    )

    // expect
    shouldThrow<SocketTimeoutException> {
      client.fetch(pageConfig, null)
    }
  }

  fun htmlFrom(file: String) =
    when (val url = javaClass.getResource(file)) {
      null -> throw IllegalArgumentException("$file classpath resource doesn't exist")
      else -> url.readText()
    }

  "retries given page in case of failure" {
    // given
    val client = JsoupFacebookPostClient(someClientConfig(retries = 2))
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
    val nameAndPosts = client.fetch(pageConfig, null)

    // then
    nameAndPosts shouldBe Pair(null, emptyList())
  }

  "fails when all attempts to retrieve given page fail" {
    // given
    val client = JsoupFacebookPostClient(someClientConfig(retries = 2))
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
      client.fetch(pageConfig, null)
    }
  }

  "extracts name from given page" {
    // given
    val client = JsoupFacebookPostClient(someClientConfig())
    val pageConfig = somePageConfig(url = URL(server.url("/posts")))

    // and
    server.givenThat(
      get("/posts")
        .willReturn(okHtml(htmlFrom("/lunch/facebook/page-name-extraction-test.html")))
    )

    // when
    val nameAndPosts = client.fetch(pageConfig, null)

    // then
    nameAndPosts shouldBe Pair(PageName("Some Lunch Page Name"), emptyList())
  }

  "falls back to page ID in case name can't be extracted" {
    // given
    val client = JsoupFacebookPostClient(someClientConfig())
    val pageConfig = somePageConfig(url = URL(server.url("/posts")))

    // and
    server.givenThat(
      get("/posts")
        .willReturn(okHtml(htmlFrom("/lunch/facebook/page-name-unextractable-test.html")))
    )

    // when
    val nameAndPosts = client.fetch(pageConfig, null)

    // then
    nameAndPosts shouldBe Pair(null, emptyList())
  }

  "extracts posts from given page" {
    // given
    val client = JsoupFacebookPostClient(someClientConfig())
    val pageConfig = somePageConfig(url = URL(server.url("/posts")))

    // and
    server.givenThat(
      get("/posts")
        .willReturn(okHtml(htmlFrom("/lunch/facebook/post-extraction-test.html")))
    )

    // when
    val nameAndPosts = client.fetch(pageConfig, null)

    // then
    nameAndPosts shouldBe Pair(
      null,
      listOf(
        Post(
          ExternalId("0"),
          URL("https://www.facebook.com/0"),
          Instant.ofEpochSecond(0),
          "Some content"
        )
      )
    )
  }

  "ignores unextractable posts" {
    // given
    val client = JsoupFacebookPostClient(someClientConfig())
    val pageConfig = somePageConfig(url = URL(server.url("/posts")))

    // and
    server.givenThat(
      get("/posts")
        .willReturn(okHtml(htmlFrom("/lunch/facebook/post-unextractable-test.html")))
    )

    // when
    val nameAndPosts = client.fetch(pageConfig, null)

    // then
    nameAndPosts shouldBe Pair(null, emptyList())
  }

  "returns posts sorted by published date" {
    // given
    val client = JsoupFacebookPostClient(someClientConfig())
    val pageConfig = somePageConfig(url = URL(server.url("/posts")))

    // and
    server.givenThat(
      get("/posts")
        .willReturn(okHtml(htmlFrom("/lunch/facebook/post-sorting-test.html")))
    )

    // when
    val nameAndPosts = client.fetch(pageConfig, null)

    // then
    nameAndPosts shouldBe Pair(
      null,
      listOf(
        Post(
          ExternalId("1"),
          URL("https://www.facebook.com/1"),
          Instant.ofEpochSecond(1),
          "Some content 1"
        ),
        Post(
          ExternalId("2"),
          URL("https://www.facebook.com/2"),
          Instant.ofEpochSecond(2),
          "Some content 2"
        )
      )
    )
  }

  "extracts posts from a real page" {
    // given
    val client = JsoupFacebookPostClient(someClientConfig())
    val pageConfig = somePageConfig(url = URL(server.url("/posts")))

    // and
    server.givenThat(
      get("/posts")
        .willReturn(okHtml(htmlFrom("/lunch/facebook/real-page-test.html")))
    )

    // when
    val nameAndPosts = client.fetch(pageConfig, null)

    // then
    nameAndPosts shouldBe Pair(
      PageName("Pół Żartem Pół Serio"),
      listOf(
        Post(
          ExternalId("2342169022692189"),
          URL("https://www.facebook.com/2342169022692189"),
          Instant.ofEpochSecond(1558799401),
          """
          |Z okazji Dnia Mamy wszystkim Mamom składamy serdecznie życzenia
          |i dla wszystkich mam mamy słodką niespodziankę - zapraszamy 26 maja
          |do Pół Żartem Pół Serio slow bistro
          """.trimMargin()
        ),
        Post(
          ExternalId("2346741902234901"),
          URL("https://www.facebook.com/2346741902234901"),
          Instant.ofEpochSecond(1559387087),
          """
          |Schabowy z kostką i młodą kapustą
          |i gravlax z łososia
          """.trimMargin()
        ),
        Post(
          ExternalId("2346903232218768"),
          URL("https://www.facebook.com/2346903232218768"),
          Instant.ofEpochSecond(1559402930),
          """
          |Sałatka z owoców morza
          |i filet z łososia na risotto
          """.trimMargin()
        ),
        Post(
          ExternalId("2350532298522528"),
          URL("https://www.facebook.com/2350532298522528"),
          Instant.ofEpochSecond(1559822687),
          """
          |Dziś w cafe: tarta z truskawkami
          |oraz tort bezowy tiramisu .
          |Naleśnikarka uruchomiona - w ofercie:
          |naleśnik z cukrem pudrem
          |naleśnik z dżemem
          |naleśnik z owocami i polewą czekoladową
          """.trimMargin()
        ),
        Post(
          ExternalId("2352062648369493"),
          URL("https://www.facebook.com/2352062648369493"),
          Instant.ofEpochSecond(1559985730),
          """
          |Zapraszamy do Pół Żartem Pół Serio cafe
          |na tapas:
          |* chorizo z karmelizową cebulą i kaparowcem
          |* łosoś wędzony z limonką i avokado
          |* mango, krewetka i kolendra
          |* pasta z pieczonego bakłażana
          |* ser kozi, owoce i szpinak
          """.trimMargin()
        ),
        Post(
          ExternalId("2357670637808694"),
          URL("https://www.facebook.com/2357670637808694"),
          Instant.ofEpochSecond(1560623524),
          """
          |Dziś  w Pół Żartem Pół Serio świętujemy urodziny Pana Tomka
          """.trimMargin()
        ),
        Post(
          ExternalId("2362955620613529"),
          URL("https://www.facebook.com/2362955620613529"),
          Instant.ofEpochSecond(1561205318),
          """
          |Półmisek greckich przekąsek
          |(pasta z bobu,hummus,karczochy)podawane z pitą🌮
          """.trimMargin()
        ),
        Post(
          ExternalId("2363823310526760"),
          URL("https://www.facebook.com/2363823310526760"),
          Instant.ofEpochSecond(1561297183),
          """
          |Po co jechać do Kampinosu, jak w Pół Żartem Pół Serio Cafe mamy trochę Lasu 🌲🌲🌲
          |Zapraszamy na nowe pyszne ciacho o wdzięcznej nazwie LEŚNY MECH 😁
          """.trimMargin()
        ),
        Post(
          ExternalId("2364540033788421"),
          URL("https://www.facebook.com/2364540033788421"),
          Instant.ofEpochSecond(1561376258),
          """
          |Kto nie lubi poniedziałków to z nami je pokocha 😍 Dziś na dobry początek tygodnia mamy dla Was kolejne pyszności! Ciacho Owocowy Raj już dostępne w Pół Żartem Pół Serio Cafe  🍭☕️🍰🍨 Zapraszamy!
          """.trimMargin()
        ),
        Post(
          ExternalId("2367006246875133"),
          URL("https://www.facebook.com/2367006246875133"),
          Instant.ofEpochSecond(1561639211),
          """
          |Odrobina słodyczy każdemu się przyda 😁😋🤗 Dziś do Pół Żartem Pół Serio Cafe na Tartę Cytrynową 🍋🍋🍋 i Bezę z owocami sezonowymi 🍓🍓🍓
          """.trimMargin()
        ),
        Post(
          ExternalId("2369216289987462"),
          URL("https://www.facebook.com/2369216289987462"),
          Instant.ofEpochSecond(1561900524),
          """
          |Żar bucha z nieba ☀️☀️☀️ to coś na ochłodę potrzeba! 🍦🍦🍦 Zapraszamy do Pół Żartem Pół Serio Cafe 🍭🍭🍭na lody rzemieślnicze i kawę mrożoną! 😋🌈🦄🍒
          """.trimMargin()
        ),
        Post(
          ExternalId("2371503593092065"),
          URL("https://www.facebook.com/2371503593092065"),
          Instant.ofEpochSecond(1562160573),
          """
          |Już od dziś 4 edycja konkursu na najfajnieszy magnes z wakacji:
          |1 nagroda zaproszenie do bistro
          |2 nagroda pyszna butelka wina
          |3 nagroda zaproszenie do cafe
          |Konkurs trwa do 1 września 2019
          """.trimMargin()
        ),
        Post(
          ExternalId("2375021466073611"),
          URL("https://www.facebook.com/2375021466073611"),
          Instant.ofEpochSecond(1562595849),
          """
          |Pół Żartem Pół Serio Cafe poleca dzisiaj:
          |
          |Tort czekoladowo-orzechowy z konfiturą owocową 🍰🍫🌰🍒🍓
          |
          |Zapraszamy 😁
          """.trimMargin()
        ),
        Post(
          ExternalId("2376464095929348"),
          URL("https://www.facebook.com/2376464095929348"),
          Instant.ofEpochSecond(1562771399),
          """
          |Dziś do Pół Żartem Poł Serio Cafe zapraszamy na klasykę polskich serników - Sernik Rosa 🍰🍰🍰, czyli delikatna i pyszna masa serowa z bezową chmurką na tradycyjnym kruchym spodzie. Pychota! Życzymy Smacznego 🤗
          """.trimMargin()
        ),
        Post(
          ExternalId("2377153185860439"),
          URL("https://www.facebook.com/2377153185860439"),
          Instant.ofEpochSecond(1562856474),
          """
          |Delikatne kruche ciasto, aksamitna masa budyniowa, świeże owoce i orzeźwiająca cytrynowa galaretka. 🍓🍇🥝🍋 To znakomite połączenie i rewelacyjny smak naszej TARTY, którą już dziś można dostać w Pół Żartem Pół Serio Cafe ☕️🍰 Zapraszamy 🤗
          """.trimMargin()
        ),
        Post(
          ExternalId("2377781032464321"),
          URL("https://www.facebook.com/2377781032464321"),
          Instant.ofEpochSecond(1562936915),
          """
          |Przedstawiamy kolejne pyszności dostępne dziś w naszej kawiarni Pół Żartem Pół Serio Cafe ☕️🍰:
          |
          |🍭BEZA z masą mascarpone - uwielbiana przez wszystkich,
          |🍭TARTA z pieczonym jogurtem greckim - lekka i wyjątkowa w smaku,
          |*obie z owocami sezonowymi 🍓🥝🍇).
          |
          |Zapraszamy i życzymy smacznego 🤗
          """.trimMargin()
        ),
        Post(
          ExternalId("2378495385726219"),
          URL("https://www.facebook.com/2378495385726219"),
          Instant.ofEpochSecond(1563025708),
          """
          |Dziś w Pol Żartem Pół Serio Cafe 🍰☕️ polecamy pyszne Buraczane Ciasto , czyli Murzynek w wersji wegańskiej 😁😋 Zapraszamy! 🤗
          """.trimMargin()
        ),
        Post(
          ExternalId("2385517911690633"),
          URL("https://www.facebook.com/2385517911690633"),
          Instant.ofEpochSecond(1563869152),
          """
          |Lunch wtorek
          |
          |Zupa dnia
          |Kalafiorowa
          |Flamandzka
          |
          |Danie główne
          |Gołąbki w sosie pomidorowym  z ziemniakami puree
          |Stripsy z kurczaka z ryżem I surówką colesław
          |Pierogi ruskie z cebulką
          |
          |Zapraszamy🍲🍗😆
          """.trimMargin()
        ),
        Post(
          ExternalId("2385635615012196"),
          URL("https://www.facebook.com/2385635615012196"),
          Instant.ofEpochSecond(1563883027),
          """
          |Zapraszamy na pyszną świeżą sielawę🐟🐟🐟
          """.trimMargin()
        ),
      )
    )
  }
})
