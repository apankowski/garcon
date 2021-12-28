package dev.pankowski.garcon.domain

import io.kotest.core.spec.style.FreeSpec
import io.kotest.core.spec.style.scopes.ContainerScope
import io.kotest.datatest.WithDataTestName
import io.kotest.datatest.withData
import io.kotest.matchers.shouldBe
import java.util.*
import java.util.Locale.ENGLISH as EnglishLocale

class WordExtractionTest : FreeSpec({

  data class TestCase(val text: String, val words: List<String>) : WithDataTestName {
    override fun dataTestName() = "words extracted from '$text' are $words"
  }

  suspend fun ContainerScope.verifyExtractions(locale: Locale, vararg testCases: TestCase) =
    withData(testCases.toList()) { (text, words) ->
      // expect
      text.extractWords(locale) shouldBe words
    }

  "extracts words" - {
    verifyExtractions(
      EnglishLocale,
      TestCase("This is some text", listOf("This", "is", "some", "text")),
      TestCase(" ", emptyList()),
      TestCase("\n", emptyList()),
      TestCase(" Abc ", listOf("Abc")),
    )
  }

  "ignores punctuation" - {
    verifyExtractions(
      EnglishLocale,
      TestCase("Hello!", listOf("Hello")),
      TestCase("Hello, sir!", listOf("Hello", "sir")),
      TestCase("...what, now?", listOf("what", "now")),
    )
  }

  "handles language-specific characters" - {
    verifyExtractions(
      PolishLocale,
      TestCase(" Cześć! ! ", listOf("Cześć")),
      TestCase("Zażółć +gęślą? Jaźń %% ", listOf("Zażółć", "gęślą", "Jaźń")),
    )
  }
})
