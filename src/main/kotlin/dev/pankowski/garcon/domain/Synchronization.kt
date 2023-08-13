package dev.pankowski.garcon.domain

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@Component
class PageSynchronizer(
  private val pageClient: PageClient,
  private val lunchPostClassifier: LunchPostClassifier,
  private val postSynchronizer: PostSynchronizer,
  private val reposter: Reposter,
) {

  private val log = LoggerFactory.getLogger(javaClass)

  fun synchronize(pageConfig: PageConfig) =
    Mdc.extendedWith(pageConfig.key) {
      log.info("Synchronizing posts of {}", pageConfig)
      val page = pageClient.load(pageConfig)
      val classifiedPosts = page.posts.map { lunchPostClassifier.classified(it) }
      postSynchronizer.synchronize(pageConfig.key, page.name, classifiedPosts)
        .forEach { if (it.lunchPostAppeared) reposter.repost(it.new) }
    }
}

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
class PostSynchronizer(
  private val repository: SynchronizedPostRepository,
) {

  private val log = LoggerFactory.getLogger(javaClass)

  fun synchronize(pageKey: PageKey, pageName: PageName, classifiedPosts: Iterable<ClassifiedPost>):
    Iterable<SynchronizedPostDelta> {

    log.info("Synchronizing classified posts")

    fun store(new: ClassifiedPost): SynchronizedPost {
      val repost = when (new.classification) {
        Classification.LUNCH_POST -> Repost.Pending
        Classification.REGULAR_POST -> Repost.Skip
      }
      val id = repository.store(
        SynchronizedPostStoreData(pageKey, pageName, new.post, new.classification, repost)
      )
      return repository.findExisting(id)
    }

    fun process(classifiedPost: ClassifiedPost): SynchronizedPostDelta? {
      val old = repository.findBy(classifiedPost.post.externalId)
      val postDelta = PostDelta(old?.post, classifiedPost.post)

      if (!postDelta.changed) return null
      val new = if (postDelta.appeared) store(classifiedPost) else update(old!!, classifiedPost)

      return SynchronizedPostDelta(old, new)
    }

    return classifiedPosts
      .sortedBy { it.post.publishedAt }
      .mapNotNull { process(it) }
      .onEach { log.info("Post delta: {}", it) }
  }

  private fun update(old: SynchronizedPost, new: ClassifiedPost): SynchronizedPost {
    // TODO: Reset Repost here or (better yet) move repost handling outside
    repository.updateExisting(old.id, old.version, new.post, new.classification)
    return repository.findExisting(old.id)
  }
}
