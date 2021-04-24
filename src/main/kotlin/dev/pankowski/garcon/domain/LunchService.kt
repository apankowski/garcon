package dev.pankowski.garcon.domain

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.time.Instant

@Component
class LunchService(
  private val lunchConfig: LunchConfig,
  private val postClient: FacebookPostClient,
  private val lunchPostClassifier: LunchPostClassifier,
  private val reposter: SlackReposter,
  private val repository: SynchronizedPostRepository,
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

  // Visible for testing
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

    if (posts.isEmpty()) {
      log.info("No new posts")
      return emptyList()
    } else {
      log.debug("Found new posts: {}", posts)
    }

    val synchronizedPostsToStore = posts.map { p ->
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
        is Repost.Error -> {
          fun updateWith(r: Repost) =
            repository.updateExisting(UpdateData(p.id, p.version, r))

          try {
            doRepost(p)
            updateWith(Repost.Success(Instant.now()))
          } catch (e: Exception) {
            val newRepost = when (p.repost) {
              is Repost.Pending -> Repost.Error(1, Instant.now())
              is Repost.Error -> Repost.Error(p.repost.errorCount + 1, Instant.now())
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
}
