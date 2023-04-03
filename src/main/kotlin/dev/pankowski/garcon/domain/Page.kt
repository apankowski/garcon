package dev.pankowski.garcon.domain

data class PageId(val value: String)

data class PageName(val value: String)

data class Page(
  val name: PageName?,
  val posts: Sequence<Post>,
)

interface PageClient {

  /**
   * Loads a page described by given configuration.
   *
   * Page name should be returned if its extraction is possible.
   * Posts can appear in any order in the sequence.
   */
  fun load(pageConfig: PageConfig): Page
}
