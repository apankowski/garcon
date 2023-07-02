package dev.pankowski.garcon.domain

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

data class PostDelta(val old: Post?, val new: Post) {
  val appeared = old == null
  val changed = old != new
}

data class SynchronizedPostDelta(val old: SynchronizedPost?, val new: SynchronizedPost) {
  private val oldIsLunchPost = old != null && old.classification == Classification.LUNCH_POST
  private val newIsLunchPost = new.classification == Classification.LUNCH_POST
  val lunchPostAppeared = !oldIsLunchPost && newIsLunchPost
  val lunchPostDisappeared = oldIsLunchPost && !newIsLunchPost
  val lunchPostChanged = oldIsLunchPost && newIsLunchPost && new.post != old?.post
}

@Component
class PageSynchronizer(
  private val repository: SynchronizedPostRepository,
  private val pageClient: PageClient,
  private val lunchPostClassifier: LunchPostClassifier,
) {

  private val log = LoggerFactory.getLogger(javaClass)

  fun synchronize(pageConfig: PageConfig): Iterable<SynchronizedPostDelta> {
    log.info("Synchronizing posts of {}", pageConfig)

    val (pageName, posts) = pageClient.load(pageConfig)

    fun store(new: ClassifiedPost): SynchronizedPost {
      val repost = when (new.classification) {
        Classification.LUNCH_POST -> Repost.Pending
        Classification.REGULAR_POST -> Repost.Skip
      }
      val id = repository.store(
        SynchronizedPostStoreData(pageConfig.key, pageName, new.post, new.classification, repost)
      )
      return repository.findExisting(id)
    }

    fun process(post: Post): SynchronizedPostDelta? {
      val old = repository.findBy(post.externalId)
      val postDelta = PostDelta(old?.post, post)

      if (!postDelta.changed) return null
      val classifiedPost = ClassifiedPost(post, lunchPostClassifier.classify(post))
      val new = if (postDelta.appeared) store(classifiedPost) else update(old!!, classifiedPost)

      return SynchronizedPostDelta(old, new)
    }

    return posts
      .sortedBy { it.publishedAt }
      .mapNotNull { process(it) }
      .onEach { log.info("Post delta: {}", it) }
  }

  private fun update(old: SynchronizedPost, new: ClassifiedPost): SynchronizedPost {
    // TODO: Reset Repost here or (better yet) move repost handling outside
    repository.updateExisting(old.id, old.version, new.post, new.classification)
    return repository.findExisting(old.id)
  }
}
