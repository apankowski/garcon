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

      // expect
      classifier.classify(post) shouldBe classification

      // and expect
      classifier.classified(post) shouldBe ClassifiedPost(post, classification)
    }

  "classifies post without lunch keyword as 'missing keywords'" - {
    verifyClassifications(
      somePostConfig(
        locale = PolishLocale,
        keywords = listOf(Keyword("lunch", 1)),
      ),
      TestCase("Some text", Classification.REGULAR_POST),
      TestCase("Zapraszamy na pyszną świeżą sielawę", Classification.REGULAR_POST),
    )
  }

  "classifies post with lunch keyword as 'lunch post'" - {
    verifyClassifications(
      somePostConfig(
        locale = PolishLocale,
        keywords = listOf(Keyword("lunch", 1), Keyword("lunchowa", 2)),
      ),
      TestCase("Lunch wtorek", Classification.LUNCH_POST),
      TestCase("jemy lunch", Classification.LUNCH_POST),
      TestCase("dzisiejsza oferta lunchowa", Classification.LUNCH_POST),
      TestCase("lunch!!!", Classification.LUNCH_POST),
      TestCase("**Lunch**", Classification.LUNCH_POST),
      TestCase("😆😆😆lunch😆😆😆", Classification.LUNCH_POST),
    )
  }

  "classifies post with misspelled lunch keyword as 'lunch post'" - {
    verifyClassifications(
      somePostConfig(
        locale = PolishLocale,
        keywords = listOf(Keyword("lunch", 1), Keyword("lunchowa", 2)),
      ),
      TestCase("luunch", Classification.LUNCH_POST),
      TestCase("Lnuch", Classification.LUNCH_POST),
      TestCase("dzisiejsza oferta lunhcowa", Classification.LUNCH_POST),
    )
  }
})
