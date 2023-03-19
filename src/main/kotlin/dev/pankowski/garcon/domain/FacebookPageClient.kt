package dev.pankowski.garcon.domain

data class PageOfPosts(
  val pageName: PageName?,
  val posts: Sequence<Post>,
)

interface FacebookPageClient {

  /**
   * Fetches posts of a page described by given configuration.
   *
   * Page name should be returned if its extraction is possible.
   * Posts can appear in any order in the sequence.
   */
  fun fetch(pageConfig: PageConfig): PageOfPosts
}
