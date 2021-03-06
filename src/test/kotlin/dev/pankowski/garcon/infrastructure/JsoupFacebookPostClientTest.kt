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

  "returns posts newer than specified published date" {
    // given
    val client = JsoupFacebookPostClient(someClientConfig())
    val pageConfig = somePageConfig(url = URL(server.url("/posts")))

    // and
    server.givenThat(
      get("/posts")
        .willReturn(okHtml(htmlFrom("/lunch/facebook/post-sorting-test.html")))
    )

    // when
    val nameAndPosts = client.fetch(pageConfig, Instant.ofEpochSecond(1))

    // then
    nameAndPosts shouldBe Pair(
      null,
      listOf(
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
      PageName("P???? ??artem P???? Serio"),
      listOf(
        Post(
          ExternalId("2342169022692189"),
          URL("https://www.facebook.com/2342169022692189"),
          Instant.ofEpochSecond(1558799401),
          """
          |Z okazji Dnia Mamy wszystkim Mamom sk??adamy serdecznie ??yczenia
          |i dla wszystkich mam mamy s??odk?? niespodziank?? - zapraszamy 26 maja
          |do P???? ??artem P???? Serio slow bistro
          """.trimMargin()
        ),
        Post(
          ExternalId("2346741902234901"),
          URL("https://www.facebook.com/2346741902234901"),
          Instant.ofEpochSecond(1559387087),
          """
          |Schabowy z kostk?? i m??od?? kapust??
          |i gravlax z ??ososia
          """.trimMargin()
        ),
        Post(
          ExternalId("2346903232218768"),
          URL("https://www.facebook.com/2346903232218768"),
          Instant.ofEpochSecond(1559402930),
          """
          |Sa??atka z owoc??w morza
          |i filet z ??ososia na risotto
          """.trimMargin()
        ),
        Post(
          ExternalId("2350532298522528"),
          URL("https://www.facebook.com/2350532298522528"),
          Instant.ofEpochSecond(1559822687),
          """
          |Dzi?? w cafe: tarta z truskawkami
          |oraz tort bezowy tiramisu .
          |Nale??nikarka uruchomiona - w ofercie:
          |nale??nik z cukrem pudrem
          |nale??nik z d??emem
          |nale??nik z owocami i polew?? czekoladow??
          """.trimMargin()
        ),
        Post(
          ExternalId("2352062648369493"),
          URL("https://www.facebook.com/2352062648369493"),
          Instant.ofEpochSecond(1559985730),
          """
          |Zapraszamy do P???? ??artem P???? Serio cafe
          |na tapas:
          |* chorizo z karmelizow?? cebul?? i kaparowcem
          |* ??oso?? w??dzony z limonk?? i avokado
          |* mango, krewetka i kolendra
          |* pasta z pieczonego bak??a??ana
          |* ser kozi, owoce i szpinak
          """.trimMargin()
        ),
        Post(
          ExternalId("2357670637808694"),
          URL("https://www.facebook.com/2357670637808694"),
          Instant.ofEpochSecond(1560623524),
          """
          |Dzi??  w P???? ??artem P???? Serio ??wi??tujemy urodziny Pana Tomka
          """.trimMargin()
        ),
        Post(
          ExternalId("2362955620613529"),
          URL("https://www.facebook.com/2362955620613529"),
          Instant.ofEpochSecond(1561205318),
          """
          |P????misek greckich przek??sek
          |(pasta z bobu,hummus,karczochy)podawane z pit??????
          """.trimMargin()
        ),
        Post(
          ExternalId("2363823310526760"),
          URL("https://www.facebook.com/2363823310526760"),
          Instant.ofEpochSecond(1561297183),
          """
          |Po co jecha?? do Kampinosu, jak w P???? ??artem P???? Serio Cafe mamy troch?? Lasu ????????????
          |Zapraszamy na nowe pyszne ciacho o wdzi??cznej nazwie LE??NY MECH ????
          """.trimMargin()
        ),
        Post(
          ExternalId("2364540033788421"),
          URL("https://www.facebook.com/2364540033788421"),
          Instant.ofEpochSecond(1561376258),
          """
          |Kto nie lubi poniedzia??k??w to z nami je pokocha ???? Dzi?? na dobry pocz??tek tygodnia mamy dla Was kolejne pyszno??ci! Ciacho Owocowy Raj ju?? dost??pne w P???? ??artem P???? Serio Cafe  ?????????????????? Zapraszamy!
          """.trimMargin()
        ),
        Post(
          ExternalId("2367006246875133"),
          URL("https://www.facebook.com/2367006246875133"),
          Instant.ofEpochSecond(1561639211),
          """
          |Odrobina s??odyczy ka??demu si?? przyda ???????????? Dzi?? do P???? ??artem P???? Serio Cafe na Tart?? Cytrynow?? ???????????? i Bez?? z owocami sezonowymi ????????????
          """.trimMargin()
        ),
        Post(
          ExternalId("2369216289987462"),
          URL("https://www.facebook.com/2369216289987462"),
          Instant.ofEpochSecond(1561900524),
          """
          |??ar bucha z nieba ?????????????????? to co?? na och??od?? potrzeba! ???????????? Zapraszamy do P???? ??artem P???? Serio Cafe ????????????na lody rzemie??lnicze i kaw?? mro??on??! ????????????????
          """.trimMargin()
        ),
        Post(
          ExternalId("2371503593092065"),
          URL("https://www.facebook.com/2371503593092065"),
          Instant.ofEpochSecond(1562160573),
          """
          |Ju?? od dzi?? 4 edycja konkursu na najfajnieszy magnes z wakacji:
          |1 nagroda zaproszenie do bistro
          |2 nagroda pyszna butelka wina
          |3 nagroda zaproszenie do cafe
          |Konkurs trwa do 1 wrze??nia 2019
          """.trimMargin()
        ),
        Post(
          ExternalId("2375021466073611"),
          URL("https://www.facebook.com/2375021466073611"),
          Instant.ofEpochSecond(1562595849),
          """
          |P???? ??artem P???? Serio Cafe poleca dzisiaj:
          |
          |Tort czekoladowo-orzechowy z konfitur?? owocow?? ????????????????????
          |
          |Zapraszamy ????
          """.trimMargin()
        ),
        Post(
          ExternalId("2376464095929348"),
          URL("https://www.facebook.com/2376464095929348"),
          Instant.ofEpochSecond(1562771399),
          """
          |Dzi?? do P???? ??artem Po?? Serio Cafe zapraszamy na klasyk?? polskich sernik??w - Sernik Rosa ????????????, czyli delikatna i pyszna masa serowa z bezow?? chmurk?? na tradycyjnym kruchym spodzie. Pychota! ??yczymy Smacznego ????
          """.trimMargin()
        ),
        Post(
          ExternalId("2377153185860439"),
          URL("https://www.facebook.com/2377153185860439"),
          Instant.ofEpochSecond(1562856474),
          """
          |Delikatne kruche ciasto, aksamitna masa budyniowa, ??wie??e owoce i orze??wiaj??ca cytrynowa galaretka. ???????????????? To znakomite po????czenie i rewelacyjny smak naszej TARTY, kt??r?? ju?? dzi?? mo??na dosta?? w P???? ??artem P???? Serio Cafe ?????????? Zapraszamy ????
          """.trimMargin()
        ),
        Post(
          ExternalId("2377781032464321"),
          URL("https://www.facebook.com/2377781032464321"),
          Instant.ofEpochSecond(1562936915),
          """
          |Przedstawiamy kolejne pyszno??ci dost??pne dzi?? w naszej kawiarni P???? ??artem P???? Serio Cafe ??????????:
          |
          |????BEZA z mas?? mascarpone - uwielbiana przez wszystkich,
          |????TARTA z pieczonym jogurtem greckim - lekka i wyj??tkowa w smaku,
          |*obie z owocami sezonowymi ????????????).
          |
          |Zapraszamy i ??yczymy smacznego ????
          """.trimMargin()
        ),
        Post(
          ExternalId("2378495385726219"),
          URL("https://www.facebook.com/2378495385726219"),
          Instant.ofEpochSecond(1563025708),
          """
          |Dzi?? w Pol ??artem P???? Serio Cafe ?????????? polecamy pyszne Buraczane Ciasto , czyli Murzynek w wersji wega??skiej ???????? Zapraszamy! ????
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
          |Danie g????wne
          |Go????bki w sosie pomidorowym  z ziemniakami puree
          |Stripsy z kurczaka z ry??em I sur??wk?? coles??aw
          |Pierogi ruskie z cebulk??
          |
          |Zapraszamy????????????
          """.trimMargin()
        ),
        Post(
          ExternalId("2385635615012196"),
          URL("https://www.facebook.com/2385635615012196"),
          Instant.ofEpochSecond(1563883027),
          """
          |Zapraszamy na pyszn?? ??wie???? sielaw??????????????
          """.trimMargin()
        ),
      )
    )
  }
})
