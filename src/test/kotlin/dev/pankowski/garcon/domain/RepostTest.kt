package dev.pankowski.garcon.domain

import dev.pankowski.garcon.WithTestName
import dev.pankowski.garcon.forAll
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe

class RepostTest : FreeSpec({

  "skipped repost defines its string representation" {
    // expect
    Repost.Skip.toString() shouldBe "Skip"
  }

  "pending repost defines its string representation" {
    // expect
    Repost.Pending.toString() shouldBe "Pending"
  }

  "repost has correct status" - {

    data class RepostStatusTestCase(val repost: Repost, val status: RepostStatus) : WithTestName {
      override fun testName() = "${repost::class.simpleName} repost has $status status"
    }

    forAll(
      RepostStatusTestCase(Repost.Skip, RepostStatus.SKIP),
      RepostStatusTestCase(Repost.Pending, RepostStatus.PENDING),
      RepostStatusTestCase(Repost.Failed(1, now()), RepostStatus.FAILED),
      RepostStatusTestCase(Repost.Success(now()), RepostStatus.SUCCESS),
    ) { (repost, status) ->
      // expect
      repost.status shouldBe status
    }
  }
})
