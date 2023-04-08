package dev.pankowski.garcon.domain

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.time.Instant

@Component
class PageSynchronizer(
  private val repository: SynchronizedPostRepository,
  private val pageClient: PageClient,
  private val lunchPostClassifier: LunchPostClassifier,
) {

  private val log = LoggerFactory.getLogger(javaClass)

  fun synchronize(pageConfig: PageConfig): Sequence<SynchronizedPost> {
    log.info("Synchronizing posts of {}", pageConfig)

    val lastSeen = repository.findLastSeen(pageConfig.id)
    val cutoffDate = lastSeen?.post?.publishedAt ?: Instant.MIN
    val (pageName, posts) = pageClient.load(pageConfig)

    return posts
      .filter { it.publishedAt > cutoffDate }
      .sortedBy { it.publishedAt }
      .onEach { log.info("Found new post: {}", it) }
      .map { p ->
        val classification = lunchPostClassifier.classify(p)
        val repost = when (classification) {
          Classification.LUNCH_POST -> Repost.Pending
          Classification.REGULAR_POST -> Repost.Skip
        }
        log.info("Post $p classified as $classification, repost decision $repost")
        StoreData(pageConfig.id, pageName, p, classification, repost)
      }
      .map(repository::store)
      .map(repository::findExisting)
  }
}
