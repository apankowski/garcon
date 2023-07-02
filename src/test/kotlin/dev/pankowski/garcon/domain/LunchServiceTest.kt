package dev.pankowski.garcon.domain

import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.should
import io.kotest.matchers.types.beTheSameInstanceAs
import io.mockk.*

class LunchServiceTest : FreeSpec({

  "synchronizes pages from config" {
    // given
    val pageConfig = somePageConfig()
    val lunchConfig = someLunchConfig(pages = listOf(pageConfig))

    val synchronizer = mockk<PageSynchronizer> { every { synchronize(any()) } returns emptyList() }
    val service = LunchService(lunchConfig, mockk(), synchronizer, mockk())

    // when
    service.synchronizeAll()

    // then
    verify { synchronizer.synchronize(pageConfig) }
  }

  "reposts pending appearing lunch posts" {
    // given
    val pageConfig = somePageConfig()
    val synchronizedPost = someSynchronizedPost(classification = Classification.LUNCH_POST, repost = Repost.Pending)
    val delta = SynchronizedPostDelta(old = null, new = synchronizedPost).also { assert(it.lunchPostAppeared) }

    val synchronizer = mockk<PageSynchronizer> { every { synchronize(any()) } returns listOf(delta) }
    val reposter = mockk<Reposter> { every { repost(synchronizedPost) } returns Unit }

    val service = LunchService(someLunchConfig(), mockk(), synchronizer, reposter)

    // when
    service.synchronize(pageConfig)

    // then
    verify { reposter.repost(synchronizedPost) }
  }

  "doesn't repost other synchronized posts" {
    // given
    val pageConfig = somePageConfig()
    val synchronizedPost = someSynchronizedPost(classification = Classification.REGULAR_POST)
    val delta = SynchronizedPostDelta(old = null, new = synchronizedPost).also { assert(!it.lunchPostAppeared) }

    val synchronizer = mockk<PageSynchronizer> { every { synchronize(any()) } returns listOf(delta) }
    val reposter = mockk<Reposter>()

    val service = LunchService(someLunchConfig(), mockk(), synchronizer, reposter)

    // when
    service.synchronize(pageConfig)

    // then
    verify { reposter wasNot Called }
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
