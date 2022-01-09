package dev.pankowski.garcon.domain

import io.kotest.core.spec.style.FreeSpec
import io.kotest.core.spec.style.scopes.ContainerScope
import io.kotest.datatest.WithDataTestName
import io.kotest.datatest.withData
import io.kotest.matchers.shouldBe

class LunchPostClassifierTest : FreeSpec({

  data class TestCase(val content: String, val classification: Classification) : WithDataTestName {
    override fun dataTestName() = "classifies '$content' as $classification"
  }

  suspend fun ContainerScope.verifyClassifications(postConfig: PostConfig, vararg testCases: TestCase) =
    withData(testCases.toList()) { (content, classification) ->
      // given
      val post = somePost(content = content)
      val classifier = LunchPostClassifier(postConfig)

      // when
      val result = classifier.classify(post)

      // then
      result shouldBe classification
    }

  "classifies post without lunch keyword as 'missing keywords'" - {
    verifyClassifications(
      somePostConfig(
        locale = PolishLocale,
        keywords = listOf(Keyword("lunch", 1)),
      ),
      TestCase("Some text", Classification.MissingKeywords),
      TestCase("Zapraszamy na pyszną świeżą sielawę", Classification.MissingKeywords),
    )
  }

  "classifies post with lunch keyword as 'lunch post'" - {
    verifyClassifications(
      somePostConfig(
        locale = PolishLocale,
        keywords = listOf(Keyword("lunch", 1), Keyword("lunchowa", 2)),
      ),
      TestCase("Lunch wtorek", Classification.LunchPost),
      TestCase("jemy lunch", Classification.LunchPost),
      TestCase("dzisiejsza oferta lunchowa", Classification.LunchPost),
      TestCase("lunch!!!", Classification.LunchPost),
      TestCase("**Lunch**", Classification.LunchPost),
      TestCase("😆😆😆lunch😆😆😆", Classification.LunchPost),
    )
  }

  "classifies post with misspelled lunch keyword as 'lunch post'" - {
    verifyClassifications(
      somePostConfig(
        locale = PolishLocale,
        keywords = listOf(Keyword("lunch", 1), Keyword("lunchowa", 2)),
      ),
      TestCase("luunch", Classification.LunchPost),
      TestCase("Lnuch", Classification.LunchPost),
      TestCase("dzisiejsza oferta lunhcowa", Classification.LunchPost),
    )
  }
})
