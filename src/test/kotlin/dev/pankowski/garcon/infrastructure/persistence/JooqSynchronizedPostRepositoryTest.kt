package dev.pankowski.garcon.infrastructure.persistence

import dev.pankowski.garcon.domain.*
import io.kotest.assertions.assertSoftly
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.datatest.forAll
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.date.between
import io.kotest.matchers.nulls.beNull
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import org.flywaydb.core.Flyway
import org.jooq.DSLContext
import org.springframework.boot.test.autoconfigure.jooq.JooqTest
import java.time.Instant
import java.time.temporal.ChronoUnit.HOURS
import java.util.UUID.randomUUID

@JooqTest
class JooqSynchronizedPostRepositoryTest(context: DSLContext, flyway: Flyway) : FreeSpec({

  beforeEach {
    flyway.clean()
    flyway.migrate()
  }

  val repository = JooqSynchronizedPostRepository(context)

  fun SynchronizedPostRepository.storeAndRetrieve(data: StoreData) = findExisting(store(data))

  fun someRepostError() = Repost.Error(1, now())

  fun someRepostSuccess() = Repost.Success(now())

  "persisting synchronized post" - {
    data class PersistTestCase(
      val pageName: PageName?,
      val classification: Classification,
      val repost: Repost
    )
    forAll(
      PersistTestCase(null, Classification.MissingKeywords, Repost.Skip),
      PersistTestCase(PageName("some page name"), Classification.LunchPost, Repost.Pending),
      PersistTestCase(null, Classification.LunchPost, someRepostError()),
      PersistTestCase(PageName("some page name"), Classification.LunchPost, someRepostSuccess()),
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

  fun someRepost() = Repost.Pending

  fun someStoredSynchronizedPost() = repository.storeAndRetrieve(someStoreData())

  "updating synchronized post" - {
    listOf(Repost.Skip, Repost.Pending, someRepostError(), someRepostSuccess()).forEach { newRepost ->
      "synchronized post can be updated with $newRepost repost" {
        // given
        val stored = someStoredSynchronizedPost()
        val updateData = UpdateData(stored.id, stored.version, newRepost)

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
          repost shouldBe updateData.repost
        }
      }
    }

    listOf(
      SynchronizedPostId("1"),
      SynchronizedPostId("a"),
      SynchronizedPostId(randomUUID().toString())
    ).forEach { nonexistentId ->
      "'not found' is returned when trying to update ID '$nonexistentId'" {
        // given
        val stored = someStoredSynchronizedPost()
        val updateData = UpdateData(nonexistentId, stored.version, someRepost())

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
      "'concurrent modification' is returned when trying to update wrong version '$wrongVersion'" {
        // given
        val stored = someStoredSynchronizedPost()
        val updateData = UpdateData(stored.id, wrongVersion, someRepost())

        // expect
        shouldThrow<SynchronizedPostModifiedConcurrently> {
          repository.updateExisting(updateData)
        }
      }
    }
  }

  "finding existing synchronized post" - {
    listOf(
      SynchronizedPostId("1"),
      SynchronizedPostId("a"),
      SynchronizedPostId(randomUUID().toString())
    ).forEach { nonexistentId ->
      "'not found' is returned when trying to find ID '$nonexistentId'" {
        // given
        someStoredSynchronizedPost()

        // expect
        shouldThrow<SynchronizedPostNotFound> {
          repository.findExisting(nonexistentId)
        }
      }
    }
  }

  "finding last seen synchronized post of a given page" {
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

  "should return null when trying to find last seen post among no posts" {
    // expect
    repository.findLastSeen(PageId("some page id")) should beNull()
  }

  "getting last seen synchronized posts" {
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
})
