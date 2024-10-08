package dev.pankowski.garcon.infrastructure.facebook

import dev.pankowski.garcon.domain.FacebookPostId
import dev.pankowski.garcon.domain.Post
import dev.pankowski.garcon.domain.toURL
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.collections.beEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.should
import org.jsoup.Jsoup
import java.time.Instant

class PostExtractionStrategyV2Test : FreeSpec({

  val strategy = PostExtractionStrategyV2()

  fun htmlFrom(file: String) =
    when (val url = javaClass.getResource(file)) {
      null -> throw IllegalArgumentException("$file classpath resource doesn't exist")
      else -> url.readText()
    }

  fun document(html: String) = Jsoup.parse(html, "https://www.facebook.com/")

  "gracefully handles edge cases" {
    val html =
      """
      <html>
      <script>
      </script>
      <script>
      [
        {
          "__typename": "Story"
        },
        {
          "__typename": "Story",
          "post_id": "some-post-id"
        },
        {
          "__typename": "Story",
          "post_id": "some-post-id",
          "content": {
          }
        },
        {
          "__typename": "Story",
          "post_id": "some-post-id",
          "content": {
            "creation_time": 0
          }
        },
        {
          "__typename": "Story",
          "post_id": "some-post-id",
          "content": {
            "creation_time": 0,
            "url": "https://facebook.com/some-post-permalink",
          }
        },
        {
          "__typename": "Story",
          "post_id": "some-post-id",
          "content": {
            "creation_time": 0,
            "url": "https://facebook.com/some-post-permalink",
            "__typename": "TextWithEntities",
          }
        },
        {
          "__typename": false,
          "post_id": "some-post-id",
          "content": {
            "creation_time": 0,
            "url": "https://facebook.com/some-post-permalink",
            "__typename": "TextWithEntities",
            "text": "some content"
          }
        },
        {
          "__typename": "Story",
          "post_id": false,
          "content": {
            "creation_time": 0,
            "url": "https://facebook.com/some-post-permalink",
            "__typename": "TextWithEntities",
            "text": "some content"
          }
        },
        {
          "__typename": "Story",
          "post_id": "some-post-id",
          "content": {
            "creation_time": false,
            "url": "https://facebook.com/some-post-permalink",
            "__typename": "TextWithEntities",
            "text": "some content"
          }
        },
        {
          "__typename": "Story",
          "post_id": "some-post-id",
          "content": {
            "creation_time": 0,
            "url": false,
            "__typename": "TextWithEntities",
            "text": "some content"
          }
        },
        {
          "__typename": "Story",
          "post_id": "some-post-id",
          "content": {
            "creation_time": 0,
            "url": "invalid URL",
            "__typename": "TextWithEntities",
            "text": "some content"
          }
        },
        {
          "__typename": "Story",
          "post_id": "some-post-id",
          "content": {
            "creation_time": 0,
            "url": "https://facebook.com/some-post-permalink",
            "__typename": false,
            "text": "some content"
          }
        },
        {
          "__typename": "Story",
          "post_id": "some-post-id",
          "content": {
            "creation_time": 0,
            "url": "https://facebook.com/some-post-permalink",
            "__typename": "TextWithEntities",
            "text": false
          }
        },
      ]
      </script>
      </html>
      """.trimIndent()

    // given
    val document = document(html)

    // when
    val result = strategy.extractPosts(document)

    // then
    result should beEmpty()
  }

  "extracts posts from a page with simplistic payload" {
    val html =
      """
      <html>
      <script>
      </script>
      <script>
      [
        {
          "__typename": "Story",
          "post_id": "some-post-id-1",
          "content": {
            "creation_time": 1,
            "url": "https://facebook.com/some-post1-permalink",
            "__typename": "TextWithEntities",
            "text": "some content #1"
          }
        },
        {
          "__typename": "Story",
          "post_id": "some-post-id-2",
          "content": {
            "creation_time": 2,
            "url": "https://facebook.com/some-post2-permalink",
            "__typename": "TextWithEntities",
            "text": "some content #2"
          }
        },
      ]
      </script>
      <div>some stuff</div>
      <script>
      [
        {
          "__typename": "Story",
          "post_id": "some-post-id-3",
          "content": {
            "creation_time": 3,
            "url": "https://facebook.com/some-post3-permalink",
            "__typename": "TextWithEntities",
            "text": "some content #3"
          }
        },
      ]
      </script>
      </html>
      """.trimIndent()

    val posts = listOf(
      Post(
        externalId = FacebookPostId("some-post-id-1"),
        url = toURL("https://facebook.com/some-post1-permalink"),
        publishedAt = Instant.ofEpochSecond(1),
        content = "some content #1"
      ),
      Post(
        externalId = FacebookPostId("some-post-id-2"),
        url = toURL("https://facebook.com/some-post2-permalink"),
        publishedAt = Instant.ofEpochSecond(2),
        content = "some content #2"
      ),
      Post(
        externalId = FacebookPostId("some-post-id-3"),
        url = toURL("https://facebook.com/some-post3-permalink"),
        publishedAt = Instant.ofEpochSecond(3),
        content = "some content #3"
      ),
    )

    // given
    val document = document(html)

    // when
    val result = strategy.extractPosts(document)

    // then
    result shouldContainExactly posts
  }

  "extracts post from real page A (text-only story)" {
    // given
    val document = document(htmlFrom("/lunch/facebook/v2/real-page-a-text-story.html"))

    // when
    val result = strategy.extractPosts(document)

    // then
    result shouldContainExactly listOf(
      Post(
        FacebookPostId("1054930068793510"),
        toURL("https://www.facebook.com/permalink.php?story_fbid=pfbid0L45BV7SFeSH8Gc3SQvCLHC2VUwqPc7bYtJB7kh1EN12T2uBYXYFx7BLDqoGLjDNjl&id=100028295814975"),
        Instant.ofEpochSecond(1673897171),
        """
        |MENU   17  STYCZNIA
        |
        | ZUPA DNIA
        |ŻUREK   10 ZŁ
        |MINESTRONE   10 ZŁ
        |    (ZUPA DNIA DO DAŃ GŁÓWNYCH, MAKARONÓW I SAŁATEK ZA 6 ZŁ)
        |
        | LUNCH
        |WĄTRÓBKA DROBIOWA Z ZIEMNIAKAMI PUREE
        | I SURÓWKĄ Z OGÓRKA KISZONEGO   26 ZŁ
        |BITKA WIEPRZOWA Z ZIEMNIAKAMI PUREE
        | I SURÓWKĄ Z KAPUSTY PEKIŃSKIEJ 26 ZŁ
        |KOTLET Z KALAFIORA Z MIXEM SAŁAT I SOSEM JOGURTOWYM   26 ZŁ
        |
        |ZUPY
        |KREM Z DYNI 20 ZŁ
        |
        |PRZEKĄSKI
        |SWOJSKA MARYNOWANA SŁONINA Z CHRZANEM,
        |OGÓRKIEM KISZONYM I CHLEBEM ŻYTNIM  19 ZŁ
        |ŚLEDŹ MARYNOWANY W OLEJU Z MARYNOWANĄ CZERWONĄ CEBULĄ,
        |OGÓRKIEM KISZONYM  I PIECZYWEM  24 ZŁ
        |TATAR WOŁOWY  38 ZŁ
        |KANAPKA Z ROSTBEFEM WOŁOWYM, SAŁATĄ RZYMSKĄ, CZERWONĄ CEBULĄ I SOSEM MIODOWO-MUSZTARDOWYM  32 ZŁ
        |BRUSCHETTA TRZY SERY: SER KOZI I ŻURAWINA, PARMEZAN I RUKOLA, SER PLEŚNIOWY I WIŚNIAMI 24 ZŁ
        |BRUSCHETTA Z POMIDORAMI I PESTO  19 ZŁ
        |
        |
        |SAŁATKI
        |SAŁATKA CEZAR Z GRIILOWANYM KURCZAKIEM  34 ZŁ
        |GRILLOWANE WARZYWA : MARCHEWKA, PAPRYKA, CUKINIA
        |Z  WOŁOWINĄ I SOSEM SOJOWYM Z SEZAMEM  38 ZŁ
        |SAŁATKA Z KOZIM SEREM  NA CARPACCIO Z PIECZONYCH BATATÓW
        |Z RUKOLĄ I DOMOWYM PESTO  36 ZŁ
        |SAŁATKA Z PIECZONYCH WARZYW (MARCHEWKA, PIETRUSZKA, CUKINIA)
        |Z HUMMUSEM, AVOCADO I ZIELONĄ SAŁATĄ Z ZIOŁAMI I OLIWĄ  34 ZŁ
        |
        |BURGERY
        |BURGER WEGETARIAŃSKI Z KOTLETEM Z SOCZEWICY. SAŁATĄ RZYMSKĄ, POMIDOREM PAPRYKĄ JALAPENO I PESTO 32 ZŁ
        |BURGER WOŁOWY KLASYCZNY Z RZYMSKĄ SAŁATĄ, POMIDOREM,
        |OGÓRKIEM KONSERWOWYM,  MARYNOWANĄ CZERWONĄ CEBULĄ
        |I SOSEM SZEFA  35 ZŁ
        |BURGER WOŁOWY Z JALAPENO, RUKOLĄ, MARYNOWANĄ CEBULĄ,
        |SEREM CHEDDAR I SOSEM MUSZTARDOWYM  36 ZŁ
        |DO BURGERA: FRYTKI – 6 ZŁ, MIX SAŁAT 6 ZŁ
        |
        |MAKARONY
        |CZARNY MAKARON Z KREWETKAMI (5 SZT) SZPINAKIEM,POMIDORKAMI CHERRY W SOSIE MAŚLANO-WINNYM  44 ZŁ
        |MAKARON Z WOŁOWINĄ  CHORIZO, Z RUKOLĄ
        |W SOSIE POMIDOROWYM Z CHILLI  38
        |MAKARON Z KURCZAKIEM, DYNIĄ, SZPINAKIEM, SZYNKĄ DOJRZEWAJĄCĄ W KREMOWYM SOSIE  37 ZŁ
        |MAKARON Z POLĘDWICZKĄ WIEPRZOWĄ  W SOSIE TRUFLOWYM  37 ZŁ
        |MAKARON W SOSIE TRUFLOWYM Z NATKĄ PIETRUSZKI  33 ZŁ
        |
        |
        |RYBY I OWOCE MORZA
        |ŁOSOŚ Z PIECA W SOSIE TERYAKI NA PIECZONYCH WARZYWACH KORZENNYCH Z CIECIORKĄ I KOLENDRĄ  48 ZŁ
        |KREWETKI Z CHORIZO (5 SZT), SZPINAKIEM, CHILLI I POMIDORKAMI  32 ZŁ
        |MULE W SOSIE MAŚLANO WINNNYM  49 ZŁ
        |MULE W SOSIE POMIDOROWYM Z CHILLI  49 ZŁ
        |KREWETKI 10 SZT W SOSIE MAŚLANO – WINNYM Z BAGIETKĄ  59 ZŁ
        |KREWETKI 10 SZT W SOSIE POMIDOROWYM Z CHILLI Z BAGIETKĄ  59 ZŁ
        |
        |DANIA GŁÓWNE
        |ROSTBEF WOŁOWY Z MASŁEM ZIOŁOWYM
        |Z FRYTKAMI I MIXEM SAŁAT  56  ZŁ
        |PIERŚ KACZKI W SOSIE TERYAKI  NA BATATACH
        |Z KARMEZLIZOWANYM PAK CHOI  48 ZŁ
        |POLĘDWICZKA WIEPRZOWA W SOSIE MIODOWO MUSZTARDOWYM
        |Z OPIEKANYMI ZIEMNIAKAMI I
        | BURACZKAMI MARYNOWANYMI  44 ZŁ
        |PIERŚ Z KURCZAKA GRILLOWANA
        |Z FRYTKAMI I MIXEM SAŁAT   37  ZŁ
        |
        |DESERY
        |NALEŚNIKI Z DŻEMEM 15 ZŁ
        """.trimMargin()
      ),
    )
  }

  "extracts post from real page A (text story with image)" {
    // given
    val document = document(htmlFrom("/lunch/facebook/v2/real-page-a-text-story-with-image.html"))

    // when
    val result = strategy.extractPosts(document)

    // then
    result shouldContainExactly listOf(
      Post(
        FacebookPostId("1053189635634220"),
        toURL("https://www.facebook.com/permalink.php?story_fbid=pfbid0NPAVi78AX53SAGKhpdixFZH9GJ7Y54JaqUxr9hR5TNXaYDW29fKcUKHMM5bLPniXl&id=100028295814975"),
        Instant.ofEpochSecond(1673688899),
        """
        |Nasza nowość sałatka z warzyw korzennych
        |z avocado i hummusem
        """.trimMargin()
      ),
    )
  }

  "extracts post from real page B (text-only story)" {
    // given
    val document = document(htmlFrom("/lunch/facebook/v2/real-page-b-text-story.html"))

    // when
    val result = strategy.extractPosts(document)

    // then
    result shouldContainExactly listOf(
      Post(
        FacebookPostId("1416281615991685"),
        toURL("https://www.facebook.com/permalink.php?story_fbid=pfbid0dcnDxDnFgyioPGkFVUBAmULwCScvaTSzro4YSANJo7NiV9GwMRJqDisVM3hc8c19l&id=100028295814975"),
        Instant.ofEpochSecond(1723573975),
        """
        |MENU 14 SIERPNIA
        |ZUPA DNIA
        |KALAFIOROWA 10 ZŁ
        |KAPUŚNIAK 10 ZŁ
        |(PRZY ZAKUPIE DANIA GŁOWNEGO, MAKARONU, SAŁATEK  ZUPA DNIA ZA 6 ZŁ)
        |
        |LUNCH
        |KOTLET MIELONY Z ZIEMNIAKAMI PUREE
        |I BURACZKAMI  26 ZŁ
        |TORTILLA Z KURCZAKIEM I WARZYWAMI
        |Z SOSEM JOGURTOWYM I MIXEM SAŁAT 26 ZŁ
        |ZIELONE NALEŚNIKI W TEMPURZE
        |Z WARZYWAMI 26 ZŁ
        |
        |ZUPA
        |CHŁODNIK 25 ZŁ
        |
        |PRZEKĄSKI
        |PANIEROWANY SER HALLOUMI Z OLIWKAMI, PIECZONĄ PAPRYKĄ NA HUMUSSIE
        |Z CHLEBKIEM PITA 36 ZŁ
        |DESKA GRECKA: HUMMUS, HITIPITI,
        |OLIWKI MARYNOWANE,DOLMADAKIA SŁODKO  PIKANTNA, ŚWIEŻE WARZYWA, FETA, PITA 48
        |TATAR WOŁOWY 43 ZŁ
        |BRUSCHETTA Z POMIDORAMI
        |I PESTO BAZYLIOWYM 22 ZŁ
        |BRUSCHETTA Z KURKAMI
        |I NATKĄ PIETRUSZKI 26 ZŁ
        |
        |SAŁATKI
        |SAŁATKA CEZAR Z GRILLOWANYM KURCZAKIEM  36 ZŁ
        |GRILLOWANE WARZYWA: MARCHEWKA, PAPRYKA, CUKINIA Z WOŁOWINĄ I SOSEM SOJOWYM Z SEZAMEM  39 ZŁ
        |SAŁATKA GRECKA Z GRZANKAMI CZOSNKOWYMI 36 ZŁ
        |SAŁATKA Z SEREM HALLOUMI NA CARPACCIO Z POMIDORÓW MALINOWYCH Z RUKOLĄ
        |I DOMOWYM SOSEM PESTO  38 ZŁ
        |
        |MAKARONY
        |CZARNY MAKARON Z KREWETKAMI (5 SZT) SZPINAKIEM, POMIDORKAMI CHERRY W SOSIE MAŚLANO-WINNYM  47 ZŁ
        |MAKARON Z WOŁOWINĄ, CHORIZO I RUKOLĄ
        |W SOSIE POMIDOROWYM Z CHILLI  45 ZŁ
        |MAKARON Z KURCZAKIEM, SZPINAKIEM  ￼
        |I SZYNKĄ DOJRZEWAJĄCĄ
        |W KREMOWYM SOSIE  45 ZŁ
        |MAKARON Z POLĘDWICZKĄ WIEPRZOWĄ
        |W SOSIE  TRUFLOWYM  43 ZŁ
        |MAKARON W SOSIE KURKOWYM
        |Z NATKĄ PIETRUSZKI  41  ZŁ
        |
        |RYBY I OWOCE MORZA
        |ŁOSOŚ PIECZONY W SOSIE CYTRYNOWYM
        |NA RISOTTO Z ZIELONYM GROSZKIEM  59 ZŁ
        |MULE W SOSIE MAŚLANO-WINNYM
        |Z BAGIETKĄ  49 ZŁ
        |MULE W SOSIE POMIDOROWYM
        |Z CHILLI Z BAGIETKĄ  49 ZŁ
        |KREWETKI 10 SZT W SOSIE MAŚLANO – WINNYM  LUB W SOSIE POMIDOROWYM
        |Z CHILLI I  BAGIETKĄ  59 ZŁ
        |
        |BURGERY
        |BURGER Z ŁOSOSIA Z MUSEM Z AVOCADO, SAŁATĄ RZYMSKĄ,ŚWIEŻYM OGÓRKIEM
        |I PIKANTNYM SOSEM SZEFA 41 ZŁ
        |BURGER WEGETARIAŃSKI Z KOTLETEM
        |Z SOCZEWICY, SAŁATĄ RZYMSKĄ,
        |POMIDOREM PAPRYKĄ JALAPENO I PESTO  36
        |BURGER WOŁOWY KLASYCZNY Z RZYMSKĄ SAŁATĄ, POMIDOREM, OGÓRKIEM KONSERWOWYM,  MARYNOWANĄ
        |CZERWONĄ CEBULĄ I SOSEM SZEFA  38 ZŁ
        |BURGER WOŁOWY Z JALAPENO, RUKOLĄ, MARYNOWANĄ CEBULĄ, SEREM CHEDDAR
        |I SOSEM MUSZTARD OWYM  38 ZŁ
        |DO BURGERA: FRYTKI – 8 ZŁ, MIX SAŁAT – 8 ZŁ
        |
        |DANIA GŁÓWNE
        |ROSTBEF WOŁOWY Z MASŁEM ZIOŁOWYM
        |Z FRYTKAMI I MIXEM SAŁAT  66 ZŁ
        |POLĘDWICZKA WIEPRZOWA W SOSIE TRUFLOWYM Z OPIEKANYMI ZIEMNIAKAMI
        |I BURCZKAMI  47 ZŁ
        |PIERŚ Z KACZKI W SOSIE TERYAKI
        |NA BATATACH Z KARMELIZOWANYM
        |PAK CHOI  48 ZŁ
        |GRILLOWANA PIERŚ Z KURCZAKA
        |W SOSIE KURKOWYM Z MŁODYMI ZIEMNIAKAMI I ZIELONĄ SAŁATKĄ 48 ZŁ
        |
        |DESER
        |SERNIK NA ZIMNO 16 ZŁ
        |CIASTO Z RABARBAREM 16 ZŁ
        """.trimMargin()
      ),
    )
  }
})
