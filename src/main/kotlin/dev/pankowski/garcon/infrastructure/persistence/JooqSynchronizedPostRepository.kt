package dev.pankowski.garcon.infrastructure.persistence

import dev.pankowski.garcon.domain.*
import dev.pankowski.garcon.infrastructure.persistence.generated.Indexes
import dev.pankowski.garcon.infrastructure.persistence.generated.Tables.SYNCHRONIZED_POSTS
import dev.pankowski.garcon.infrastructure.persistence.generated.tables.records.SynchronizedPostsRecord
import org.jooq.DSLContext
import org.jooq.DatePart
import org.jooq.impl.DSL.*
import org.springframework.dao.DuplicateKeyException
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import java.net.URL
import java.sql.Timestamp
import java.time.Duration
import java.time.Instant
import java.util.*

@Repository
@Transactional(propagation = Propagation.NESTED)
class JooqSynchronizedPostRepository(private val context: DSLContext) : SynchronizedPostRepository {

  override fun store(data: StoreData): SynchronizedPostId =
    with(context.newRecord(SYNCHRONIZED_POSTS)) {
      val now = Instant.now()

      // Header
      id = UUID.randomUUID()
      version = Version.first()
      createdAt = now
      updatedAt = now

      // Page ID & name
      pageId = data.pageId.value
      pageName = data.pageName.value

      // Post
      postExternalId = data.post.externalId.value
      postUrl = data.post.url.toString()
      postPublishedAt = data.post.publishedAt
      postContent = data.post.content

      // Classification
      classification = data.classification

      // Repost
      repostStatus = data.repost.status
      when (data.repost) {
        is Repost.Skip,
        is Repost.Pending -> {
          // Nothing
        }

        is Repost.Failed -> {
          repostAttempts = data.repost.attempts
          repostLastAttemptAt = data.repost.lastAttemptAt
        }

        is Repost.Success ->
          repostRepostedAt = data.repost.repostedAt
      }

      try {
        store()
      } catch (e: DuplicateKeyException) {
        if (e.message!!.contains(Indexes.SYNCHRONIZED_POSTS_POST_EXTERNAL_ID.name, ignoreCase = true))
          throw SynchronizedPostHasDuplicateExternalId(
            "Failed to store synchronized post due to duplicate external ID ${data.post.externalId.value}"
          )
        else throw e
      }

      SynchronizedPostId(id.toString())
    }

  override fun updateExisting(id: SynchronizedPostId, version: Version, repost: Repost) {
    fun throwNotFound(): Nothing =
      throw SynchronizedPostNotFound("Could not find synchronized post with ID ${id.value}")

    fun throwModifiedConcurrently(): Nothing =
      throw SynchronizedPostModifiedConcurrently(
        "Synchronized post with ID ${id.value} was modified concurrently by another client"
      )

    val uuid: UUID
    try {
      uuid = UUID.fromString(id.value)
    } catch (_: IllegalArgumentException) {
      throwNotFound()
    }

    if (!context.fetchExists(selectFrom(SYNCHRONIZED_POSTS).where(SYNCHRONIZED_POSTS.ID.equal(uuid))))
      throwNotFound()

    val updateStatement = context.update(SYNCHRONIZED_POSTS)
      // Header
      .set(SYNCHRONIZED_POSTS.VERSION, version.next())
      .set(SYNCHRONIZED_POSTS.UPDATED_AT, Instant.now())

    // Repost
    updateStatement.set(SYNCHRONIZED_POSTS.REPOST_STATUS, repost.status)
    when (repost) {
      is Repost.Skip,
      is Repost.Pending ->
        updateStatement
          .setNull(SYNCHRONIZED_POSTS.REPOST_ATTEMPTS)
          .setNull(SYNCHRONIZED_POSTS.REPOST_LAST_ATTEMPT_AT)
          .setNull(SYNCHRONIZED_POSTS.REPOST_REPOSTED_AT)

      is Repost.Failed ->
        updateStatement
          .set(SYNCHRONIZED_POSTS.REPOST_ATTEMPTS, repost.attempts)
          .set(SYNCHRONIZED_POSTS.REPOST_LAST_ATTEMPT_AT, repost.lastAttemptAt)
          .setNull(SYNCHRONIZED_POSTS.REPOST_REPOSTED_AT)

      is Repost.Success ->
        updateStatement
          .setNull(SYNCHRONIZED_POSTS.REPOST_ATTEMPTS)
          .setNull(SYNCHRONIZED_POSTS.REPOST_LAST_ATTEMPT_AT)
          .set(SYNCHRONIZED_POSTS.REPOST_REPOSTED_AT, repost.repostedAt)
    }

    val updatedRows = updateStatement
      .where(SYNCHRONIZED_POSTS.ID.equal(uuid))
      .and(SYNCHRONIZED_POSTS.VERSION.equal(version))
      .execute()

    if (updatedRows == 0)
      throwModifiedConcurrently()
  }

  private fun toDomainObject(record: SynchronizedPostsRecord): SynchronizedPost {
    fun toFacebookPost(r: SynchronizedPostsRecord) =
      Post(
        externalId = ExternalId(r.postExternalId),
        url = URL(r.postUrl),
        publishedAt = r.postPublishedAt,
        content = r.postContent
      )

    fun toRepost(r: SynchronizedPostsRecord) =
      when (r.repostStatus!!) {
        RepostStatus.SKIP -> Repost.Skip
        RepostStatus.PENDING -> Repost.Pending
        RepostStatus.FAILED ->
          Repost.Failed(
            attempts = r.repostAttempts,
            lastAttemptAt = r.repostLastAttemptAt
          )

        RepostStatus.SUCCESS ->
          Repost.Success(
            repostedAt = r.repostRepostedAt
          )
      }

    return SynchronizedPost(
      id = SynchronizedPostId(record.id.toString()),
      version = record.version,
      createdAt = record.createdAt,
      updatedAt = record.updatedAt,
      pageId = PageId(record.pageId),
      pageName = PageName(record.pageName),
      post = toFacebookPost(record),
      classification = record.classification,
      repost = toRepost(record)
    )
  }

  @Transactional(readOnly = true)
  override fun findExisting(id: SynchronizedPostId): SynchronizedPost {
    fun throwNotFound(): Nothing =
      throw SynchronizedPostNotFound("Could not find synchronized post with ID ${id.value}")

    val uuid: UUID
    try {
      uuid = UUID.fromString(id.value)
    } catch (_: IllegalArgumentException) {
      throwNotFound()
    }

    return context
      .selectFrom(SYNCHRONIZED_POSTS)
      .where(SYNCHRONIZED_POSTS.ID.equal(uuid))
      .fetchOne()
      ?.let(::toDomainObject) ?: throwNotFound()
  }

  @Transactional(readOnly = true)
  override fun findByExternalId(externalId: ExternalId): SynchronizedPost? =
    context
      .selectFrom(SYNCHRONIZED_POSTS)
      .where(SYNCHRONIZED_POSTS.POST_EXTERNAL_ID.equal(externalId.value))
      .fetchOne()
      ?.let(::toDomainObject)

  @Transactional(readOnly = true)
  override fun findLastSeen(pageId: PageId): SynchronizedPost? =
    context
      .selectFrom(SYNCHRONIZED_POSTS)
      .where(SYNCHRONIZED_POSTS.PAGE_ID.equal(pageId.value))
      .orderBy(SYNCHRONIZED_POSTS.POST_PUBLISHED_AT.desc())
      .limit(1)
      .fetchOne()
      ?.let(::toDomainObject)

  @Transactional(readOnly = true)
  override fun getLastSeen(limit: Int): SynchronizedPosts =
    context.selectFrom(SYNCHRONIZED_POSTS)
      .orderBy(SYNCHRONIZED_POSTS.POST_PUBLISHED_AT.desc())
      .limit(limit)
      .fetch()
      .map(::toDomainObject)
      .toList()

  @Transactional(readOnly = true)
  override fun streamRetryable(baseDelay: Duration, maxAttempts: Int, block: (SynchronizedPost) -> Unit) =
    context.selectFrom(SYNCHRONIZED_POSTS)
      .where(SYNCHRONIZED_POSTS.REPOST_STATUS.equal(RepostStatus.FAILED))
      .and(SYNCHRONIZED_POSTS.REPOST_ATTEMPTS.lessThan(maxAttempts))
      .and(
        timestampAdd(
          SYNCHRONIZED_POSTS.REPOST_LAST_ATTEMPT_AT.coerce(Timestamp::class.java),
          power(2, SYNCHRONIZED_POSTS.REPOST_ATTEMPTS - 1) * baseDelay.toSeconds(),
          DatePart.SECOND
        )
          .coerce(SYNCHRONIZED_POSTS.REPOST_LAST_ATTEMPT_AT)
          .lessThan(Instant.now())
      )
      .orderBy(SYNCHRONIZED_POSTS.POST_PUBLISHED_AT.asc())
      .fetchSize(50)
      .stream()
      .use {
        it.map(::toDomainObject).forEach(block)
      }
}
