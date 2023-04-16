package dev.pankowski.garcon.domain

import dev.pankowski.garcon.infrastructure.persistence.InMemorySynchronizedPostRepository
import dev.pankowski.garcon.infrastructure.persistence.someFailedRepost
import dev.pankowski.garcon.infrastructure.persistence.someSuccessRepost
import io.kotest.core.spec.style.FreeSpec
import io.kotest.datatest.WithDataTestName
import io.kotest.datatest.withData
import io.kotest.matchers.date.between
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.beTheSameInstanceAs
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.*

class LunchServiceTest : FreeSpec({

  "synchronizes pages from config" {
    // given
    val pageConfig = somePageConfig()
    val lunchConfig = someLunchConfig(pages = listOf(pageConfig))

    val synchronizer = mockk<PageSynchronizer> { every { synchronize(any()) } returns emptySequence() }
    val service = LunchService(lunchConfig, mockk(), mockk(), synchronizer, mockk())

    // when
    service.synchronizeAll()

    // then
    verify {
      synchronizer.synchronize(pageConfig)
    }
  }

  "reposts pending appearing lunch posts" {
    // given
    val pageConfig = somePageConfig()
    val synchronizedPost = someSynchronizedPost(classification = Classification.LUNCH_POST, repost = Repost.Pending)
    val delta = SynchronizedPostDelta(old = null, new = synchronizedPost).also { assert(it.lunchPostAppeared) }

    val repository = spyk(InMemorySynchronizedPostRepository()).apply { put(synchronizedPost) }
    val synchronizer = mockk<PageSynchronizer> { every { synchronize(any()) } returns sequenceOf(delta) }
    val slack = mockk<Slack> { every { repost(synchronizedPost.post, synchronizedPost.pageName) } returns Unit }

    val service = LunchService(someLunchConfig(), mockk(), repository, synchronizer, slack)

    // when
    val before = now()
    service.synchronize(pageConfig)
    val after = now()

    // then
    val repostSlot = slot<Repost>()
    verify { repository.updateExisting(any(), any(), capture(repostSlot)) }

    with(repostSlot.captured) {
      shouldBeInstanceOf<Repost.Success>()
      repostedAt shouldBe between(before, after)
    }
  }

  "doesn't repost other synchronized posts" {
    // given
    val pageConfig = somePageConfig()
    val synchronizedPost = someSynchronizedPost(classification = Classification.REGULAR_POST)
    val delta = SynchronizedPostDelta(old = null, new = synchronizedPost).also { assert(!it.lunchPostAppeared) }

    val repository = spyk(InMemorySynchronizedPostRepository())
    val synchronizer = mockk<PageSynchronizer> { every { synchronize(any()) } returns sequenceOf(delta) }
    val slack = mockk<Slack> { every { repost(synchronizedPost.post, synchronizedPost.pageName) } returns Unit }

    val service = LunchService(someLunchConfig(), mockk(), repository, synchronizer, slack)

    // when
    service.synchronize(pageConfig)

    // then
    verify {
      repository wasNot Called
      slack wasNot Called
    }
  }

  "returns synchronization log" {
    // given
    val log = ArrayList<SynchronizedPost>()

    val repository = mockk<SynchronizedPostRepository> { every { getLastSeen(20) } returns log }
    val service = LunchService(someLunchConfig(), mockk(), repository, mockk(), mockk())

    // expect
    service.getLog() should beTheSameInstanceAs(log)
  }

  "retries failed reposts" - {

    withData<Repost>(
      { "doesn't retry ${it::class.simpleName} repost" },
      Repost.Skip,
      someSuccessRepost(),
    ) { r ->

      // given
      val post = someSynchronizedPost(repost = r)
      val retryConfig = someRepostRetryConfig()

      val repository = mockk<SynchronizedPostRepository> {
        val lambdaSlot = slot<(SynchronizedPost) -> Unit>()
        every { streamRetryable(capture(lambdaSlot)) } answers { lambdaSlot.captured(post) }
        excludeRecords { streamRetryable(any()) }
      }

      val slack = mockk<Slack>()
      val service = LunchService(someLunchConfig(), retryConfig, repository, mockk(), slack)

      // when
      service.retryFailedReposts()

      // then
      verify {
        repository wasNot Called
        slack wasNot Called
      }
    }

    withData<Repost>(
      { "retries ${it::class.simpleName} repost" },
      Repost.Pending,
      someFailedRepost(),
    ) { r ->

      // given
      val post = someSynchronizedPost(repost = r)
      val retryConfig = someRepostRetryConfig()

      val repository = mockk<SynchronizedPostRepository> {
        val lambdaSlot = slot<(SynchronizedPost) -> Unit>()
        every { streamRetryable(capture(lambdaSlot)) } answers { lambdaSlot.captured(post) }
        every { updateExisting(post.id, post.version, any()) } returns Unit
      }

      val slack = mockk<Slack> { every { repost(post.post, any()) } returns Unit }
      val service = LunchService(someLunchConfig(), retryConfig, repository, mockk(), slack)

      // when
      val before = now()
      service.retryFailedReposts()
      val after = now()

      // then
      val repostSlot = slot<Repost>()
      verify { repository.updateExisting(any(), any(), capture(repostSlot)) }

      with(repostSlot.captured) {
        shouldBeInstanceOf<Repost.Success>()
        repostedAt shouldBe between(before, after)
      }
    }

    data class FailedRetryTestCase(val repost: Repost, val newAttempts: Int) : WithDataTestName {
      override fun dataTestName() =
        "increments number of attempts after failed retry of ${repost::class.simpleName} repost"
    }

    withData(
      FailedRetryTestCase(Repost.Pending, 1),
      FailedRetryTestCase(someFailedRepost(attempts = 2), 3),
    ) { (r, newAttempts) ->

      // given
      val post = someSynchronizedPost(repost = r)
      val retryConfig = someRepostRetryConfig()

      val repository = mockk<SynchronizedPostRepository> {
        val lambdaSlot = slot<(SynchronizedPost) -> Unit>()
        every { streamRetryable(capture(lambdaSlot)) } answers { lambdaSlot.captured(post) }
        every { updateExisting(post.id, post.version, any()) } returns Unit
      }

      val slack = mockk<Slack> {
        every { repost(post.post, any()) } throws RuntimeException("something went wrong")
      }

      val service = LunchService(someLunchConfig(), retryConfig, repository, mockk(), slack)

      // when
      val before = now()
      service.retryFailedReposts()
      val after = now()

      // then
      val repostSlot = slot<Repost>()
      verify { repository.updateExisting(any(), any(), capture(repostSlot)) }

      with(repostSlot.captured) {
        shouldBeInstanceOf<Repost.Failed>()
        attempts shouldBe newAttempts
        lastAttemptAt shouldBe between(before, after)
      }
    }
  }
})
