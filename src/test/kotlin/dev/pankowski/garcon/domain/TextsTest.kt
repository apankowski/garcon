package dev.pankowski.garcon.domain

import io.kotest.core.spec.style.FreeSpec
import io.kotest.core.spec.style.scopes.ContainerScope
import io.kotest.datatest.WithDataTestName
import io.kotest.datatest.withData
import io.kotest.matchers.shouldBe
import java.util.*

class TextsTest : FreeSpec({

  "string distance" - {

    data class TestCase(val a: String, val b: String, val distance: Int) : WithDataTestName {
      override fun dataTestName() = "distance between '$a' and '$b' is $distance"
    }

    "Levenshtein distance between two strings" - {
      withData(
        TestCase("", "", 0),
        TestCase("abc", "abc", 0),
        TestCase("abc", "ab", 1),
        TestCase("abc", "abd", 1),
        TestCase("bc", "abc", 1),
        TestCase("ac", "abc", 1),
        TestCase("abc", "b", 2),
        TestCase("abc", "d", 3),
        TestCase("abc", "cd", 3),
        TestCase("abc", "ad", 2),
      ) { (a, b, distance) ->
        // expect
        levenshtein(a, b) shouldBe distance
        levenshtein(b, a) shouldBe distance
      }
    }

    "Damerau-Levenshtein distance between two strings" - {
      withData(
        TestCase("", "", 0),
        TestCase("", "abc", 3),
        TestCase("b", "abc", 2),
        TestCase("d", "abc", 3),
        TestCase("abc", "abc", 0),
        TestCase("ab", "abc", 1),
        TestCase("abc", "ab", 1),
        TestCase("abc", "abd", 1),
        TestCase("ac", "abc", 1),
        TestCase("abc", "ac", 1),
        TestCase("abc", "adc", 1),
        TestCase("abc", "acb", 1),
        TestCase("abc", "bac", 1),
        TestCase("abc", "cab", 2),
        TestCase("abcdef", "bacdfe", 2),
        TestCase("abcdef", "poiu", 6),
      ) { (a, b, distance) ->
        // expect
        damerauLevenshtein(a, b) shouldBe distance
        damerauLevenshtein(b, a) shouldBe distance
      }
    }
  }

  "word extraction" - {

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
        Locale.ENGLISH,
        TestCase("This is some text", listOf("This", "is", "some", "text")),
        TestCase(" ", emptyList()),
        TestCase("\n", emptyList()),
        TestCase(" Abc ", listOf("Abc")),
      )
    }

    "ignores punctuation" - {
      verifyExtractions(
        Locale.ENGLISH,
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
  }

  "ellipsize" - {

    data class TestCase(val text: String, val ellipsizeAt: Int, val result: String) : WithDataTestName {
      override fun dataTestName() = "ellipsizes '$text' at $ellipsizeAt to '$result'"
    }

    withData(
      TestCase("1", 1, "1"),
      TestCase("1234", 2, "12…"),
      TestCase("1 \t\n ą3", 6, "1 \t\n ą…"),
    ) { (text, at, result) ->

      text.ellipsize(at) shouldBe result
    }
  }

  "one-line preview" - {

    data class TestCase(val text: String, val maxLength: Int, val result: String) : WithDataTestName {
      override fun dataTestName() = "one-line preview of '$text' cut at $maxLength is '$result'"
    }

    withData(
      TestCase("1 \n2\n 3  4  5", 6, "1 2 3 …"),
      TestCase("1 2 3 4", 4, "1 2 …"),
      TestCase("a \t b \t c \n def", 7, "a \t b \t…"),
    ) { (text, maxLength, result) ->

      text.oneLinePreview(maxLength) shouldBe result
    }
  }
})
