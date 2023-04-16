package dev.pankowski.garcon.domain

import java.time.Instant

data class SynchronizedPostId(val value: String)

data class SynchronizedPost(
  val id: SynchronizedPostId,
  val version: Version,
  val createdAt: Instant,
  val updatedAt: Instant,
  val pageId: PageId,
  val pageName: PageName,
  val post: Post,
  val classification: Classification,
  val repost: Repost,
)

typealias SynchronizedPosts = List<SynchronizedPost>
