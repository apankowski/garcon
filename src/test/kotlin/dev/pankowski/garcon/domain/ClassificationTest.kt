package dev.pankowski.garcon.domain

import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe

class ClassificationTest : FreeSpec({

  "'lunch post' classification has proper string representation" {
    // expect
    Classification.LunchPost.toString() shouldBe "LunchPost"
  }

  "'lunch post' classification has proper status" {
    // expect
    Classification.LunchPost.status shouldBe ClassificationStatus.LUNCH_POST
  }

  "'missing keywords' classification has proper string representation" {
    // expect
    Classification.MissingKeywords.toString() shouldBe "MissingKeywords"
  }

  "'missing keywords' classification has proper status" {
    // expect
    Classification.MissingKeywords.status shouldBe ClassificationStatus.MISSING_KEYWORDS
  }
})
