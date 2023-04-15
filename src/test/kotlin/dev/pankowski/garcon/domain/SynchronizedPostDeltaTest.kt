package dev.pankowski.garcon.domain

import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe

class SynchronizedPostDeltaTest : FreeSpec({

  fun someRegularPost() = someSynchronizedPost(classification = Classification.REGULAR_POST)
  fun someLunchPost() = someSynchronizedPost(classification = Classification.LUNCH_POST)

  "answers if lunch post has appeared" {
    // expect
    SynchronizedPostDelta(null, someRegularPost()).lunchPostAppeared shouldBe false
    SynchronizedPostDelta(null, someLunchPost()).lunchPostAppeared shouldBe true
    SynchronizedPostDelta(someRegularPost(), someRegularPost()).lunchPostAppeared shouldBe false
    SynchronizedPostDelta(someRegularPost(), someLunchPost()).lunchPostAppeared shouldBe true
    SynchronizedPostDelta(someLunchPost(), someRegularPost()).lunchPostAppeared shouldBe false
    SynchronizedPostDelta(someLunchPost(), someLunchPost()).lunchPostAppeared shouldBe false
  }

  "answers if lunch post has disappeared" {
    // expect
    SynchronizedPostDelta(null, someRegularPost()).lunchPostDisappeared shouldBe false
    SynchronizedPostDelta(null, someLunchPost()).lunchPostDisappeared shouldBe false
    SynchronizedPostDelta(someRegularPost(), someRegularPost()).lunchPostDisappeared shouldBe false
    SynchronizedPostDelta(someRegularPost(), someLunchPost()).lunchPostDisappeared shouldBe false
    SynchronizedPostDelta(someLunchPost(), someRegularPost()).lunchPostDisappeared shouldBe true
    SynchronizedPostDelta(someLunchPost(), someLunchPost()).lunchPostDisappeared shouldBe false
  }

  "answers if lunch post has changed" {
    // expect
    SynchronizedPostDelta(null, someRegularPost()).lunchPostChanged shouldBe false
    SynchronizedPostDelta(null, someLunchPost()).lunchPostChanged shouldBe false
    SynchronizedPostDelta(someRegularPost(), someRegularPost()).lunchPostChanged shouldBe false
    SynchronizedPostDelta(someRegularPost(), someLunchPost()).lunchPostChanged shouldBe false
    SynchronizedPostDelta(someLunchPost(), someRegularPost()).lunchPostChanged shouldBe false
    SynchronizedPostDelta(someLunchPost(), someLunchPost()).lunchPostChanged shouldBe true
  }
})
