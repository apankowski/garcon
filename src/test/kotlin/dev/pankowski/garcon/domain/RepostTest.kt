package dev.pankowski.garcon.domain

import io.kotest.core.spec.style.FreeSpec
import io.kotest.datatest.WithDataTestName
import io.kotest.datatest.withData
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

    data class RepostStatusTestCase(val repost: Repost, val status: RepostStatus) : WithDataTestName {
      override fun dataTestName() = "${repost::class.simpleName} repost has $status status"
    }

    withData(
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
