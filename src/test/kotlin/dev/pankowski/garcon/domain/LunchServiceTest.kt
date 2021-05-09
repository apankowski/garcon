package dev.pankowski.garcon.domain

import dev.pankowski.garcon.WithTestName
import dev.pankowski.garcon.forAll
import dev.pankowski.garcon.infrastructure.persistence.InMemorySynchronizedPostRepository
import dev.pankowski.garcon.infrastructure.persistence.someErrorRepost
import dev.pankowski.garcon.infrastructure.persistence.someSuccessRepost
import io.kotest.assertions.assertSoftly
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.date.between
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.beInstanceOf
import io.kotest.matchers.types.beTheSameInstanceAs
import io.mockk.*
import java.time.Duration

class LunchServiceTest : FreeSpec({

  "synchronizes all pages from config" {
    // given
    val pageConfig = somePageConfig()

    val postClient = mockk<FacebookPostClient>()
    val repository = mockk<SynchronizedPostRepository>()
    val service = spyk(
      LunchService(someLunchConfig(pages = listOf(pageConfig)), postClient, mockk(), mockk(), repository)
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
    val service = LunchService(someLunchConfig(), postClient, postClassifier, reposter, repository)

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
    val service = LunchService(someLunchConfig(), postClient, postClassifier, reposter, repository)

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
    val service = LunchService(someLunchConfig(), postClient, postClassifier, reposter, repository)

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
    val service = LunchService(someLunchConfig(), mockk(), mockk(), mockk(), repository)

    every { repository.getLastSeen(20) } returns log

    // expect
    service.getLog() should beTheSameInstanceAs(log)
  }

  "retries failed reposts" - {

    val baseDelay = Duration.ofMinutes(1)
    val maxAttempts = 10

    data class NoRetryTestCase(val repost: Repost) : WithTestName {
      override fun testName() = "doesn't retry ${repost::class.simpleName} repost"
    }

    forAll(
      NoRetryTestCase(Repost.Skip),
      NoRetryTestCase(someSuccessRepost()),
    ) { (r) ->
      // given
      val post = someSynchronizedPost(repost = r)

      val reposter = mockk<SlackReposter>()
      val repository = mockk<SynchronizedPostRepository>()
      val service = LunchService(someLunchConfig(), mockk(), mockk(), reposter, repository)

      excludeRecords { repository.streamRetryable(any(), any(), any()) }
      every { repository.streamRetryable(baseDelay, maxAttempts, captureLambda()) } answers
        { lambda<(SynchronizedPost) -> Unit>().invoke(post) }

      // when
      service.retryFailed()

      // then
      verify {
        repository wasNot Called
        reposter wasNot Called
      }
    }

    data class RetryTestCase(val repost: Repost) : WithTestName {
      override fun testName() = "retries ${repost::class.simpleName} repost"
    }

    forAll(
      RetryTestCase(Repost.Pending),
      RetryTestCase(someErrorRepost()),
    ) { (r) ->
      // given
      val post = someSynchronizedPost(repost = r)

      val reposter = mockk<SlackReposter>()
      val repository = mockk<SynchronizedPostRepository>()
      val service = LunchService(someLunchConfig(), mockk(), mockk(), reposter, repository)

      every { repository.streamRetryable(baseDelay, maxAttempts, captureLambda()) } answers
        { lambda<(SynchronizedPost) -> Unit>().invoke(post) }
      every { reposter.repost(post.post, any()) } returns Unit
      every { repository.updateExisting(any()) } returns Unit

      // when
      val before = now()
      service.retryFailed()
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

    data class FailedRetryTestCase(val repost: Repost, val newAttempts: Int) : WithTestName {
      override fun testName() = "increments number of attempts after failed retry of $repost repost"
    }

    forAll(
      FailedRetryTestCase(Repost.Pending, 1),
      FailedRetryTestCase(someErrorRepost(attempts = 2), 3),
    ) { (r, newAttempts) ->
      // given
      val post = someSynchronizedPost(repost = r)

      val reposter = mockk<SlackReposter>()
      val repository = mockk<SynchronizedPostRepository>()
      val service = LunchService(someLunchConfig(), mockk(), mockk(), reposter, repository)

      every { repository.streamRetryable(baseDelay, maxAttempts, captureLambda()) } answers
        { lambda<(SynchronizedPost) -> Unit>().invoke(post) }
      every { reposter.repost(post.post, any()) } throws RuntimeException("something went wrong")
      every { repository.updateExisting(any()) } returns Unit

      // when
      val before = now()
      service.retryFailed()
      val after = now()

      // then
      val updateDataSlot = slot<UpdateData>()
      verify { repository.updateExisting(capture(updateDataSlot)) }

      assertSoftly(updateDataSlot.captured) {
        id shouldBe post.id
        version shouldBe post.version
        repost should beInstanceOf<Repost.Error>()
        assertSoftly(repost as Repost.Error) {
          attempts shouldBe newAttempts
          lastAttemptAt shouldBe between(before, after)
        }
      }
    }
  }
})
