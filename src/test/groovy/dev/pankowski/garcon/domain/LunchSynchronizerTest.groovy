package dev.pankowski.garcon.domain


import dev.pankowski.garcon.persistence.InMemorySynchronizedPostRepository
import spock.lang.Specification
import spock.lang.Subject

import java.time.Instant

class LunchSynchronizerTest extends Specification {

  def pageConfig = new LunchPageConfig(
    new LunchPageId("LP1"),
    new URL("https://facebook/page")
  )

  FacebookPostClient postClient = Mock()
  LunchPostClassifier postClassifier = Mock()
  SlackReposter reposter = Mock()
  SynchronizedPostRepository repository = Spy(InMemorySynchronizedPostRepository)

  @Subject
  LunchSynchronizer synchronizer = new LunchSynchronizer(
    postClient, postClassifier, reposter, repository)

  def "should ignore already seen posts"() {
    given:
    def now = Instant.now()
    def post = new FacebookPost(
      new FacebookId("FBID1"),
      new URI("https://facebook/post/1"),
      now.minusSeconds(1),
      "Some content 1"
    )

    postClient.fetch(pageConfig) >> [post]
    repository.findLastSeen(pageConfig.id) >> now

    when:
    synchronizer.synchronize(pageConfig)

    then:
    0 * repository.store(_)
    0 * repository.updateExisting(_)
    0 * reposter.repost(_)
  }

  def "should save & repost fetched lunch posts"() {
    given:
    def post = new FacebookPost(
      new FacebookId("FBID1"),
      new URI("https://facebook/post/1"),
      Instant.now(),
      "Some content 1"
    )

    postClient.fetch(pageConfig) >> [post]
    postClassifier.classify(post) >> Classification.LunchPost.INSTANCE

    when:
    synchronizer.synchronize(pageConfig)

    then:
    1 * repository.store(new StoreData(pageConfig.id, post, Classification.LunchPost.INSTANCE, Repost.Pending.INSTANCE))
    1 * reposter.repost(post, pageConfig.id)
    1 * repository.updateExisting({ it.version == Version.first() && it.repost instanceof Repost.Success })
  }

  def "should save fetched non-lunch posts"() {
    given:
    def post = new FacebookPost(
      new FacebookId("FBID1"),
      new URI("https://facebook/post/1"),
      Instant.now(),
      "Some content 1"
    )

    postClient.fetch(pageConfig) >> [post]
    postClassifier.classify(post) >> Classification.MissingKeywords.INSTANCE

    when:
    synchronizer.synchronize(pageConfig)

    then:
    1 * repository.store(new StoreData(pageConfig.id, post, Classification.MissingKeywords.INSTANCE, Repost.Skip.INSTANCE))
    0 * reposter.repost(_, _)
    0 * repository.updateExisting(_)
  }
}
