package dev.pankowski.garcon.domain

import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Component

@Component
class PageSynchronizer(
  private val pageClient: PageClient,
  private val lunchPostClassifier: LunchPostClassifier,
  private val postSynchronizer: PostSynchronizer,
) {

  private val log = LoggerFactory.getLogger(javaClass)

  fun synchronize(pageConfig: PageConfig) =
    Mdc.extendedWith(pageConfig.key) {
      log.info("Synchronizing posts of {}", pageConfig)
      val page = pageClient.load(pageConfig)
      val classifiedPosts = page.posts.map { lunchPostClassifier.classified(it) }
      postSynchronizer.synchronize(pageConfig.key, page.name, classifiedPosts)
    }
}

data class PostDelta(val old: Post?, val new: Post) {
  val appeared = old == null
  val changed = old != new
}

data class SynchronizedPostCreatedEvent(val new: SynchronizedPost)

data class SynchronizedPostUpdatedEvent(val old: SynchronizedPost, val new: SynchronizedPost)

@Component
class PostSynchronizer(
  private val repository: SynchronizedPostRepository,
  private val eventPublisher: ApplicationEventPublisher,
) {

  private val log = LoggerFactory.getLogger(javaClass)

  fun synchronize(pageKey: PageKey, pageName: PageName, classifiedPosts: Iterable<ClassifiedPost>) {

    log.info("Synchronizing classified posts")

    fun store(new: ClassifiedPost): SynchronizedPost {
      val repost = when (new.classification) {
        Classification.LUNCH_POST -> Repost.Pending
        Classification.REGULAR_POST -> Repost.Skip
      }
      return repository.store(SynchronizedPostStoreData(pageKey, pageName, new.post, new.classification, repost))
        .let { repository.findExisting(it) }
        .also { eventPublisher.publishEvent(SynchronizedPostCreatedEvent(it)) }
    }

    fun process(classifiedPost: ClassifiedPost) {
      val old = repository.findBy(classifiedPost.post.externalId)
      val postDelta = PostDelta(old?.post, classifiedPost.post)

      if (!postDelta.changed) return
      else if (postDelta.appeared) store(classifiedPost)
      else update(old!!, classifiedPost)
    }

    classifiedPosts
      .sortedBy { it.post.publishedAt }
      .forEach { process(it) }
  }

  private fun update(existing: SynchronizedPost, matchWith: ClassifiedPost): SynchronizedPost {
    repository.updateExisting(existing.id, existing.version, matchWith.post, matchWith.classification)
    return repository.findExisting(existing.id)
      .also { eventPublisher.publishEvent(SynchronizedPostUpdatedEvent(existing, it)) }
  }
}
