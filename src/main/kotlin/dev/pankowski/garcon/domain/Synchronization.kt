package dev.pankowski.garcon.domain

import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Component
import java.time.Instant

// Model

data class SynchronizedPostId(val value: String)

data class SynchronizedPost(
  val id: SynchronizedPostId,
  val version: Version,
  val createdAt: Instant,
  val updatedAt: Instant,
  val pageKey: PageKey,
  val pageName: PageName,
  val post: Post,
  val classification: Classification,
  val repost: Repost,
) {
  val isLunchPost = classification == Classification.LUNCH_POST
}

typealias SynchronizedPosts = List<SynchronizedPost>

data class SynchronizedPostCreatedEvent(val new: SynchronizedPost)
data class SynchronizedPostUpdatedEvent(val old: SynchronizedPost, val new: SynchronizedPost)

// Infrastructure

class SynchronizedPostNotFound(message: String) : RuntimeException(message)
class SynchronizedPostModifiedConcurrently(message: String) : RuntimeException(message)
class SynchronizedPostHasDuplicateExternalId(message: String) : RuntimeException(message)

data class SynchronizedPostStoreData(
  val pageKey: PageKey,
  val pageName: PageName,
  val post: Post,
  val classification: Classification,
  val repost: Repost,
)

interface SynchronizedPostRepository {

  fun store(data: SynchronizedPostStoreData): SynchronizedPostId
  fun updateExisting(id: SynchronizedPostId, version: Version, repost: Repost)
  fun updateExisting(id: SynchronizedPostId, version: Version, post: Post, classification: Classification)
  fun findExisting(id: SynchronizedPostId): SynchronizedPost
  fun findBy(externalId: ExternalPostId): SynchronizedPost?
  fun getLastSeen(limit: Int): SynchronizedPosts
  fun streamRetryable(block: (SynchronizedPost) -> Unit)
}

// Logic

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
      page.posts
        .sortedBy { it.publishedAt }
        .map { lunchPostClassifier.classified(it) }
        .forEach { postSynchronizer.synchronize(pageConfig.key, page.name, it) }
    }
}

@Component
class PostSynchronizer(
  private val repository: SynchronizedPostRepository,
  private val eventPublisher: ApplicationEventPublisher,
) {

  private val log = LoggerFactory.getLogger(javaClass)

  fun synchronize(pageKey: PageKey, pageName: PageName, classifiedPost: ClassifiedPost) {
    log.debug("Synchronizing classified post {}", classifiedPost)

    fun store(new: ClassifiedPost): SynchronizedPost {
      val repost = when (new.classification) {
        Classification.LUNCH_POST -> Repost.Pending
        Classification.REGULAR_POST -> Repost.Skip
      }
      return repository.store(SynchronizedPostStoreData(pageKey, pageName, new.post, new.classification, repost))
        .let { repository.findExisting(it) }
        .also { eventPublisher.publishEvent(SynchronizedPostCreatedEvent(it)) }
    }

    data class PostDelta(val old: Post?, val new: Post) {
      val appeared = old == null
      val changed = old != new
    }

    val old = repository.findBy(classifiedPost.post.externalId)
    val delta = PostDelta(old?.post, classifiedPost.post)

    if (!delta.changed) return
    else if (delta.appeared) store(classifiedPost)
    else update(old!!, classifiedPost)
  }

  private fun update(existing: SynchronizedPost, matchWith: ClassifiedPost): SynchronizedPost {
    repository.updateExisting(existing.id, existing.version, matchWith.post, matchWith.classification)
    return repository.findExisting(existing.id)
      .also { eventPublisher.publishEvent(SynchronizedPostUpdatedEvent(existing, it)) }
  }
}
