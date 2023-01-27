package dev.pankowski.garcon.domain

data class PageOfPosts(
  val pageName: PageName?,
  val posts: Posts,
)

interface FacebookPageClient {

  /**
   * Fetches posts of a page described by given configuration.
   */
  fun fetch(pageConfig: PageConfig): PageOfPosts
}
