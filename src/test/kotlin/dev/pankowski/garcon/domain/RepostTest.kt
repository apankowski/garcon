package dev.pankowski.garcon.domain

import io.kotest.assertions.assertSoftly
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FreeSpec
import io.kotest.datatest.WithDataTestName
import io.kotest.datatest.withData
import io.kotest.matchers.date.between
import io.kotest.matchers.shouldBe
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.seconds
import kotlin.time.toJavaDuration

class RepostTest : FreeSpec({

  "repost has correct status" - {

    data class RepostStatusTestCase(val repost: Repost, val status: RepostStatus) : WithDataTestName {
      override fun dataTestName() = "${repost::class.simpleName} repost has $status status"
    }

    withData(
      RepostStatusTestCase(Repost.Skip, RepostStatus.SKIP),
      RepostStatusTestCase(Repost.Pending, RepostStatus.PENDING),
      RepostStatusTestCase(Repost.Failed(1, now(), now()), RepostStatus.FAILED),
      RepostStatusTestCase(Repost.Success(now()), RepostStatus.SUCCESS),
    ) { (repost, status) ->
      // expect
      repost.status shouldBe status
    }
  }

  "exponential backoff calculation" - {

    "fails for invalid attempt number" - {

      withData<Int>(
        { "fails for $it attempt" },
        -10, -1, 0
      ) { attempt ->

        // given
        val someMaxAttempts = 1
        val someBaseDelay = 1.seconds

        // expect
        shouldThrow<IllegalArgumentException> {
          Repost.failedWithExponentialBackoff(attempt, someMaxAttempts, someBaseDelay.toJavaDuration())
        }
      }
    }

    "succeeds for valid attempt number" - {

      data class SuccessTestCase(
        val attempt: Int,
        val maxAttempts: Int,
        val baseDelay: Duration,
        val delay: Duration?,
      ) : WithDataTestName {
        override fun dataTestName() =
          if (delay != null) "$attempt/$maxAttempts attempt with base delay $baseDelay results in delay $delay"
          else "$attempt/$maxAttempts with base delay $baseDelay results in no next attempt"
      }

      withData(
        SuccessTestCase(1, 5, 1.hours, 1.hours),
        SuccessTestCase(2, 5, 1.hours, 2.hours),
        SuccessTestCase(4, 5, 1.hours, 8.hours),
        SuccessTestCase(5, 5, 1.hours, null),
      ) { (attempt, maxAttempts, baseDelay, delay) ->

        // when
        val before = now()
        val repost = Repost.failedWithExponentialBackoff(attempt, maxAttempts, baseDelay.toJavaDuration())
        val after = now()

        // then
        assertSoftly(repost) {
          status shouldBe RepostStatus.FAILED
          attempts shouldBe attempt
          lastAttemptAt shouldBe between(before, after)
          if (delay == null) nextAttemptAt shouldBe null
          else nextAttemptAt shouldBe between(before + delay.toJavaDuration(), after + delay.toJavaDuration())
        }
      }
    }
  }
})
