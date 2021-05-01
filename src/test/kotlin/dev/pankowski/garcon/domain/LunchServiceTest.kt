package dev.pankowski.garcon.domain

import dev.pankowski.garcon.infrastructure.persistence.InMemorySynchronizedPostRepository
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.beInstanceOf
import io.kotest.matchers.types.beTheSameInstanceAs
import io.mockk.*

class LunchServiceTest : FreeSpec({

  "should fetch new posts" {
    // given
    val pageConfig = somePageConfig()
    val lastSeenPublishedAt = now()
    val lastSeen = somePost(publishedAt = lastSeenPublishedAt)

    val postClient = mockk<FacebookPostClient>()
    val postClassifier = mockk<LunchPostClassifier>()
    val reposter = mockk<SlackReposter>()
    val repository = spyk(InMemorySynchronizedPostRepository())
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

  "should save & repost fetched lunch posts" {
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

  "should save fetched non-lunch posts" {
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

  "should return synchronization log" {
    // given
    val log = ArrayList<SynchronizedPost>()

    val postClient = mockk<FacebookPostClient>()
    val postClassifier = mockk<LunchPostClassifier>()
    val reposter = mockk<SlackReposter>()
    val repository = spyk(InMemorySynchronizedPostRepository())
    val service = LunchService(someLunchConfig(), postClient, postClassifier, reposter, repository)

    every { repository.getLastSeen(20) } returns log

    // expect
    service.getLog() should beTheSameInstanceAs(log)
  }
})
