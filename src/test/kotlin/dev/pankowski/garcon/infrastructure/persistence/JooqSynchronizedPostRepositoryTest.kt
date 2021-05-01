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
      val storeData = StoreData(PageId("some page id"), pageName, somePost(), classification, repost)

      // when
      val before = now()
      val storedId = repository.store(storeData)
      val after = now()

      // and
      val retrieved = repository.findExisting(storedId)

      // then
      assertSoftly(retrieved) {
        id shouldBe storedId
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

  fun somePageId() = PageId("1")

  fun somePageName() = PageName("some name")

  fun someClassification() = Classification.LunchPost

  fun someRepost() = Repost.Pending

  fun someStoredSynchronizedPost(): SynchronizedPost {
    val storeData = StoreData(somePageId(), somePageName(), somePost(), someClassification(), someRepost())
    return repository.findExisting(repository.store(storeData))
  }

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

    listOf("1", "a", randomUUID().toString()).forEach { nonexistentId ->
      "'not found' is returned when trying to update ID '$nonexistentId'" {
        // given
        val stored = someStoredSynchronizedPost()
        val updateData = UpdateData(SynchronizedPostId(nonexistentId), stored.version, someRepost())

        // expect
        shouldThrow<SynchronizedPostNotFound> {
          repository.updateExisting(updateData)
        }
      }
    }

    listOf(0, 2).forEach { wrongVersion ->
      "'concurrent modification' is returned when trying to update wrong version '$wrongVersion'" {
        // given
        val stored = someStoredSynchronizedPost()
        val updateData = UpdateData(stored.id, Version(wrongVersion), someRepost())

        // expect
        shouldThrow<SynchronizedPostModifiedConcurrently> {
          repository.updateExisting(updateData)
        }
      }
    }
  }

  "finding existing synchronized post" - {
    listOf("1", "a", randomUUID().toString()).forEach { nonexistentId ->
      "'not found' is returned when trying to find ID '$nonexistentId'" {
        // given
        someStoredSynchronizedPost()

        // expect
        shouldThrow<SynchronizedPostNotFound> {
          repository.findExisting(SynchronizedPostId(nonexistentId))
        }
      }
    }
  }

  "finding last seen synchronized post of a given page" {
    // given
    val somePageId = PageId("1")
    val otherPageId = PageId("2")
    val somePointInTime = Instant.parse("2000-01-01T00:00:00Z")

    repository.findExisting(
      repository.store(
        StoreData(
          somePageId,
          somePageName(),
          somePost(externalId = ExternalId("1"), publishedAt = somePointInTime.plus(1, HOURS), content = "3"),
          someClassification(),
          someRepost()
        )
      )
    )

    val somePageExpectedLastSeen = repository.findExisting(
      repository.store(
        StoreData(
          somePageId,
          somePageName(),
          somePost(externalId = ExternalId("3"), publishedAt = somePointInTime.plus(3, HOURS), content = "1"),
          someClassification(),
          someRepost()
        )
      )
    )

    repository.findExisting(
      repository.store(
        StoreData(
          somePageId,
          somePageName(),
          somePost(externalId = ExternalId("2"), publishedAt = somePointInTime.plus(2, HOURS), content = "2"),
          someClassification(),
          someRepost()
        )
      )
    )

    val otherPageExpectedLastSeen = repository.findExisting(
      repository.store(
        StoreData(
          otherPageId,
          somePageName(),
          somePost(externalId = ExternalId("4"), publishedAt = somePointInTime.plus(2, HOURS), content = "4"),
          someClassification(),
          someRepost()
        )
      )
    )

    repository.findExisting(
      repository.store(
        StoreData(
          otherPageId,
          somePageName(),
          somePost(externalId = ExternalId("5"), publishedAt = somePointInTime.plus(1, HOURS), content = "5"),
          someClassification(),
          someRepost()
        )
      )
    )

    // expect
    repository.findLastSeen(somePageId) shouldBe somePageExpectedLastSeen
    repository.findLastSeen(otherPageId) shouldBe otherPageExpectedLastSeen
  }

  "should return null when trying to find last seen post among no posts" {
    // expect
    repository.findLastSeen(somePageId()) should beNull()
  }

  "getting last seen synchronized posts" {
    // given
    val now = now()
    val posts = (1..100).map { i ->
      repository.findExisting(
        repository.store(
          StoreData(
            somePageId(),
            somePageName(),
            somePost(
              externalId = ExternalId("$i"),
              publishedAt = now.minus(i.toLong(), HOURS),
              content = "Content #$i"
            ),
            someClassification(),
            someRepost()
          )
        )
      )
    }

    // when
    val actualLog = repository.getLastSeen(20)

    // then
    actualLog shouldBe posts.take(20)
  }
})
