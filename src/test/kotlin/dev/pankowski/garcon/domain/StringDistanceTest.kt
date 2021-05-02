package dev.pankowski.garcon.domain

import dev.pankowski.garcon.WithTestName
import dev.pankowski.garcon.forAll
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe

class StringDistanceTest : FreeSpec({

  data class TestCase(val a: String, val b: String, val distance: Int) : WithTestName {
    override fun testName() = "distance between '$a' and '$b' is $distance"
  }

  "Levenshtein distance between two strings" - {
    forAll(
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
    forAll(
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
})
