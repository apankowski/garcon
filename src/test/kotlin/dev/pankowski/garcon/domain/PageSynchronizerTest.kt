package dev.pankowski.garcon.domain

import io.kotest.core.spec.style.FreeSpec
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
    val postSynchronizer = mockk<PostSynchronizer> { every { synchronize(any(), any(), any()) } returns Unit }

    val pageSynchronizer = PageSynchronizer(client, classifier, postSynchronizer)

    // when
    pageSynchronizer.synchronize(pageConfig)

    // then
    verify {
      client.load(pageConfig)
      classifier.classified(post)
      postSynchronizer.synchronize(pageConfig.key, page.name, listOf(classifiedPost))
    }
  }
})
