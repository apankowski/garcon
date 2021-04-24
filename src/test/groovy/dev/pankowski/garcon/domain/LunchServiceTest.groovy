package dev.pankowski.garcon.domain

import dev.pankowski.garcon.infrastructure.persistence.InMemorySynchronizedPostRepository
import kotlin.Pair
import spock.lang.Specification
import spock.lang.Subject

import java.time.Duration
import java.time.Instant

class LunchServiceTest extends Specification {

  def config = new LunchConfig(
    new URL("https://slack/webhook"),
    Duration.ofMinutes(5),
    [],
  )

  def postClient = Mock(FacebookPostClient)
  def postClassifier = Mock(LunchPostClassifier)
  def reposter = Mock(SlackReposter)
  def repository = Spy(InMemorySynchronizedPostRepository)

  @Subject
  def service = new LunchService(config, postClient, postClassifier, reposter, repository)

  def somePageConfig() {
    new LunchPageConfig(
      new PageId("some id"),
      new URL("https://facebook/page")
    )
  }

  def somePageName() {
    new PageName("some name")
  }

  def somePost() {
    new Post(
      new ExternalId("some id"),
      new URI("https://facebook/post"),
      Instant.now(),
      "some post content"
    )
  }

  def "should fetch new posts"() {
    given:
    def pageConfig = somePageConfig()

    def lastSeen = somePost()
    def lastSeenPublishedAt = lastSeen.publishedAt

    repository.findLastSeen(pageConfig.id) >> new SynchronizedPost(
      new SynchronizedPostId("some id"),
      new Version(1),
      Instant.now(),
      Instant.now(),
      new PageId("some id"),
      null,
      lastSeen,
      Classification.LunchPost.INSTANCE,
      Repost.Skip.INSTANCE
    )

    when:
    service.synchronize(pageConfig)

    then:
    1 * postClient.fetch(pageConfig, lastSeenPublishedAt) >> new Pair(somePageName(), [])
  }

  def "should save & repost fetched lunch posts"() {
    given:
    def pageConfig = somePageConfig()
    def pageName = somePageName()

    def post = somePost()
    def classification = Classification.LunchPost.INSTANCE

    postClient.fetch(pageConfig, _) >> new Pair(somePageName(), [post])
    postClassifier.classify(post) >> classification

    when:
    service.synchronize(pageConfig)

    then:
    1 * repository.store(new StoreData(pageConfig.id, pageName, post, classification, Repost.Pending.INSTANCE))
    1 * reposter.repost(post, pageConfig.id)
    1 * repository.updateExisting({ it.version == Version.first() && it.repost instanceof Repost.Success })
  }

  def "should save fetched non-lunch posts"() {
    given:
    def pageConfig = somePageConfig()
    def pageName = somePageName()

    def post = somePost()
    def classification = Classification.MissingKeywords.INSTANCE

    postClient.fetch(pageConfig, _) >> new Pair(somePageName(), [post])
    postClassifier.classify(post) >> classification

    when:
    service.synchronize(pageConfig)

    then:
    1 * repository.store(new StoreData(pageConfig.id, pageName, post, classification, Repost.Skip.INSTANCE))
    0 * reposter.repost(_, _)
    0 * repository.updateExisting(_)
  }

  def "should return synchronization log"() {
    given:
    def log = []

    repository.getLog(20) >> log

    expect:
    service.getLog().is(log)
  }
}
