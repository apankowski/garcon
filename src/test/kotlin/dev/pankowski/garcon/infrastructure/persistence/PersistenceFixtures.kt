package dev.pankowski.garcon.infrastructure.persistence

import dev.pankowski.garcon.domain.*
import java.time.Instant

fun someStoreData(
  pageId: PageId = PageId("some page id"),
  pageName: PageName? = PageName("some page name"),
  post: Post = somePost(),
  classification: Classification = Classification.LunchPost,
  repost: Repost = Repost.Pending,
) = StoreData(pageId, pageName, post, classification, repost)

fun someFailedRepost(
  attempts: Int = 10,
  lastAttemptAt: Instant = now(),
) = Repost.Failed(attempts, lastAttemptAt)

fun someSuccessRepost() = Repost.Success(now())
