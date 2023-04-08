package dev.pankowski.garcon.domain

import dev.pankowski.garcon.infrastructure.persistence.InMemorySynchronizedPostRepository
import io.kotest.core.spec.style.FreeSpec
import io.mockk.*

class PageSynchronizerTest : FreeSpec({

  /** No-op function making sure all sequence elements will be iterated over. */
  fun <T> Sequence<T>.drain() = toList()

  "fetches new posts" {
    // given
    val pageConfig = somePageConfig()

    val repository = mockk<SynchronizedPostRepository>()
    val pageClient = mockk<PageClient>()
    val postClassifier = mockk<LunchPostClassifier>()
    val synchronizer = PageSynchronizer(repository, pageClient, postClassifier)

    every { repository.findLastSeen(any()) } returns someSynchronizedPost()
    every { pageClient.load(any()) } returns somePage(posts = emptySequence())

    // when
    synchronizer.synchronize(pageConfig).drain()

    // then
    verify {
      repository.findLastSeen(pageConfig.id)
      pageClient.load(pageConfig)
      postClassifier wasNot Called
    }
  }

  "saves fetched lunch posts" {
    // given
    val pageConfig = somePageConfig()
    val post = somePost()
    val page = somePage(posts = sequenceOf(post))
    val classification = Classification.LUNCH_POST

    val repository = spyk(InMemorySynchronizedPostRepository())
    val pageClient = mockk<PageClient>()
    val postClassifier = mockk<LunchPostClassifier>()
    val synchronizer = PageSynchronizer(repository, pageClient, postClassifier)

    every { pageClient.load(pageConfig) } returns page
    every { postClassifier.classify(post) } returns classification

    // when
    synchronizer.synchronize(pageConfig).drain()

    // then
    verify { repository.store(StoreData(pageConfig.id, page.name, post, classification, Repost.Pending)) }
  }

  "saves fetched regular posts" {
    // given
    val pageConfig = somePageConfig()
    val post = somePost()
    val page = somePage(posts = sequenceOf(post))
    val classification = Classification.REGULAR_POST

    val repository = spyk(InMemorySynchronizedPostRepository())
    val pageClient = mockk<PageClient>()
    val postClassifier = mockk<LunchPostClassifier>()
    val synchronizer = PageSynchronizer(repository, pageClient, postClassifier)

    every { pageClient.load(pageConfig) } returns page
    every { postClassifier.classify(post) } returns classification

    // when
    synchronizer.synchronize(pageConfig).drain()

    // then
    verify { repository.store(StoreData(pageConfig.id, page.name, post, classification, Repost.Skip)) }
  }
})
