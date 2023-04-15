package dev.pankowski.garcon.domain

import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe

class PostDeltaTest : FreeSpec({

  "answers if post has appeared" {
    // expect
    PostDelta(null, somePost()).appeared shouldBe true
    PostDelta(somePost(), somePost()).appeared shouldBe false
  }

  "answers if post has changed" {
    // expect
    PostDelta(null, somePost()).changed shouldBe true
    somePost().let { PostDelta(it, it.copy()) }.changed shouldBe false
  }
})
