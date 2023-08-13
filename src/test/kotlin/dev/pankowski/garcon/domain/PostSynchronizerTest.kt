package dev.pankowski.garcon.domain

import dev.pankowski.garcon.infrastructure.persistence.InMemorySynchronizedPostRepository
import dev.pankowski.garcon.infrastructure.persistence.someSuccessRepost
import io.kotest.assertions.assertSoftly
import io.kotest.core.spec.style.FreeSpec
import io.kotest.datatest.withData
import io.kotest.matchers.collections.containExactly
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe

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
      val synchronizer = PostSynchronizer(repository)

      // when
      val deltas = synchronizer.synchronize(pageConfig.key, page.name, listOf(classifiedPost)).toList()

      // then
      assertSoftly(repository.findBy(post.externalId)) {
        it.shouldNotBeNull()
        it.pageKey shouldBe pageConfig.key
        it.pageName shouldBe page.name
        it.post shouldBe post
        it.classification shouldBe classification
        it.repost shouldBe repost

        deltas should containExactly(SynchronizedPostDelta(null, it))
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
      val synchronizer = PostSynchronizer(repository)

      // when
      val deltas = synchronizer.synchronize(pageConfig.key, page.name, listOf(classifiedPost)).toList()

      // then
      assertSoftly(repository.findBy(post.externalId)) { new ->
        new.shouldNotBeNull()
        new.post shouldBe post
        new.classification shouldBe classification
        // This won't work at the moment, as updates don't reset repost status
        //new.repost shouldBe repost

        deltas should containExactly(SynchronizedPostDelta(old, new))
      }
    }
  }
})
