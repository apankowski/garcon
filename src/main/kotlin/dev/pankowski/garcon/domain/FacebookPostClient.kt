package dev.pankowski.garcon.domain

interface FacebookPostClient {

  /**
   * Fetches posts of lunch page described by given configuration.
   */
  fun fetch(pageConfig: LunchPageConfig): Pair<PageName?, Posts>
}
