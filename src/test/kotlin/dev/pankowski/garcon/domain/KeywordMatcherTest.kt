package dev.pankowski.garcon.domain

import io.kotest.core.spec.style.FreeSpec
import io.kotest.datatest.WithDataTestName
import io.kotest.datatest.withData
import io.kotest.matchers.shouldBe
import java.util.*

class KeywordMatcherTest : FreeSpec({

  data class TestCase(val text: String, val keyword: Keyword, val match: Boolean) : WithDataTestName {
    override fun dataTestName() =
      if (match) "'$text' matches $keyword"
      else "'$text' doesn't match $keyword"
  }

  "text matches a keyword" - {
    withData(
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

  data class AnyTestCase(val text: String, val keywords: List<Keyword>, val match: Boolean) : WithDataTestName {

    override fun dataTestName() =
      if (match) "'$text' matches any of $keywords"
      else "'$text' doesn't match any of $keywords"
  }

  "text matches any keyword" - {
    withData(
      AnyTestCase("some text", emptyList(), false),
      AnyTestCase("some text", listOf(Keyword("so", 2)), true),
      AnyTestCase("some text", listOf(Keyword("so", 1), Keyword("tex", 1)), true),
      AnyTestCase("some text", listOf(Keyword("so", 1), Keyword("te", 1)), false),
    ) { (text, keywords, match) ->

      // given
      val matcher = KeywordMatcher.onWordsOf(text, Locale.ENGLISH)

      // expect
      matcher.matchesAny(keywords) shouldBe match
    }
  }
})
