package dev.pankowski.garcon.domain

import java.time.Instant

interface FacebookPostClient {

  fun fetch(pageConfig: LunchPageConfig, lastSeenPublishedAt: Instant?): Pair<PageName?, Posts>
}
