package dev.pankowski.garcon.infrastructure.facebook

import dev.pankowski.garcon.domain.FacebookPostId
import dev.pankowski.garcon.domain.Post
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.collections.beEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.should
import org.jsoup.Jsoup
import java.net.URL
import java.time.Instant

class PostExtractionStrategyV1Test : FreeSpec({

  val strategy = PostExtractionStrategyV1()

  fun htmlFrom(file: String) =
    when (val url = javaClass.getResource(file)) {
      null -> throw IllegalArgumentException("$file classpath resource doesn't exist")
      else -> url.readText()
    }

  fun documentFromFile(file: String) =
    Jsoup.parse(htmlFrom(file), "https://www.facebook.com/")

  "extracts posts from given page" {
    // given
    val document = documentFromFile("/lunch/facebook/v1/post-extraction-test.html")

    // when
    val result = strategy.extractPosts(document)

    // then
    result shouldContainExactly listOf(
      Post(
        FacebookPostId("0"),
        URL("https://www.facebook.com/permalink.php?story_fbid=0"),
        Instant.ofEpochSecond(0),
        "Some content"
      )
    )
  }

  "ignores unextractable posts" {
    // given
    val document = documentFromFile("/lunch/facebook/v1/post-unextractable-test.html")

    // when
    val result = strategy.extractPosts(document)

    // then
    result should beEmpty()
  }

  "extracts posts from a real page" {
    // given
    val document = documentFromFile("/lunch/facebook/v1/real-page-test.html")

    // when
    val result = strategy.extractPosts(document)

    // then
    result shouldContainExactly listOf(
      Post(
        FacebookPostId("2342169022692189"),
        URL("https://www.facebook.com/1597565460485886/photos/a.1678463395729425/2342169022692189/?type=3"),
        Instant.ofEpochSecond(1558799401),
        """
        |Z okazji Dnia Mamy wszystkim Mamom składamy serdecznie życzenia
        |i dla wszystkich mam mamy słodką niespodziankę - zapraszamy 26 maja
        |do Pół Żartem Pół Serio slow bistro
        """.trimMargin()
      ),
      Post(
        FacebookPostId("2346741902234901"),
        URL("https://www.facebook.com/1597565460485886/photos/a.1678463395729425/2346741902234901/?type=3"),
        Instant.ofEpochSecond(1559387087),
        """
        |Schabowy z kostką i młodą kapustą
        |i gravlax z łososia
        """.trimMargin()
      ),
      Post(
        FacebookPostId("2346903232218768"),
        URL("https://www.facebook.com/1597565460485886/photos/a.1678463395729425/2346903232218768/?type=3"),
        Instant.ofEpochSecond(1559402930),
        """
        |Sałatka z owoców morza
        |i filet z łososia na risotto
        """.trimMargin()
      ),
      Post(
        FacebookPostId("2350532298522528"),
        URL("https://www.facebook.com/1597565460485886/photos/a.1678463395729425/2350532298522528/?type=3"),
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
        FacebookPostId("2352062648369493"),
        URL("https://www.facebook.com/1597565460485886/photos/a.1678463395729425/2352062648369493/?type=3"),
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
        FacebookPostId("2357670637808694"),
        URL("https://www.facebook.com/1597565460485886/photos/a.1678463395729425/2357670637808694/?type=3"),
        Instant.ofEpochSecond(1560623524),
        """
        |Dziś  w Pół Żartem Pół Serio świętujemy urodziny Pana Tomka
        """.trimMargin()
      ),
      Post(
        FacebookPostId("2362955620613529"),
        URL("https://www.facebook.com/1597565460485886/photos/a.1678463395729425/2362955620613529/?type=3"),
        Instant.ofEpochSecond(1561205318),
        """
        |Półmisek greckich przekąsek
        |(pasta z bobu,hummus,karczochy)podawane z pitą🌮
        """.trimMargin()
      ),
      Post(
        FacebookPostId("2363823310526760"),
        URL("https://www.facebook.com/1597565460485886/photos/a.1678463395729425/2363823310526760/?type=3"),
        Instant.ofEpochSecond(1561297183),
        """
        |Po co jechać do Kampinosu, jak w Pół Żartem Pół Serio Cafe mamy trochę Lasu 🌲🌲🌲
        |Zapraszamy na nowe pyszne ciacho o wdzięcznej nazwie LEŚNY MECH 😁
        """.trimMargin()
      ),
      Post(
        FacebookPostId("2364540033788421"),
        URL("https://www.facebook.com/1597565460485886/photos/a.1678463395729425/2364540033788421/?type=3"),
        Instant.ofEpochSecond(1561376258),
        """
        |Kto nie lubi poniedziałków to z nami je pokocha 😍 Dziś na dobry początek tygodnia mamy dla Was kolejne pyszności! Ciacho Owocowy Raj już dostępne w Pół Żartem Pół Serio Cafe  🍭☕️🍰🍨 Zapraszamy!
        """.trimMargin()
      ),
      Post(
        FacebookPostId("2367006246875133"),
        URL("https://www.facebook.com/permalink.php?story_fbid=2367006246875133&id=1597565460485886"),
        Instant.ofEpochSecond(1561639211),
        """
        |Odrobina słodyczy każdemu się przyda 😁😋🤗 Dziś do Pół Żartem Pół Serio Cafe na Tartę Cytrynową 🍋🍋🍋 i Bezę z owocami sezonowymi 🍓🍓🍓
        """.trimMargin()
      ),
      Post(
        FacebookPostId("2369216289987462"),
        URL("https://www.facebook.com/permalink.php?story_fbid=2369216289987462&id=1597565460485886"),
        Instant.ofEpochSecond(1561900524),
        """
        |Żar bucha z nieba ☀️☀️☀️ to coś na ochłodę potrzeba! 🍦🍦🍦 Zapraszamy do Pół Żartem Pół Serio Cafe 🍭🍭🍭na lody rzemieślnicze i kawę mrożoną! 😋🌈🦄🍒
        """.trimMargin()
      ),
      Post(
        FacebookPostId("2371503593092065"),
        URL("https://www.facebook.com/1597565460485886/photos/a.1678463395729425/2371503593092065/?type=3"),
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
        FacebookPostId("2375021466073611"),
        URL("https://www.facebook.com/1597565460485886/photos/a.1678463395729425/2375021466073611/?type=3"),
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
        FacebookPostId("2376464095929348"),
        URL("https://www.facebook.com/1597565460485886/photos/a.1678463395729425/2376464095929348/?type=3"),
        Instant.ofEpochSecond(1562771399),
        """
        |Dziś do Pół Żartem Poł Serio Cafe zapraszamy na klasykę polskich serników - Sernik Rosa 🍰🍰🍰, czyli delikatna i pyszna masa serowa z bezową chmurką na tradycyjnym kruchym spodzie. Pychota! Życzymy Smacznego 🤗
        """.trimMargin()
      ),
      Post(
        FacebookPostId("2377153185860439"),
        URL("https://www.facebook.com/1597565460485886/photos/a.1678463395729425/2377153185860439/?type=3"),
        Instant.ofEpochSecond(1562856474),
        """
        |Delikatne kruche ciasto, aksamitna masa budyniowa, świeże owoce i orzeźwiająca cytrynowa galaretka. 🍓🍇🥝🍋 To znakomite połączenie i rewelacyjny smak naszej TARTY, którą już dziś można dostać w Pół Żartem Pół Serio Cafe ☕️🍰 Zapraszamy 🤗
        """.trimMargin()
      ),
      Post(
        FacebookPostId("2377781032464321"),
        URL("https://www.facebook.com/permalink.php?story_fbid=2377781032464321&id=1597565460485886"),
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
        FacebookPostId("2378495385726219"),
        URL("https://www.facebook.com/1597565460485886/photos/a.1678463395729425/2378495385726219/?type=3"),
        Instant.ofEpochSecond(1563025708),
        """
        |Dziś w Pol Żartem Pół Serio Cafe 🍰☕️ polecamy pyszne Buraczane Ciasto , czyli Murzynek w wersji wegańskiej 😁😋 Zapraszamy! 🤗
        """.trimMargin()
      ),
      Post(
        FacebookPostId("2385517911690633"),
        URL("https://www.facebook.com/permalink.php?story_fbid=2385517911690633&id=1597565460485886"),
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
        FacebookPostId("2385635615012196"),
        URL("https://www.facebook.com/1597565460485886/photos/a.1678463395729425/2385635615012196/?type=3"),
        Instant.ofEpochSecond(1563883027),
        """
        |Zapraszamy na pyszną świeżą sielawę🐟🐟🐟
        """.trimMargin()
      ),
    )
  }
})
