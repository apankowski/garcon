package dev.pankowski.garcon.domain

import io.kotest.core.spec.style.FreeSpec
import io.kotest.core.spec.style.scopes.FreeScope
import io.kotest.matchers.shouldBe
import java.util.*

class WordExtractionTest : FreeSpec({

  data class TestCase(val text: String, val words: List<String>)

  suspend fun FreeScope.verifyExtractions(locale: Locale, vararg testCases: TestCase) =
    testCases.forEach { (text, words) ->
      "words extracted from '$text' are $words" {
        // expect
        text.extractWords(locale) shouldBe words
      }
    }

  "should extract words" - {
    verifyExtractions(
      Locale.ENGLISH,
      TestCase("This is some text", listOf("This", "is", "some", "text")),
      TestCase(" ", emptyList()),
      TestCase("\n", emptyList()),
      TestCase(" Abc ", listOf("Abc")),
    )
  }

  "should ignore punctuation" - {
    verifyExtractions(
      Locale.ENGLISH,
      TestCase("Hello!", listOf("Hello")),
      TestCase("Hello, sir!", listOf("Hello", "sir")),
      TestCase("...what, now?", listOf("what", "now")),
    )
  }

  "should handle language-specific characters" - {
    verifyExtractions(
      Locale("pl", "PL"),
      TestCase(" Cześć! ! ", listOf("Cześć")),
      TestCase("Zażółć +gęślą? Jaźń %% ", listOf("Zażółć", "gęślą", "Jaźń")),
    )
  }
})
