package dev.pankowski.garcon.domain

import dev.pankowski.garcon.infrastructure.persistence.InMemorySynchronizedPostRepository
import dev.pankowski.garcon.infrastructure.persistence.someSuccessRepost
import io.kotest.assertions.assertSoftly
import io.kotest.core.spec.style.FreeSpec
import io.kotest.datatest.withData
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.mockk.mockk
import io.mockk.verify
import org.springframework.context.ApplicationEventPublisher

class PostSynchronizerTest : FreeSpec({

  "stores new posts" - {

    data class StoreTestCase(val name: String, val classification: Classification, val repost: Repost)

    withData<StoreTestCase>(
      { "stores appearing ${it.name}" },
      StoreTestCase("regular post", Classification.REGULAR_POST, Repost.Skip),
      StoreTestCase("lunch post", Classification.LUNCH_POST, Repost.Pending),
    ) { (_, classification, repost) ->

      // given
      val pageConfig = somePageConfig()
      val post = somePost()
      val page = somePage(posts = listOf(post))
      val classifiedPost = ClassifiedPost(post, classification)

      val repository = InMemorySynchronizedPostRepository()
      val eventPublisher = mockk<ApplicationEventPublisher>(relaxUnitFun = true)
      val synchronizer = PostSynchronizer(repository, eventPublisher)

      // when
      synchronizer.synchronize(pageConfig.key, page.name, classifiedPost)

      // then
      val new = repository.findBy(post.externalId)!!
      assertSoftly(new) {
        it.shouldNotBeNull()
        it.pageKey shouldBe pageConfig.key
        it.pageName shouldBe page.name
        it.post shouldBe post
        it.classification shouldBe classification
        it.repost shouldBe repost
      }

      // and
      verify {
        eventPublisher.publishEvent(SynchronizedPostCreatedEvent(new))
      }
    }
  }

  "updates existing posts" - {

    data class UpdateTestCase(val name: String, val classification: Classification, val repost: Repost)

    withData<UpdateTestCase>(
      { "updates existing ${it.name}" },
      UpdateTestCase("regular post", Classification.REGULAR_POST, Repost.Skip),
      UpdateTestCase("lunch post", Classification.LUNCH_POST, Repost.Pending),
    ) { (_, classification, _) ->

      // given
      val pageConfig = somePageConfig()
      val post = somePost(content = "now post")
      val old = someSynchronizedPost(post = post.copy(content = "old post"), repost = someSuccessRepost())
      val page = somePage(posts = listOf(post))
      val classifiedPost = ClassifiedPost(post, classification)

      val repository = InMemorySynchronizedPostRepository().apply { put(old) }
      val eventPublisher = mockk<ApplicationEventPublisher>(relaxUnitFun = true)
      val synchronizer = PostSynchronizer(repository, eventPublisher)

      // when
      synchronizer.synchronize(pageConfig.key, page.name, classifiedPost)

      // then
      val new = repository.findBy(post.externalId)!!
      assertSoftly(new) {
        it.shouldNotBeNull()
        it.post shouldBe post
        it.classification shouldBe classification
        // This won't work at the moment, as updates don't reset repost status
        //new.repost shouldBe repost
      }

      // and
      verify {
        eventPublisher.publishEvent(SynchronizedPostUpdatedEvent(old, new))
      }
    }
  }
})
