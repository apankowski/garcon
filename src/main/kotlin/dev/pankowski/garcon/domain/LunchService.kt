package dev.pankowski.garcon.domain

import com.google.common.annotations.VisibleForTesting
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.time.Instant

@Component
class LunchService(
  private val lunchConfig: LunchConfig,
  private val repostRetryConfig: RepostRetryConfig,
  private val repository: SynchronizedPostRepository,
  private val postClient: FacebookPostClient,
  private val lunchPostClassifier: LunchPostClassifier,
  private val reposter: SlackReposter,
) {

  private val log = LoggerFactory.getLogger(javaClass)

  @Synchronized
  fun synchronizeAll() {
    log.info("Checking for lunch posts")
    lunchConfig.pages.forEach { pageConfig ->
      try {
        synchronize(pageConfig)
      } catch (e: Exception) {
        log.error("Error while synchronizing posts of page $pageConfig", e)
      }
    }
  }

  @VisibleForTesting
  fun synchronize(page: LunchPageConfig) =
    Mdc.PageId.having(page.id) {
      val synchronizedPosts = synchronizePosts(page)
      synchronizedPosts
        .filter { it.repost != Repost.Skip }
        .forEach(::repost)
    }

  private fun synchronizePosts(pageConfig: LunchPageConfig): List<SynchronizedPost> {
    log.info("Synchronizing posts of {}", pageConfig)

    val lastSeen = repository.findLastSeen(pageConfig.id)
    val (pageName, posts) = postClient.fetch(pageConfig, lastSeen?.post?.publishedAt)
    val newPosts = posts
      .filter { it.publishedAt > (lastSeen?.post?.publishedAt ?: Instant.MIN) }

    if (newPosts.isEmpty()) {
      log.info("No new posts")
      return emptyList()
    } else {
      log.debug("Found new posts: {}", newPosts)
    }

    val synchronizedPostsToStore = newPosts.map { p ->
      val classification = lunchPostClassifier.classify(p)
      val repost = when (classification) {
        Classification.LunchPost -> Repost.Pending
        Classification.MissingKeywords -> Repost.Skip
      }
      log.info("Post $p classified as $classification, repost decision $repost")
      StoreData(pageConfig.id, pageName, p, classification, repost)
    }

    return synchronizedPostsToStore
      .map(repository::store)
      .map(repository::findExisting)
  }

  private fun repost(p: SynchronizedPost) =
    Mdc.SynchronizedPostId.having(p.id) {
      when (p.repost) {
        is Repost.Skip,
        is Repost.Success ->
          log.warn("Ignoring request to repost $p because of its repost decision")

        is Repost.Pending,
        is Repost.Failed -> {
          fun updateWith(r: Repost) =
            repository.updateExisting(UpdateData(p.id, p.version, r))
          try {
            doRepost(p)
            updateWith(Repost.Success(Instant.now()))
          } catch (e: Exception) {
            val newRepost = when (p.repost) {
              is Repost.Pending -> Repost.Failed(1, Instant.now())
              is Repost.Failed -> Repost.Failed(p.repost.attempts + 1, Instant.now())
              else -> throw IllegalStateException("Unhandled repost ${p.repost}")
            }
            updateWith(newRepost)
          }
        }
      }
    }

  private fun doRepost(p: SynchronizedPost) =
    try {
      reposter.repost(p.post, p.pageName ?: PageName(p.pageId.value))
      log.info("Post ${p.post.link} reposted on Slack")
    } catch (e: Exception) {
      log.error("Failed to repost post ${p.post.link} on Slack", e)
      throw e
    }

  fun getLog() =
    repository.getLastSeen(20)

  fun retryFailedReposts() =
    repository.streamRetryable(repostRetryConfig.baseDelay, repostRetryConfig.maxAttempts, ::repost)
}
