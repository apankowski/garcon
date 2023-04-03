package dev.pankowski.garcon.infrastructure.persistence

import dev.pankowski.garcon.domain.*
import io.kotest.assertions.assertSoftly
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FreeSpec
import io.kotest.datatest.WithDataTestName
import io.kotest.datatest.withData
import io.kotest.matchers.collections.beEmpty
import io.kotest.matchers.date.between
import io.kotest.matchers.nulls.beNull
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import org.flywaydb.core.Flyway
import org.jooq.DSLContext
import org.springframework.boot.test.autoconfigure.jooq.JooqTest
import org.springframework.test.context.ActiveProfiles
import java.time.Duration
import java.time.Instant
import java.time.temporal.ChronoUnit.DAYS
import java.time.temporal.ChronoUnit.HOURS
import java.util.UUID.randomUUID

@JooqTest
@ActiveProfiles("no-scheduled-tasks")
class JooqSynchronizedPostRepositoryTest(context: DSLContext, flyway: Flyway) : FreeSpec({

  beforeEach {
    flyway.clean()
    flyway.migrate()
  }

  val repository = JooqSynchronizedPostRepository(context)

  fun SynchronizedPostRepository.storeAndRetrieve(data: StoreData) = findExisting(store(data))

  "persists synchronized post" - {

    data class PersistTestCase(
      val pageName: PageName,
      val classification: Classification,
      val repost: Repost
    ) : WithDataTestName {
      override fun dataTestName() = "persists synchronized post with $classification classification " +
        "and ${repost::class.simpleName} repost"
    }

    withData(
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
        pageId shouldBe storeData.pageId
        post shouldBe storeData.post
        classification shouldBe storeData.classification
        repost shouldBe storeData.repost
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
    ) { repost ->
      // given
      val stored = someStoredSynchronizedPost()
      val updateData = UpdateData(stored.id, stored.version, repost)

      // when
      val before = now()
      repository.updateExisting(updateData)
      val after = now()

      // and
      val updated = repository.findExisting(stored.id)

      // then
      assertSoftly(updated) {
        id shouldBe stored.id
        version shouldBe stored.version.next()
        createdAt shouldBe stored.createdAt
        updatedAt shouldBe between(before, after)
        pageId shouldBe stored.pageId
        post shouldBe stored.post
        classification shouldBe stored.classification
        this.repost shouldBe updateData.repost
      }
    }

    listOf(
      SynchronizedPostId("1"),
      SynchronizedPostId("a"),
      SynchronizedPostId(randomUUID().toString())
    ).forEach { nonexistentId ->
      "returns 'not found' when trying to update nonexistent $nonexistentId" {
        // given
        val stored = someStoredSynchronizedPost()
        val updateData = UpdateData(nonexistentId, stored.version, someSuccessRepost())

        // expect
        shouldThrow<SynchronizedPostNotFound> {
          repository.updateExisting(updateData)
        }
      }
    }

    listOf(
      Version(0),
      Version(2)
    ).forEach { wrongVersion ->
      "returns 'concurrent modification' when trying to update wrong $wrongVersion" {
        // given
        val stored = someStoredSynchronizedPost()
        val updateData = UpdateData(stored.id, wrongVersion, someSuccessRepost())

        // expect
        shouldThrow<SynchronizedPostModifiedConcurrently> {
          repository.updateExisting(updateData)
        }
      }
    }
  }

  "finds existing synchronized post" - {
    listOf(
      SynchronizedPostId("1"),
      SynchronizedPostId("a"),
      SynchronizedPostId(randomUUID().toString())
    ).forEach { nonexistentId ->
      "returns 'not found' when trying to find nonexistent $nonexistentId" {
        // given
        someStoredSynchronizedPost()

        // expect
        shouldThrow<SynchronizedPostNotFound> {
          repository.findExisting(nonexistentId)
        }
      }
    }
  }

  "finds last seen synchronized post of a given page" {
    // given
    val somePointInTime = Instant.parse("2000-01-01T00:00:00Z")

    val somePageId = PageId("1")
    repository.store(
      someStoreData(
        pageId = somePageId,
        post = somePost(publishedAt = somePointInTime.plus(1, HOURS))
      )
    )

    val somePageExpectedLastSeen = repository.storeAndRetrieve(
      someStoreData(
        pageId = somePageId,
        post = somePost(publishedAt = somePointInTime.plus(3, HOURS))
      )
    )

    repository.store(
      someStoreData(
        pageId = somePageId,
        post = somePost(publishedAt = somePointInTime.plus(2, HOURS))
      )
    )

    val otherPageId = PageId("2")
    val otherPageExpectedLastSeen = repository.storeAndRetrieve(
      someStoreData(
        pageId = otherPageId,
        post = somePost(publishedAt = somePointInTime.plus(2, HOURS))
      )
    )

    repository.store(
      someStoreData(
        pageId = otherPageId,
        post = somePost(publishedAt = somePointInTime.plus(1, HOURS))
      )
    )

    // expect
    repository.findLastSeen(somePageId) shouldBe somePageExpectedLastSeen
    repository.findLastSeen(otherPageId) shouldBe otherPageExpectedLastSeen
  }

  "finds no last seen synchronized post when there are no posts" {
    // expect
    repository.findLastSeen(PageId("some page id")) should beNull()
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
    listOf(
      Repost.Skip,
      Repost.Pending,
      someSuccessRepost()
    ).forEach { repost ->
      "doesn't consider post with ${repost::class.simpleName} repost as retryable" {
        // given
        repository.store(someStoreData(post = somePost(publishedAt = now()), repost = repost))

        // when
        val retryable = mutableListOf<SynchronizedPost>()
        repository.streamRetryable(Duration.ofMinutes(1), 10, retryable::add)

        // then
        retryable should beEmpty()
      }
    }

    "streams failed reposts for which required delay has elapsed" {
      // given
      val now = now()
      val dayAgo = now.minus(1, DAYS)
      val baseDelay = Duration.ofMinutes(1)

      val qualifyingPosts = mutableListOf<SynchronizedPost>()
      for (i in 1..5) {
        val requiredWaitTime = baseDelay.multipliedBy((1 shl (i - 1)).toLong()) // = baseDelay * 2 ^ (attempt - 1)

        qualifyingPosts += repository.storeAndRetrieve(
          someStoreData(
            post = somePost(publishedAt = dayAgo.plus(i.toLong(), HOURS), content = "post $i"),
            repost = Repost.Failed(i, now - requiredWaitTime)
          )
        )

        // Not enough time has passed for below post to qualify as retryable
        repository.storeAndRetrieve(
          someStoreData(
            post = somePost(publishedAt = dayAgo.plus(i.toLong(), HOURS), content = "post $i, not qualifying"),
            repost = Repost.Failed(i, now - requiredWaitTime + Duration.ofMinutes(1)),
          )
        )
      }

      // when
      val retryable = mutableListOf<SynchronizedPost>()
      repository.streamRetryable(baseDelay, 10, retryable::add)

      // then
      retryable shouldBe qualifyingPosts

      // when
      retryable.clear()
      repository.streamRetryable(baseDelay, 4, retryable::add)

      // then
      retryable shouldBe qualifyingPosts.take(3)
    }
  }
})
