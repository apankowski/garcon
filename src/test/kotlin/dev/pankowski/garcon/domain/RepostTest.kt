package dev.pankowski.garcon.domain

import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe

class RepostTest : FreeSpec({

  "skipped repost should define its string representation" {
    // expect
    Repost.Skip.toString() shouldBe "Skip"
  }

  "skipped repost should have skipped status" {
    // expect
    Repost.Skip.status shouldBe RepostStatus.SKIP
  }

  "pending repost should define its string representation" {
    // expect
    Repost.Pending.toString() shouldBe "Pending"
  }

  "pending repost should have pending status" {
    // expect
    Repost.Pending.status shouldBe RepostStatus.PENDING
  }

  "failed repost should have error status" {
    // given
    val failed = Repost.Failed(1, now())

    // expect
    failed.status shouldBe RepostStatus.FAILED
  }

  "successful repost should have success status" {
    // given
    val success = Repost.Success(now())

    // expect
    success.status shouldBe RepostStatus.SUCCESS
  }
})
