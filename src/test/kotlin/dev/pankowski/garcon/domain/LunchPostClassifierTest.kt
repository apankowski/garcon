package dev.pankowski.garcon.domain

import io.kotest.core.datatest.forAll
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe

class LunchPostClassifierTest : FreeSpec({

  val classifier = LunchPostClassifier(somePostConfig())

  "should reject post having content without lunch keyword" - {
    forAll(
      "Some text",
      "Zapraszamy na pyszną świeżą sielawę",
    ) { postContent ->
      // given
      val post = somePost(content = postContent)

      // when
      val result = classifier.classify(post)

      // then
      result shouldBe Classification.MissingKeywords
    }
  }

  "should accept post having content with lunch keyword" - {
    forAll(
      "Lunch wtorek",
      "jemy lunch",
      "dzisiejsza oferta lunchowa",
      "lunch!!!",
      "**Lunch**",
      "😆😆😆lunch😆😆😆",
    ) { postContent ->
      // given
      val post = somePost(content = postContent)

      // when
      val result = classifier.classify(post)

      // then
      result shouldBe Classification.LunchPost
    }
  }

  "should accept post having content with misspelled lunch keyword" - {
    forAll(
      "luunch",
      "Lnuch",
      "dzisiejsza oferta lunhcowa",
    ) { postContent ->
      // given
      val post = somePost(content = postContent)

      // when
      val result = classifier.classify(post)

      // then
      result shouldBe Classification.LunchPost
    }
  }
})
