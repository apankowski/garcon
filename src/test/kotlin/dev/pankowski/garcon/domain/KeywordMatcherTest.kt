package dev.pankowski.garcon.domain

import io.kotest.core.datatest.forAll
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import java.util.*

class KeywordMatcherTest : FreeSpec({

  data class TestCase(val text: String, val keyword: Keyword, val match: Boolean)

  "text matches keyword with edit distance" - {
    forAll(
      TestCase("some text", Keyword("some", 0), true),
      TestCase("some text", Keyword("smoe", 1), true),
      TestCase("some text", Keyword("pxet", 2), true),
      TestCase("some text", Keyword("so", 2), true),
      TestCase("some text", Keyword("pxet", 1), false),
      TestCase("some text", Keyword("smoa", 1), false),
    ) { (text, keyword, match) ->
      // given
      val matcher = KeywordMatcher.onWordsOf(text, Locale.ENGLISH)

      // expect
      matcher.matches(keyword) shouldBe match
    }
  }
})
