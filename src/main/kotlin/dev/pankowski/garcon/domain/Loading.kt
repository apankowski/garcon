package dev.pankowski.garcon.domain

import java.net.URL
import java.time.Instant

// Model

sealed interface ExternalPostId {
  val value: String
}

data class FacebookPostId(override val value: String) : ExternalPostId

data class Post(
  val externalId: ExternalPostId,
  val url: URL,
  val publishedAt: Instant,
  val content: String,
)

typealias Posts = Collection<Post>

data class PageName(val value: String)

data class Page(
  val name: PageName,
  val posts: Posts,
)

// Infrastructure

interface PageClient {

  /**
   * Loads a page described by given configuration.
   *
   * Page name should be returned if its extraction is possible.
   * Posts can appear in any order in the sequence.
   */
  fun load(pageConfig: PageConfig): Page
}
