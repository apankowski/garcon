package dev.pankowski.garcon.domain

import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.should
import io.kotest.matchers.types.beTheSameInstanceAs
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify

class LunchServiceTest : FreeSpec({

  "synchronizes pages from config" {
    // given
    val pageConfig = somePageConfig()
    val lunchConfig = someLunchConfig(pages = listOf(pageConfig))

    val synchronizer = mockk<PageSynchronizer> { every { synchronize(any()) } returns Unit }
    val service = LunchService(lunchConfig, mockk(), synchronizer, mockk())

    // when
    service.synchronizeAll()

    // then
    verify { synchronizer.synchronize(pageConfig) }
  }

  "returns synchronization log" {
    // given
    val log = ArrayList<SynchronizedPost>()

    val repository = mockk<SynchronizedPostRepository> { every { getLastSeen(20) } returns log }
    val service = LunchService(someLunchConfig(), repository, mockk(), mockk())

    // expect
    service.getLog() should beTheSameInstanceAs(log)
  }

  "retries failed reposts" {
    // given
    val post = someSynchronizedPost()

    val repository = mockk<SynchronizedPostRepository> {
      val lambdaSlot = slot<(SynchronizedPost) -> Unit>()
      every { streamRetryable(capture(lambdaSlot)) } answers { lambdaSlot.captured(post) }
    }

    val reposter = mockk<Reposter> { every { repost(post) } returns Unit }
    val service = LunchService(someLunchConfig(), repository, mockk(), reposter)

    // when
    service.retryFailedReposts()

    // then
    verify { reposter.repost(post) }
  }
})
