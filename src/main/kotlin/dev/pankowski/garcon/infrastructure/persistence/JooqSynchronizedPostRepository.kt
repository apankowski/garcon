package dev.pankowski.garcon.infrastructure.persistence

import dev.pankowski.garcon.domain.*
import dev.pankowski.garcon.infrastructure.persistence.generated.Tables.SYNCHRONIZED_POSTS
import dev.pankowski.garcon.infrastructure.persistence.generated.tables.records.SynchronizedPostsRecord
import org.jooq.DSLContext
import org.jooq.impl.DSL
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import java.net.URI
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
      pageName = data.pageName?.value

      // Post
      postExternalId = data.post.externalId.id
      postLink = data.post.link.toString()
      postPublishedAt = data.post.publishedAt
      postContent = data.post.content

      // Classification
      classificationStatus = data.classification.status

      // Repost
      repostStatus = data.repost.status
      when (data.repost) {
        is Repost.Skip,
        is Repost.Pending -> {
          // Nothing
        }
        is Repost.Error -> {
          repostErrorCount = data.repost.errorCount
          repostLastAttemptAt = data.repost.lastAttemptAt
        }
        is Repost.Success ->
          repostRepostedAt = data.repost.repostedAt
      }

      store()

      SynchronizedPostId(id.toString())
    }

  override fun updateExisting(data: UpdateData) {
    fun throwNotFound(): Nothing =
      throw SynchronizedPostNotFound("Could not find synchronized post with ID " + data.id.value)

    fun throwModifiedConcurrently(): Nothing =
      throw SynchronizedPostModifiedConcurrently("Synchronized post with ID " + data.id.value + " was modified concurrently by another client")

    val uuid: UUID
    try {
      uuid = UUID.fromString(data.id.value)
    } catch (_: IllegalArgumentException) {
      throwNotFound()
    }

    if (!context.fetchExists(DSL.selectFrom(SYNCHRONIZED_POSTS).where(SYNCHRONIZED_POSTS.ID.equal(uuid))))
      throwNotFound()

    val updateStatement = context.update(SYNCHRONIZED_POSTS)
      // Header
      .set(SYNCHRONIZED_POSTS.VERSION, data.version.next())
      .set(SYNCHRONIZED_POSTS.UPDATED_AT, Instant.now())

    // Repost
    updateStatement.set(SYNCHRONIZED_POSTS.REPOST_STATUS, data.repost.status)
    when (data.repost) {
      is Repost.Skip,
      is Repost.Pending ->
        updateStatement
          .setNull(SYNCHRONIZED_POSTS.REPOST_ERROR_COUNT)
          .setNull(SYNCHRONIZED_POSTS.REPOST_LAST_ATTEMPT_AT)
          .setNull(SYNCHRONIZED_POSTS.REPOST_REPOSTED_AT)
      is Repost.Error ->
        updateStatement
          .set(SYNCHRONIZED_POSTS.REPOST_ERROR_COUNT, data.repost.errorCount)
          .set(SYNCHRONIZED_POSTS.REPOST_LAST_ATTEMPT_AT, data.repost.lastAttemptAt)
          .setNull(SYNCHRONIZED_POSTS.REPOST_REPOSTED_AT)
      is Repost.Success ->
        updateStatement
          .setNull(SYNCHRONIZED_POSTS.REPOST_ERROR_COUNT)
          .setNull(SYNCHRONIZED_POSTS.REPOST_LAST_ATTEMPT_AT)
          .set(SYNCHRONIZED_POSTS.REPOST_REPOSTED_AT, data.repost.repostedAt)
    }

    val updatedRows = updateStatement
      .where(SYNCHRONIZED_POSTS.ID.equal(uuid))
      .and(SYNCHRONIZED_POSTS.VERSION.equal(data.version))
      .execute()

    if (updatedRows == 0)
      throwModifiedConcurrently()
  }

  private fun toDomainObject(record: SynchronizedPostsRecord): SynchronizedPost {
    fun toFacebookPost(r: SynchronizedPostsRecord) =
      Post(
        externalId = ExternalId(r.postExternalId),
        link = URI.create(r.postLink),
        publishedAt = r.postPublishedAt,
        content = r.postContent
      )

    fun toClassification(r: SynchronizedPostsRecord) =
      when (r.classificationStatus!!) {
        ClassificationStatus.MISSING_KEYWORDS -> Classification.MissingKeywords
        ClassificationStatus.LUNCH_POST -> Classification.LunchPost
      }

    fun toRepost(r: SynchronizedPostsRecord) =
      when (r.repostStatus!!) {
        RepostStatus.SKIP -> Repost.Skip
        RepostStatus.PENDING -> Repost.Pending
        RepostStatus.ERROR ->
          Repost.Error(
            errorCount = r.repostErrorCount,
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
      pageId = LunchPageId(record.pageId),
      pageName = record.pageName?.let(::PageName),
      post = toFacebookPost(record),
      classification = toClassification(record),
      repost = toRepost(record)
    )
  }

  @Transactional(readOnly = true)
  override fun findExisting(id: SynchronizedPostId): SynchronizedPost {
    fun throwNotFound(): Nothing =
      throw SynchronizedPostNotFound("Could not find synchronized post with ID " + id.value)

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
  override fun findLastSeen(pageId: LunchPageId): SynchronizedPost? =
    context
      .selectFrom(SYNCHRONIZED_POSTS)
      .where(SYNCHRONIZED_POSTS.PAGE_ID.equal(pageId.value))
      .orderBy(SYNCHRONIZED_POSTS.POST_PUBLISHED_AT.desc())
      .limit(1)
      .fetchOne()
      ?.let(::toDomainObject)

  @Transactional(readOnly = true)
  override fun getLog(count: Int): SynchronizationLog =
    context.selectFrom(SYNCHRONIZED_POSTS)
      .orderBy(SYNCHRONIZED_POSTS.POST_PUBLISHED_AT.desc())
      .limit(count)
      .fetch()
      .map(::toDomainObject)
      .toList()
}
