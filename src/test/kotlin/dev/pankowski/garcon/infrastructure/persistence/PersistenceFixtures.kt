package dev.pankowski.garcon.infrastructure.persistence

import dev.pankowski.garcon.domain.*

fun someStoreData(
  pageId: PageId = PageId("some page id"),
  pageName: PageName? = PageName("some page name"),
  post: Post = somePost(),
  classification: Classification = Classification.LunchPost,
  repost: Repost = Repost.Pending,
) = StoreData(pageId, pageName, post, classification, repost)
