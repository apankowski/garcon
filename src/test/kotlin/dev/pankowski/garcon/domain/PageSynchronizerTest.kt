package dev.pankowski.garcon.domain

import io.kotest.core.spec.style.FreeSpec
import io.mockk.Called
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify

class PageSynchronizerTest : FreeSpec({

  "fetches, classifies and synchronizes posts" {
    // given
    val pageConfig = somePageConfig()

    val post = somePost()
    val page = somePage(posts = listOf(post))
    val classifiedPost = ClassifiedPost(post, Classification.LUNCH_POST)

    val client = mockk<PageClient> { every { load(any()) } returns page }
    val classifier = mockk<LunchPostClassifier> { every { classified(any()) } returns classifiedPost }
    val postSynchronizer = mockk<PostSynchronizer> { every { synchronize(any(), any(), any()) } returns emptyList() }
    val reposter = mockk<Reposter>()

    val pageSynchronizer = PageSynchronizer(client, classifier, postSynchronizer, reposter)

    // when
    pageSynchronizer.synchronize(pageConfig)

    // then
    verify {
      client.load(pageConfig)
      classifier.classified(post)
      postSynchronizer.synchronize(pageConfig.key, page.name, listOf(classifiedPost))
    }
  }

  "reposts appearing lunch posts" {
    // given
    val pageConfig = somePageConfig()

    val synchronizedPost = someSynchronizedPost(classification = Classification.LUNCH_POST, repost = Repost.Pending)
    val delta = SynchronizedPostDelta(old = null, new = synchronizedPost).also { assert(it.lunchPostAppeared) }

    val client = mockk<PageClient> { every { load(any()) } returns somePage() }
    val postSynchronizer = mockk<PostSynchronizer> { every { synchronize(any(), any(), any()) } returns listOf(delta) }
    val reposter = mockk<Reposter> { every { repost(any()) } returns Unit }

    val pageSynchronizer = PageSynchronizer(client, mockk(), postSynchronizer, reposter)

    // when
    pageSynchronizer.synchronize(pageConfig)

    // then
    verify { reposter.repost(synchronizedPost) }
  }

  "doesn't repost other synchronized posts" {
    // given
    val pageConfig = somePageConfig()
    val synchronizedPost = someSynchronizedPost(classification = Classification.REGULAR_POST)
    val delta = SynchronizedPostDelta(old = null, new = synchronizedPost).also { assert(!it.lunchPostAppeared) }

    val client = mockk<PageClient> { every { load(any()) } returns somePage() }
    val postSynchronizer = mockk<PostSynchronizer> { every { synchronize(any(), any(), any()) } returns listOf(delta) }
    val reposter = mockk<Reposter>()

    val pageSynchronizer = PageSynchronizer(client, mockk(), postSynchronizer, reposter)

    // when
    pageSynchronizer.synchronize(pageConfig)

    // then
    verify { reposter wasNot Called }
  }
})
