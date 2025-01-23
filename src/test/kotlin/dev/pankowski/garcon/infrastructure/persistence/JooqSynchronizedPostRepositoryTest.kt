package dev.pankowski.garcon.infrastructure.persistence

import dev.pankowski.garcon.domain.Classification
import dev.pankowski.garcon.domain.ExternalPostId
import dev.pankowski.garcon.domain.FacebookPostId
import dev.pankowski.garcon.domain.PageName
import dev.pankowski.garcon.domain.Repost
import dev.pankowski.garcon.domain.SynchronizedPost
import dev.pankowski.garcon.domain.SynchronizedPostHasDuplicateExternalId
import dev.pankowski.garcon.domain.SynchronizedPostId
import dev.pankowski.garcon.domain.SynchronizedPostModifiedConcurrently
import dev.pankowski.garcon.domain.SynchronizedPostNotFound
import dev.pankowski.garcon.domain.SynchronizedPostRepository
import dev.pankowski.garcon.domain.SynchronizedPostStoreData
import dev.pankowski.garcon.domain.Version
import dev.pankowski.garcon.domain.now
import dev.pankowski.garcon.domain.somePost
import dev.pankowski.garcon.domain.toURL
import io.kotest.assertions.assertSoftly
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.datatest.withData
import io.kotest.matchers.collections.beEmpty
import io.kotest.matchers.date.between
import io.kotest.matchers.nulls.beNull
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import java.time.Duration
import java.time.temporal.ChronoUnit.DAYS
import java.time.temporal.ChronoUnit.HOURS
import java.util.UUID.randomUUID

class JooqSynchronizedPostRepositoryTest(repository: JooqSynchronizedPostRepository) : PersistenceSpec({

  fun SynchronizedPostRepository.storeAndRetrieve(data: SynchronizedPostStoreData) =
    findExisting(store(data))

  "persists synchronized post" - {

    data class PersistTestCase(
      val pageName: PageName,
      val classification: Classification,
      val repost: Repost,
    )

    withData<PersistTestCase>(
      { "persists synchronized post with ${it.classification} classification and ${it.repost::class.simpleName} repost" },
      PersistTestCase(PageName("some page name"), Classification.REGULAR_POST, Repost.Skip),
      PersistTestCase(PageName("some page name"), Classification.LUNCH_POST, Repost.Pending),
      PersistTestCase(PageName("other page name"), Classification.LUNCH_POST, someFailedRepost()),
      PersistTestCase(PageName("other page name"), Classification.LUNCH_POST, someSuccessRepost()),
    ) { (pageName, classification, repost) ->

      // given
      val storeData = someStoreData(
        pageName = pageName,
        classification = classification,
        repost = repost
      )

      // when
      val before = now()
      val retrieved = repository.storeAndRetrieve(storeData)
      val after = now()

      // then
      assertSoftly(retrieved) {
        this.pageName shouldBe storeData.pageName
        version shouldBe Version.first()
        createdAt shouldBe between(before, after)
        updatedAt shouldBe createdAt
        pageKey shouldBe storeData.pageKey
        post shouldBe storeData.post
        classification shouldBe storeData.classification
        repost shouldBe storeData.repost
      }
    }

    "fails with 'duplicate key' when trying to store synchronized post with duplicate external ID" {
      // given
      val storeData = someStoreData()
      repository.storeAndRetrieve(storeData)

      // expect
      shouldThrow<SynchronizedPostHasDuplicateExternalId> {
        repository.store(storeData)
      }
    }
  }

  fun someStoredSynchronizedPost() = repository.storeAndRetrieve(someStoreData())

  "updates synchronized post" - {

    withData<Repost>(
      { "updates synchronized post with $it repost" },
      Repost.Skip,
      Repost.Pending,
      someFailedRepost(),
      someSuccessRepost()
    ) { newRepost ->

      // given
      val stored = someStoredSynchronizedPost()

      // when
      val before = now()
      repository.updateExisting(stored.id, stored.version, newRepost)
      val after = now()
      val updated = repository.findExisting(stored.id)

      // then
      assertSoftly(updated) {
        id shouldBe stored.id
        version shouldBe stored.version.next()
        createdAt shouldBe stored.createdAt
        updatedAt shouldBe between(before, after)
        pageKey shouldBe stored.pageKey
        post shouldBe stored.post
        classification shouldBe stored.classification
        repost shouldBe newRepost
      }
    }

    "updates synchronized post with post and classification" {
      // given
      val oldPost = somePost(url = toURL("https://old/url"), content = "old content")
      val oldClassification = Classification.REGULAR_POST
      val stored = repository.storeAndRetrieve(someStoreData(post = oldPost, classification = oldClassification))

      // and
      val newPost = somePost(url = toURL("https://new/url"), content = "new content")
      val newClassification = Classification.LUNCH_POST

      // when
      val before = now()
      repository.updateExisting(stored.id, stored.version, newPost, newClassification)
      val after = now()
      val updated = repository.findExisting(stored.id)

      // then
      assertSoftly(updated) {
        id shouldBe stored.id
        version shouldBe stored.version.next()
        createdAt shouldBe stored.createdAt
        updatedAt shouldBe between(before, after)
        pageKey shouldBe stored.pageKey
        post shouldBe newPost
        classification shouldBe newClassification
        repost shouldBe stored.repost
      }
    }

    withData<SynchronizedPostId>(
      { "fails with 'not found' when trying to update nonexistent $it" },
      SynchronizedPostId("1"),
      SynchronizedPostId("a"),
      SynchronizedPostId(randomUUID().toString()),
    ) { nonexistentId ->

      // given
      val stored = someStoredSynchronizedPost()

      // expect
      shouldThrow<SynchronizedPostNotFound> {
        repository.updateExisting(nonexistentId, stored.version, someSuccessRepost())
      }

      // and expect
      shouldThrow<SynchronizedPostNotFound> {
        repository.updateExisting(nonexistentId, stored.version, somePost(), Classification.REGULAR_POST)
      }
    }

    withData<Version>(
      { "fails with 'concurrent modification' when trying to update wrong $it" },
      Version(0),
      Version(2),
    ) { wrongVersion ->

      // given
      val stored = someStoredSynchronizedPost()

      // expect
      shouldThrow<SynchronizedPostModifiedConcurrently> {
        repository.updateExisting(stored.id, wrongVersion, someSuccessRepost())
      }

      // and expect
      shouldThrow<SynchronizedPostModifiedConcurrently> {
        repository.updateExisting(stored.id, wrongVersion, somePost(), Classification.REGULAR_POST)
      }
    }
  }

  "finds existing synchronized post" - {

    "finds previously stored synchronized post" {
      // tested in the persist case above
    }

    withData<SynchronizedPostId>(
      { "fails with 'not found' when trying to find nonexistent $it" },
      SynchronizedPostId("1"),
      SynchronizedPostId("a"),
      SynchronizedPostId(randomUUID().toString())
    ) { nonexistentId ->

      // given
      someStoredSynchronizedPost()

      // expect
      shouldThrow<SynchronizedPostNotFound> {
        repository.findExisting(nonexistentId)
      }
    }
  }

  "finds synchronized post by external ID" - {

    "finds existing synchronized post" {
      // given
      val stored = someStoredSynchronizedPost()

      // expect
      repository.findBy(stored.post.externalId) shouldBe stored
    }

    withData<ExternalPostId>(
      { "returns null for nonexistent $it" },
      FacebookPostId("??"),
      FacebookPostId("xyz"),
    ) { nonexistentExternalId ->

      // given
      someStoredSynchronizedPost()

      // expect
      repository.findBy(nonexistentExternalId) should beNull()
    }
  }

  "gets last seen synchronized posts" {
    // given
    val now = now()
    val posts = (1..100).map { i ->
      repository.storeAndRetrieve(
        someStoreData(post = somePost(publishedAt = now.minus(i.toLong(), HOURS)))
      )
    }

    // when
    val actualLog = repository.getLastSeen(20)

    // then
    actualLog shouldBe posts.take(20)
  }

  "streams retryable posts" - {

    withData<Repost>(
      { "doesn't consider post with ${it::class.simpleName} repost as retryable" },
      Repost.Skip,
      Repost.Pending,
      someSuccessRepost()
    ) { repost ->

        // given
        repository.store(someStoreData(post = somePost(publishedAt = now()), repost = repost))

        // when
        val retryable = mutableListOf<SynchronizedPost>()
        repository.streamRetryable(retryable::add)

        // then
        retryable should beEmpty()
      }

    "considers failed repost with next attempt in the past as retryable" {
      // given
      val now = now()

      fun monotonePublishedAt(i: Int) = now.minus(1, DAYS).plus(i.toLong(), HOURS)

      val qualifyingPosts = mutableListOf<SynchronizedPost>()
      for (i in 1..5) {
        qualifyingPosts += repository.storeAndRetrieve(
          someStoreData(
            // Published at establishes order of results
            post = somePost(publishedAt = monotonePublishedAt(i), content = "post $i"),
            repost = Repost.Failed(i, now, now)
          )
        )

        // Shouldn't qualify as next attempt date-time is in the future
        repository.storeAndRetrieve(
          someStoreData(
            post = somePost(publishedAt = monotonePublishedAt(i), content = "post $i, not qualifying"),
            repost = Repost.Failed(i, now, now + Duration.ofMinutes(1)),
          )
        )
      }

      // when
      val retryable = mutableListOf<SynchronizedPost>()
      repository.streamRetryable(retryable::add)

      // then
      retryable shouldBe qualifyingPosts
    }
  }
})
