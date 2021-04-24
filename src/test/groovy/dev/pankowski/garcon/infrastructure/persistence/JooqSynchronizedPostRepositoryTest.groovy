package dev.pankowski.garcon.infrastructure.persistence

import dev.pankowski.garcon.domain.Classification
import dev.pankowski.garcon.domain.ExternalId
import dev.pankowski.garcon.domain.PageId
import dev.pankowski.garcon.domain.PageName
import dev.pankowski.garcon.domain.Post
import dev.pankowski.garcon.domain.Repost
import dev.pankowski.garcon.domain.StoreData
import dev.pankowski.garcon.domain.SynchronizedPostId
import dev.pankowski.garcon.domain.SynchronizedPostModifiedConcurrently
import dev.pankowski.garcon.domain.SynchronizedPostNotFound
import dev.pankowski.garcon.domain.SynchronizedPostRepository
import dev.pankowski.garcon.domain.UpdateData
import dev.pankowski.garcon.domain.Version
import org.flywaydb.test.annotation.FlywayTest
import org.jooq.DSLContext
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.jooq.JooqTest
import spock.lang.Specification
import spock.lang.Subject

import java.time.Instant

import static java.time.temporal.ChronoUnit.MICROS

@JooqTest
@FlywayTest
class JooqSynchronizedPostRepositoryTest extends Specification {

  @Autowired
  DSLContext context

  @Subject
  SynchronizedPostRepository repository

  def setup() {
    repository = new JooqSynchronizedPostRepository(context)
  }

  private static def somePost(Map arguments = [:]) {
    arguments = [
      "externalId" : "SomeExternalId",
      "publishedAt": Instant.now().truncatedTo(MICROS),
      "content"    : "some content"
    ] << arguments

    new Post(
      new ExternalId(arguments.externalId as String),
      URI.create("https://www.facebook.com/" + arguments.externalId),
      arguments.publishedAt as Instant,
      arguments.content as String
    )
  }

  private static def someRepostError() {
    new Repost.Error(1, Instant.now().truncatedTo(MICROS))
  }

  private static def someRepostSuccess() {
    new Repost.Success(Instant.now().truncatedTo(MICROS))
  }

  def "should persist synchronized post"() {
    given:
    def pageName = pname != null ? new PageName(pname) : null
    def pageId = new PageId(pid)
    def storeData = new StoreData(pageId, pageName, somePost(), classification, repost)

    when:
    def before = Instant.now()
    def storedId = repository.store(storeData)
    def after = Instant.now()

    and:
    def retrieved = repository.findExisting(storedId)

    then:
    verifyAll(retrieved) {
      id == storedId
      it.pageName == storeData.pageName
      version == Version.first()
      before <= createdAt && createdAt <= after
      updatedAt == createdAt
      pageId == storeData.pageId
      post == storeData.post
      classification == storeData.classification
      repost == storeData.repost
    }

    where:
    pid       | pname            | classification                          | repost
    "100"     | null             | Classification.MissingKeywords.INSTANCE | Repost.Skip.INSTANCE
    "XXX"     | "some page name" | Classification.LunchPost.INSTANCE       | Repost.Pending.INSTANCE
    "some id" | null             | Classification.LunchPost.INSTANCE       | someRepostError()
    "test"    | "some page name" | Classification.LunchPost.INSTANCE       | someRepostSuccess()
  }

  private static def somePageId() {
    new PageId("1")
  }

  private static def somePageName() {
    new PageName("some name")
  }

  private static def someClassification() {
    Classification.LunchPost.INSTANCE
  }

  private static def someRepost() {
    Repost.Pending.INSTANCE
  }

  private def someStoredSynchronizedPost() {
    def storeData = new StoreData(somePageId(), somePageName(), somePost(), someClassification(), someRepost())
    repository.findExisting(repository.store(storeData))
  }

  def "should update synchronized post"() {
    given:
    def stored = someStoredSynchronizedPost()
    def updateData = new UpdateData(stored.id, stored.version, newRepost)

    when:
    def before = Instant.now()
    repository.updateExisting(updateData)
    def after = Instant.now()

    and:
    def updated = repository.findExisting(stored.id)

    then:
    verifyAll(updated) {
      id == stored.id
      version == stored.version.next()
      createdAt == stored.createdAt
      before <= updatedAt && updatedAt <= after
      pageId == stored.pageId
      post == stored.post
      classification == stored.classification
      repost == updateData.repost
    }

    where:
    newRepost               | _
    Repost.Skip.INSTANCE    | _
    Repost.Pending.INSTANCE | _
    someRepostError()       | _
    someRepostSuccess()     | _
  }

  def "should throw when trying to update nonexistent synchronized post"() {
    given:
    def stored = someStoredSynchronizedPost()
    def updateData = new UpdateData(new SynchronizedPostId(nonexistentId), stored.version, someRepost())

    when:
    repository.updateExisting(updateData)

    then:
    thrown SynchronizedPostNotFound

    where:
    nonexistentId << ["1", "a", UUID.randomUUID().toString()]
  }

  def "should throw when trying to update wrong version"() {
    given:
    def stored = someStoredSynchronizedPost()
    def updateData = new UpdateData(stored.id, new Version(wrongVersion), someRepost())

    when:
    repository.updateExisting(updateData)

    then:
    thrown SynchronizedPostModifiedConcurrently

    where:
    wrongVersion << [0, 2]
  }

  def "should throw when trying to find nonexistent synchronized post"() {
    given:
    someStoredSynchronizedPost()

    when:
    repository.findExisting(new SynchronizedPostId(nonexistentId))

    then:
    thrown SynchronizedPostNotFound

    where:
    nonexistentId << ["1", "a", UUID.randomUUID().toString()]
  }

  def "should allow finding last seen post of a given page"() {
    given:
    def somePageId = new PageId("1")
    def otherPageId = new PageId("2")
    def now = Instant.now().truncatedTo(MICROS)

    repository.findExisting(repository.store(new StoreData(
      somePageId,
      somePageName(),
      somePost(externalId: "3", publishedAt: now.minusSeconds(300), content: "3"),
      someClassification(),
      someRepost()
    )))

    def somePageExpectedLastSeen = repository.findExisting(repository.store(new StoreData(
      somePageId,
      somePageName(),
      somePost(externalId: "1", publishedAt: now.minusSeconds(100), content: "1"),
      someClassification(),
      someRepost()
    )))

    repository.findExisting(repository.store(new StoreData(
      somePageId,
      somePageName(),
      somePost(externalId: "2", publishedAt: now.minusSeconds(200), content: "2"),
      someClassification(),
      someRepost()
    )))

    def otherPageExpectedLastSeen = repository.findExisting(repository.store(new StoreData(
      otherPageId,
      somePageName(),
      somePost(externalId: "4", publishedAt: now.minusSeconds(10), content: "4"),
      someClassification(),
      someRepost()
    )))

    repository.findExisting(repository.store(new StoreData(
      otherPageId,
      somePageName(),
      somePost(externalId: "5", publishedAt: now.minusSeconds(50), content: "5"),
      someClassification(),
      someRepost()
    )))

    expect:
    repository.findLastSeen(somePageId) == somePageExpectedLastSeen
    repository.findLastSeen(otherPageId) == otherPageExpectedLastSeen
  }

  def "should return null when trying to find last seen post among no posts"() {
    expect:
    repository.findLastSeen(somePageId()) == null
  }

  def "should return last seen posts"() {
    given:
    def now = Instant.now().truncatedTo(MICROS)
    def posts = (1..100).collect {
      def i = it
      repository.findExisting(repository.store(new StoreData(
        somePageId(),
        somePageName(),
        somePost(externalId: "$i", publishedAt: now.minusSeconds(i), content: "Content #$i"),
        someClassification(),
        someRepost()
      )))
    }

    when:
    def actualLog = repository.getLastSeen(20)

    then:
    actualLog == posts.take(20)
  }
}
