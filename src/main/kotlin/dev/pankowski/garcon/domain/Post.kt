package dev.pankowski.garcon.domain

import java.net.URI
import java.time.Instant

data class ExternalId(val id: String)

data class Post(
  val externalId: ExternalId,
  val link: URI,
  val publishedAt: Instant,
  val content: String
)

typealias Posts = List<Post>
