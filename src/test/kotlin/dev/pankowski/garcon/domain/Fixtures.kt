package dev.pankowski.garcon.domain

import java.net.URI
import java.time.Instant
import java.util.*

fun somePostConfig(
  locale: Locale = Locale("pl", "PL"),
  keywords: List<Keyword> = listOf(Keyword("lunch", 1), Keyword("lunchowa", 2)),
) = LunchPostConfig(locale, keywords)

fun somePost(
  externalId: ExternalId = ExternalId("FBID1"),
  link: URI = URI.create("https://facebook/post"),
  publishedAt: Instant = Instant.now(),
  content: String = "some post content",
) = Post(externalId, link, publishedAt, content)
