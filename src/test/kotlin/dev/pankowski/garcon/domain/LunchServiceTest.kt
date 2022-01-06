package dev.pankowski.garcon.domain

import dev.pankowski.garcon.infrastructure.persistence.InMemorySynchronizedPostRepository
import dev.pankowski.garcon.infrastructure.persistence.someFailedRepost
import dev.pankowski.garcon.infrastructure.persistence.someSuccessRepost
import io.kotest.assertions.assertSoftly
import io.kotest.core.spec.style.FreeSpec
import io.kotest.datatest.WithDataTestName
import io.kotest.datatest.withData
import io.kotest.matchers.date.between
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.beInstanceOf
import io.kotest.matchers.types.beTheSameInstanceAs
import io.mockk.*

class LunchServiceTest : FreeSpec({

  "synchronizes all pages from config" {
    // given
    val pageConfig = somePageConfig()

    val postClient = mockk<FacebookPostClient>()
    val repository = mockk<SynchronizedPostRepository>()
    val service = spyk(
      LunchService(someLunchConfig(pages = listOf(pageConfig)), mockk(), repository, postClient, mockk(), mockk())
    )

    every { repository.findLastSeen(any()) } returns null
    every { postClient.fetch(any(), any()) } returns Pair(null, emptyList())

    // when
    service.synchronizeAll()

    // then
    verify {
      service.synchronize(pageConfig)
    }
  }

  "fetches new posts" {
    // given
    val pageConfig = somePageConfig()
    val lastSeenPublishedAt = now()
    val lastSeen = somePost(publishedAt = lastSeenPublishedAt)

    val postClient = mockk<FacebookPostClient>()
    val postClassifier = mockk<LunchPostClassifier>()
    val reposter = mockk<SlackReposter>()
    val repository = mockk<SynchronizedPostRepository>()
    val service = LunchService(someLunchConfig(), mockk(), repository, postClient, postClassifier, reposter)

    every { repository.findLastSeen(any()) } returns
      SynchronizedPost(
        SynchronizedPostId("some id"),
        Version(1),
        now(),
        now(),
        PageId("some id"),
        null,
        lastSeen,
        Classification.LunchPost,
        Repost.Skip
      )

    every { postClient.fetch(any(), any()) } returns Pair(somePageName(), emptyList())

    // when
    service.synchronize(pageConfig)

    // then
    verify {
      repository.findLastSeen(pageConfig.id)
      postClient.fetch(pageConfig, lastSeenPublishedAt)
      postClassifier wasNot Called
      reposter wasNot Called
    }
  }

  "saves & reposts fetched lunch posts" {
    // given
    val pageConfig = somePageConfig()
    val pageName = somePageName()
    val post = somePost()
    val classification = Classification.LunchPost

    val postClient = mockk<FacebookPostClient>()
    val postClassifier = mockk<LunchPostClassifier>()
    val reposter = mockk<SlackReposter>()
    val repository = spyk(InMemorySynchronizedPostRepository())
    val service = LunchService(someLunchConfig(), mockk(), repository, postClient, postClassifier, reposter)

    every { postClient.fetch(pageConfig, any()) } returns Pair(pageName, listOf(post))
    every { postClassifier.classify(post) } returns classification
    every { reposter.repost(post, pageName) } returns Unit

    // when
    service.synchronize(pageConfig)

    // then
    val updateDataSlot = slot<UpdateData>()
    verify {
      repository.store(StoreData(pageConfig.id, pageName, post, classification, Repost.Pending))
      repository.updateExisting(capture(updateDataSlot))
    }

    updateDataSlot.captured.version shouldBe Version.first()
    updateDataSlot.captured.repost should beInstanceOf(Repost.Success::class)
  }

  "saves fetched non-lunch posts" {
    // given
    val pageConfig = somePageConfig()
    val pageName = somePageName()
    val post = somePost()
    val classification = Classification.MissingKeywords

    val postClient = mockk<FacebookPostClient>()
    val postClassifier = mockk<LunchPostClassifier>()
    val reposter = mockk<SlackReposter>()
    val repository = spyk(InMemorySynchronizedPostRepository())
    val service = LunchService(someLunchConfig(), mockk(), repository, postClient, postClassifier, reposter)

    every { postClient.fetch(pageConfig, any()) } returns Pair(somePageName(), listOf(post))
    every { postClassifier.classify(post) } returns classification

    // when
    service.synchronize(pageConfig)

    // then
    verify {
      repository.store(StoreData(pageConfig.id, pageName, post, classification, Repost.Skip))
      reposter wasNot Called
    }
  }

  "returns synchronization log" {
    // given
    val log = ArrayList<SynchronizedPost>()

    val repository = mockk<SynchronizedPostRepository>()
    val service = LunchService(someLunchConfig(), mockk(), repository, mockk(), mockk(), mockk())

    every { repository.getLastSeen(20) } returns log

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
      val retryConfig = someRetryConfig()

      val reposter = mockk<SlackReposter>()
      val repository = mockk<SynchronizedPostRepository>()
      val service = LunchService(someLunchConfig(), retryConfig, repository, mockk(), mockk(), reposter)

      excludeRecords { repository.streamRetryable(any(), any(), any()) }
      every { repository.streamRetryable(retryConfig.baseDelay, retryConfig.maxAttempts, captureLambda()) } answers
        { lambda<(SynchronizedPost) -> Unit>().invoke(post) }

      // when
      service.retryFailedReposts()

      // then
      verify {
        repository wasNot Called
        reposter wasNot Called
      }
    }

    withData<Repost>(
      { "retries ${it::class.simpleName} repost" },
      Repost.Pending,
      someFailedRepost(),
    ) { r ->
      // given
      val post = someSynchronizedPost(repost = r)
      val retryConfig = someRetryConfig()

      val reposter = mockk<SlackReposter>()
      val repository = mockk<SynchronizedPostRepository>()
      val service = LunchService(someLunchConfig(), retryConfig, repository, mockk(), mockk(), reposter)

      every { repository.streamRetryable(retryConfig.baseDelay, retryConfig.maxAttempts, captureLambda()) } answers
        { lambda<(SynchronizedPost) -> Unit>().invoke(post) }
      every { reposter.repost(post.post, any()) } returns Unit
      every { repository.updateExisting(any()) } returns Unit

      // when
      val before = now()
      service.retryFailedReposts()
      val after = now()

      // then
      val updateDataSlot = slot<UpdateData>()
      verify { repository.updateExisting(capture(updateDataSlot)) }

      assertSoftly(updateDataSlot.captured) {
        id shouldBe post.id
        version shouldBe post.version
        repost should beInstanceOf<Repost.Success>()
        assertSoftly(repost as Repost.Success) {
          repostedAt shouldBe between(before, after)
        }
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
      val retryConfig = someRetryConfig()

      val reposter = mockk<SlackReposter>()
      val repository = mockk<SynchronizedPostRepository>()
      val service = LunchService(someLunchConfig(), retryConfig, repository, mockk(), mockk(), reposter)

      every { repository.streamRetryable(retryConfig.baseDelay, retryConfig.maxAttempts, captureLambda()) } answers
        { lambda<(SynchronizedPost) -> Unit>().invoke(post) }
      every { reposter.repost(post.post, any()) } throws RuntimeException("something went wrong")
      every { repository.updateExisting(any()) } returns Unit

      // when
      val before = now()
      service.retryFailedReposts()
      val after = now()

      // then
      val updateDataSlot = slot<UpdateData>()
      verify { repository.updateExisting(capture(updateDataSlot)) }

      assertSoftly(updateDataSlot.captured) {
        id shouldBe post.id
        version shouldBe post.version
        repost should beInstanceOf<Repost.Failed>()
        assertSoftly(repost as Repost.Failed) {
          attempts shouldBe newAttempts
          lastAttemptAt shouldBe between(before, after)
        }
      }
    }
  }
})
