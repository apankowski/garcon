package dev.pankowski.garcon.domain

import com.github.tomakehurst.wiremock.WireMockServer
import kotlin.Pair
import org.jsoup.HttpStatusException
import org.springframework.http.MediaType
import spock.lang.Shared
import spock.lang.Specification

import java.time.Duration
import java.time.Instant

import static com.github.tomakehurst.wiremock.client.WireMock.badRequest
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo
import static com.github.tomakehurst.wiremock.client.WireMock.get
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
import static com.github.tomakehurst.wiremock.client.WireMock.notFound
import static com.github.tomakehurst.wiremock.client.WireMock.ok
import static com.github.tomakehurst.wiremock.client.WireMock.okForContentType
import static com.github.tomakehurst.wiremock.client.WireMock.serviceUnavailable
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import static com.github.tomakehurst.wiremock.stubbing.Scenario.STARTED

class FacebookPostClientTest extends Specification {

  @Shared
  def server = new WireMockServer(4321)

  def setupSpec() {
    server.start()
  }

  def setup() {
    server.resetAll()
  }

  def cleanupSpec() {
    server.stop()
  }

  def somePageConfig() {
    new LunchPageConfig(
      new PageId("LP1"),
      new URL("http://localhost:4321/posts")
    )
  }

  def okHtml(String html) {
    okForContentType(MediaType.TEXT_HTML_VALUE, html)
  }

  def "should retrieve given page"() {
    given:
    def clientConfig = new LunchClientConfig("Some User Agent", Duration.ofSeconds(5))
    def client = new FacebookPostClient(clientConfig)
    def pageConfig = somePageConfig()

    and:
    server.givenThat(get("/posts")
      .willReturn(okHtml("<html><body>Some body</body></html>")))

    when:
    client.fetch(pageConfig, null)

    then:
    server.verify(getRequestedFor(urlEqualTo("/posts"))
      .withHeader("User-Agent", equalTo(clientConfig.getUserAgent()))
      .withHeader("Accept", equalTo("text/html,application/xhtml+xml"))
      .withHeader("Accept-Language", equalTo("pl,en;q=0.5"))
      .withHeader("Cache-Control", equalTo("no-cache"))
      .withHeader("Pragma", equalTo("no-cache")))
  }

  def "should honor specified timeout"() {
    given:
    def clientConfig = new LunchClientConfig("Some User Agent", Duration.ofMillis(100))
    def client = new FacebookPostClient(clientConfig)
    def pageConfig = somePageConfig()

    and:
    server.givenThat(get("/posts")
      .willReturn(ok().withFixedDelay(200)))

    when:
    client.fetch(pageConfig, null)

    then:
    thrown SocketTimeoutException
  }

  def somePostClient() {
    def clientConfig = new LunchClientConfig("Some User Agent", Duration.ofSeconds(5))
    new FacebookPostClient(clientConfig)
  }

  def htmlFrom(String file) {
    getClass().getResource(file).text
  }

  def "should attempt retrieving given page 3 times in case of failure"() {
    given:
    def client = somePostClient()
    def pageConfig = somePageConfig()

    and:
    server.givenThat(get("/posts")
      .inScenario("retry")
      .whenScenarioStateIs(STARTED)
      .willReturn(badRequest())
      .willSetStateTo("attempt #2"))

    server.givenThat(get("/posts")
      .inScenario("retry")
      .whenScenarioStateIs("attempt #2")
      .willReturn(notFound())
      .willSetStateTo("attempt #3"))

    server.givenThat(get("/posts")
      .inScenario("retry")
      .whenScenarioStateIs("attempt #3")
      .willReturn(okHtml("<html><body>Some body</body></html>")))

    when:
    def nameAndPosts = client.fetch(pageConfig, null)

    then:
    nameAndPosts == new Pair(null, [])
  }

  def "should fail when all 3 attempts to retrieve given page fail"() {
    given:
    def client = somePostClient()
    def pageConfig = somePageConfig()

    and:
    server.givenThat(get("/posts")
      .inScenario("retry")
      .whenScenarioStateIs(STARTED)
      .willReturn(badRequest())
      .willSetStateTo("attempt #2"))

    server.givenThat(get("/posts")
      .inScenario("retry")
      .whenScenarioStateIs("attempt #2")
      .willReturn(notFound())
      .willSetStateTo("attempt #3"))

    server.givenThat(get("/posts")
      .inScenario("retry")
      .whenScenarioStateIs("attempt #3")
      .willReturn(serviceUnavailable())
      .willSetStateTo("attempt #4"))

    server.givenThat(get("/posts")
      .inScenario("retry")
      .whenScenarioStateIs("attempt #4")
      .willReturn(okHtml("<html><body>Some body</body></html>")))

    when:
    client.fetch(pageConfig, null)

    then:
    thrown HttpStatusException
  }

  def "should extract name from given page"() {
    given:
    def client = somePostClient()
    def pageConfig = somePageConfig()

    and:
    server.givenThat(get("/posts")
      .willReturn(okHtml(htmlFrom("/lunch/facebook/page-name-extraction-test.html"))))

    when:
    def nameAndPosts = client.fetch(pageConfig, null)

    then:
    nameAndPosts == new Pair(new PageName("Some Lunch Page Name"), [])
  }

  def "should fall back to page ID in case name can't be extracted"() {
    given:
    def client = somePostClient()
    def pageConfig = somePageConfig()

    and:
    server.givenThat(get("/posts")
      .willReturn(okHtml(htmlFrom("/lunch/facebook/page-name-unextractable-test.html"))))

    when:
    def nameAndPosts = client.fetch(pageConfig, null)

    then:
    nameAndPosts == new Pair(null, [])
  }

  def "should extract posts from given page"() {
    given:
    def client = somePostClient()
    def pageConfig = somePageConfig()

    and:
    server.givenThat(get("/posts")
      .willReturn(okHtml(htmlFrom("/lunch/facebook/post-extraction-test.html"))))

    when:
    def nameAndPosts = client.fetch(pageConfig, null)

    then:
    nameAndPosts == new Pair(
      null,
      [
        new Post(
          new ExternalId("0"),
          new URI("https://www.facebook.com/0"),
          Instant.ofEpochSecond(0),
          "Some content"
        )
      ],
    )
  }

  def "should ignore unextractable posts"() {
    given:
    def client = somePostClient()
    def pageConfig = somePageConfig()

    and:
    server.givenThat(get("/posts")
      .willReturn(okHtml(htmlFrom("/lunch/facebook/post-unextractable-test.html"))))

    when:
    def nameAndPosts = client.fetch(pageConfig, null)

    then:
    nameAndPosts == new Pair(null, [])
  }

  def "should return posts sorted by published date"() {
    given:
    def client = somePostClient()
    def pageConfig = somePageConfig()

    and:
    server.givenThat(get("/posts")
      .willReturn(okHtml(htmlFrom("/lunch/facebook/post-sorting-test.html"))))

    when:
    def nameAndPosts = client.fetch(pageConfig, null)

    then:
    nameAndPosts == new Pair(
      null,
      [
        new Post(
          new ExternalId("1"),
          new URI("https://www.facebook.com/1"),
          Instant.ofEpochSecond(1),
          "Some content 1"
        ),
        new Post(
          new ExternalId("2"),
          new URI("https://www.facebook.com/2"),
          Instant.ofEpochSecond(2),
          "Some content 2"
        )
      ],
    )
  }

  def "should return posts newer than specified published date"() {
    given:
    def client = somePostClient()
    def pageConfig = somePageConfig()

    and:
    server.givenThat(get("/posts")
      .willReturn(okHtml(htmlFrom("/lunch/facebook/post-sorting-test.html"))))

    when:
    def nameAndPosts = client.fetch(pageConfig, Instant.ofEpochSecond(1))

    then:
    nameAndPosts == new Pair(
      null,
      [
        new Post(
          new ExternalId("2"),
          new URI("https://www.facebook.com/2"),
          Instant.ofEpochSecond(2),
          "Some content 2"
        )
      ],
    )
  }

  def "should extract posts from a real page"() {
    given:
    def client = somePostClient()
    def pageConfig = somePageConfig()

    and:
    server.givenThat(get("/posts")
      .willReturn(okHtml(htmlFrom("/lunch/facebook/real-page-test.html"))))

    when:
    def nameAndPosts = client.fetch(pageConfig, null)

    then:
    nameAndPosts == new Pair(
      new PageName("Pół Żartem Pół Serio"),
      [
        new Post(
          new ExternalId("2342169022692189"),
          URI.create("https://www.facebook.com/2342169022692189"),
          Instant.ofEpochSecond(1558799401),
          """\
          |Z okazji Dnia Mamy wszystkim Mamom składamy serdecznie życzenia
          |i dla wszystkich mam mamy słodką niespodziankę - zapraszamy 26 maja
          |do Pół Żartem Pół Serio slow bistro""".stripMargin()
        ),
        new Post(
          new ExternalId("2346741902234901"),
          URI.create("https://www.facebook.com/2346741902234901"),
          Instant.ofEpochSecond(1559387087),
          """\
          |Schabowy z kostką i młodą kapustą
          |i gravlax z łososia""".stripMargin()
        ),
        new Post(
          new ExternalId("2346903232218768"),
          URI.create("https://www.facebook.com/2346903232218768"),
          Instant.ofEpochSecond(1559402930),
          """\
          |Sałatka z owoców morza
          |i filet z łososia na risotto""".stripMargin()
        ),
        new Post(
          new ExternalId("2350532298522528"),
          URI.create("https://www.facebook.com/2350532298522528"),
          Instant.ofEpochSecond(1559822687),
          """\
          |Dziś w cafe: tarta z truskawkami
          |oraz tort bezowy tiramisu .
          |Naleśnikarka uruchomiona - w ofercie:
          |naleśnik z cukrem pudrem
          |naleśnik z dżemem
          |naleśnik z owocami i polewą czekoladową""".stripMargin()
        ),
        new Post(
          new ExternalId("2352062648369493"),
          URI.create("https://www.facebook.com/2352062648369493"),
          Instant.ofEpochSecond(1559985730),
          """\
          |Zapraszamy do Pół Żartem Pół Serio cafe
          |na tapas:
          |* chorizo z karmelizową cebulą i kaparowcem
          |* łosoś wędzony z limonką i avokado
          |* mango, krewetka i kolendra
          |* pasta z pieczonego bakłażana
          |* ser kozi, owoce i szpinak""".stripMargin()
        ),
        new Post(
          new ExternalId("2357670637808694"),
          URI.create("https://www.facebook.com/2357670637808694"),
          Instant.ofEpochSecond(1560623524),
          """\
          |Dziś  w Pół Żartem Pół Serio świętujemy urodziny Pana Tomka""".stripMargin()
        ),
        new Post(
          new ExternalId("2362955620613529"),
          URI.create("https://www.facebook.com/2362955620613529"),
          Instant.ofEpochSecond(1561205318),
          """\
          |Półmisek greckich przekąsek
          |(pasta z bobu,hummus,karczochy)podawane z pitą🌮""".stripMargin()
        ),
        new Post(
          new ExternalId("2363823310526760"),
          URI.create("https://www.facebook.com/2363823310526760"),
          Instant.ofEpochSecond(1561297183),
          """\
          |Po co jechać do Kampinosu, jak w Pół Żartem Pół Serio Cafe mamy trochę Lasu 🌲🌲🌲
          |Zapraszamy na nowe pyszne ciacho o wdzięcznej nazwie LEŚNY MECH 😁""".stripMargin()
        ),
        new Post(
          new ExternalId("2364540033788421"),
          URI.create("https://www.facebook.com/2364540033788421"),
          Instant.ofEpochSecond(1561376258),
          """\
          |Kto nie lubi poniedziałków to z nami je pokocha 😍 Dziś na dobry początek tygodnia mamy dla Was kolejne pyszności! Ciacho Owocowy Raj już dostępne w Pół Żartem Pół Serio Cafe  🍭☕️🍰🍨 Zapraszamy!""".stripMargin()
        ),
        new Post(
          new ExternalId("2367006246875133"),
          URI.create("https://www.facebook.com/2367006246875133"),
          Instant.ofEpochSecond(1561639211),
          """\
          |Odrobina słodyczy każdemu się przyda 😁😋🤗 Dziś do Pół Żartem Pół Serio Cafe na Tartę Cytrynową 🍋🍋🍋 i Bezę z owocami sezonowymi 🍓🍓🍓""".stripMargin()
        ),
        new Post(
          new ExternalId("2369216289987462"),
          URI.create("https://www.facebook.com/2369216289987462"),
          Instant.ofEpochSecond(1561900524),
          """\
          |Żar bucha z nieba ☀️☀️☀️ to coś na ochłodę potrzeba! 🍦🍦🍦 Zapraszamy do Pół Żartem Pół Serio Cafe 🍭🍭🍭na lody rzemieślnicze i kawę mrożoną! 😋🌈🦄🍒""".stripMargin()
        ),
        new Post(
          new ExternalId("2371503593092065"),
          URI.create("https://www.facebook.com/2371503593092065"),
          Instant.ofEpochSecond(1562160573),
          """\
          |Już od dziś 4 edycja konkursu na najfajnieszy magnes z wakacji:
          |1 nagroda zaproszenie do bistro
          |2 nagroda pyszna butelka wina
          |3 nagroda zaproszenie do cafe
          |Konkurs trwa do 1 września 2019""".stripMargin()
        ),
        new Post(
          new ExternalId("2375021466073611"),
          URI.create("https://www.facebook.com/2375021466073611"),
          Instant.ofEpochSecond(1562595849),
          """\
          |Pół Żartem Pół Serio Cafe poleca dzisiaj:
          |
          |Tort czekoladowo-orzechowy z konfiturą owocową 🍰🍫🌰🍒🍓
          |
          |Zapraszamy 😁""".stripMargin()
        ),
        new Post(
          new ExternalId("2376464095929348"),
          URI.create("https://www.facebook.com/2376464095929348"),
          Instant.ofEpochSecond(1562771399),
          """\
          |Dziś do Pół Żartem Poł Serio Cafe zapraszamy na klasykę polskich serników - Sernik Rosa 🍰🍰🍰, czyli delikatna i pyszna masa serowa z bezową chmurką na tradycyjnym kruchym spodzie. Pychota! Życzymy Smacznego 🤗""".stripMargin()
        ),
        new Post(
          new ExternalId("2377153185860439"),
          URI.create("https://www.facebook.com/2377153185860439"),
          Instant.ofEpochSecond(1562856474),
          """\
          |Delikatne kruche ciasto, aksamitna masa budyniowa, świeże owoce i orzeźwiająca cytrynowa galaretka. 🍓🍇🥝🍋 To znakomite połączenie i rewelacyjny smak naszej TARTY, którą już dziś można dostać w Pół Żartem Pół Serio Cafe ☕️🍰 Zapraszamy 🤗""".stripMargin()
        ),
        new Post(
          new ExternalId("2377781032464321"),
          URI.create("https://www.facebook.com/2377781032464321"),
          Instant.ofEpochSecond(1562936915),
          """\
          |Przedstawiamy kolejne pyszności dostępne dziś w naszej kawiarni Pół Żartem Pół Serio Cafe ☕️🍰:
          |
          |🍭BEZA z masą mascarpone - uwielbiana przez wszystkich,
          |🍭TARTA z pieczonym jogurtem greckim - lekka i wyjątkowa w smaku,
          |*obie z owocami sezonowymi 🍓🥝🍇).
          |
          |Zapraszamy i życzymy smacznego 🤗""".stripMargin()
        ),
        new Post(
          new ExternalId("2378495385726219"),
          URI.create("https://www.facebook.com/2378495385726219"),
          Instant.ofEpochSecond(1563025708),
          """\
          |Dziś w Pol Żartem Pół Serio Cafe 🍰☕️ polecamy pyszne Buraczane Ciasto , czyli Murzynek w wersji wegańskiej 😁😋 Zapraszamy! 🤗""".stripMargin()
        ),
        new Post(
          new ExternalId("2385517911690633"),
          URI.create("https://www.facebook.com/2385517911690633"),
          Instant.ofEpochSecond(1563869152),
          """\
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
          |Zapraszamy🍲🍗😆""".stripMargin()
        ),
        new Post(
          new ExternalId("2385635615012196"),
          URI.create("https://www.facebook.com/2385635615012196"),
          Instant.ofEpochSecond(1563883027),
          """\
          |Zapraszamy na pyszną świeżą sielawę🐟🐟🐟""".stripMargin()
        ),
      ]
    )
  }
}
