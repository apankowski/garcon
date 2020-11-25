package dev.pankowski.garcon.domain

import java.net.URI
import java.time.Instant

data class FacebookId(val id: String)

data class FacebookPost(
  val facebookId: FacebookId,
  val link: URI,
  val publishedAt: Instant,
  val content: String
)

typealias FacebookPosts = List<FacebookPost>
