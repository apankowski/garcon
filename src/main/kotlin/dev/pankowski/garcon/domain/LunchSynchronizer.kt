package dev.pankowski.garcon.domain

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.time.Instant

@Component
class LunchSynchronizer(
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
        .forEach { attemptRepost(it, page.id) }
    }

  private fun synchronizePosts(page: LunchPageConfig): List<SynchronizedPost> {
    log.info("Synchronizing posts of {}", page)

    val lastSeen = repository.findLastSeen(page.id)
    val newPosts = postClient.fetch(page, lastSeen?.post?.publishedAt)

    if (newPosts.isEmpty()) {
      log.info("No new posts")
      return emptyList()
    } else {
      log.debug("Found new posts: {}", newPosts)
    }

    val synchronizedPostsToStore = newPosts.map {
      val classification = lunchPostClassifier.classify(it)
      val repost = decideOnRepost(classification)
      log.info("Post $it classified as $classification, repost decision $repost")
      StoreData(page.id, it, classification, repost)
    }

    return synchronizedPostsToStore
      .map(repository::store)
      .map(repository::findExisting)
  }

  private fun decideOnRepost(c: Classification) =
    when (c) {
      Classification.LunchPost -> Repost.Pending
      Classification.MissingKeywords -> Repost.Skip
    }

  private fun attemptRepost(p: SynchronizedPost, pageId: LunchPageId) {
    fun updateWith(r: Repost) =
      repository.updateExisting(UpdateData(p.id, p.version, r))

    when (p.repost) {
      is Repost.Skip,
      is Repost.Success ->
        log.warn("Ignoring request to repost $p because of its repost")
      is Repost.Pending,
      is Repost.Error -> {
        if (p.repost is Repost.Error && p.repost.errorCount > 3) return
        try {
          repost(p.post, pageId)
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

  private fun repost(post: Post, pageId: LunchPageId) =
    try {
      reposter.repost(post, pageId)
      log.info("Post ${post.link} reposted on Slack")
    } catch (e: Exception) {
      log.error("Failed to repost post ${post.link} on Slack", e)
      throw e
    }

  fun getLog() =
    repository.getLog(20)
}
