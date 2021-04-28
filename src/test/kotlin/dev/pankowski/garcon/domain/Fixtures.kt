package dev.pankowski.garcon.domain

import java.net.URL
import java.time.Instant
import java.util.*

fun somePostConfig(
  locale: Locale = Locale("pl", "PL"),
  keywords: List<Keyword> = listOf(Keyword("lunch", 1), Keyword("lunchowa", 2)),
) = LunchPostConfig(locale, keywords)

fun somePost(
  externalId: ExternalId = ExternalId("FBID1"),
  link: URL = URL("https://facebook/post"),
  publishedAt: Instant = Instant.now(),
  content: String = "some post content",
) = Post(externalId, link, publishedAt, content)
