package dev.pankowski.garcon.domain

import java.time.Instant

interface FacebookPostClient {

  /**
   * Fetches posts of lunch page described by given configuration.
   *
   * Publication date-time of last seen post acts as a hint. Implementation <em>can</em> return posts published before
   * that date-time. However, it should always attempt to return all posts published after that date-time.
   */
  fun fetch(pageConfig: LunchPageConfig, lastSeenPublishedAt: Instant?): Pair<PageName?, Posts>
}
