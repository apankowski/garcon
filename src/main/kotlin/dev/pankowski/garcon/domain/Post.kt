package dev.pankowski.garcon.domain

import java.net.URL
import java.time.Instant

data class ExternalId(val value: String)

data class Post(
  val externalId: ExternalId,
  val url: URL,
  val publishedAt: Instant,
  val content: String,
)

typealias Posts = List<Post>
