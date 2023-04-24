package dev.pankowski.garcon.infrastructure.persistence

import dev.pankowski.garcon.domain.*
import java.time.Instant
import java.time.temporal.ChronoUnit.MINUTES

fun someStoreData(
  pageId: PageId = PageId("some page id"),
  pageName: PageName = PageName("some page name"),
  post: Post = somePost(),
  classification: Classification = Classification.LUNCH_POST,
  repost: Repost = Repost.Pending,
) = SynchronizedPostStoreData(pageId, pageName, post, classification, repost)

fun someFailedRepost(
  attempts: Int = 10,
  lastAttemptAt: Instant = now(),
  nextAttemptAt: Instant = lastAttemptAt.plus(5, MINUTES)
) = Repost.Failed(attempts, lastAttemptAt, nextAttemptAt)

fun someSuccessRepost() = Repost.Success(now())
