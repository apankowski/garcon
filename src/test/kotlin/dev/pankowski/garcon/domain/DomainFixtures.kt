package dev.pankowski.garcon.domain

import java.net.URL
import java.time.Duration
import java.time.Instant
import java.util.*

// Configs
fun someLunchConfig(
  slackWebhookUrl: URL = URL("https://slack/webhook"),
  syncInterval: Duration? = Duration.ofMinutes(5),
  pages: List<LunchPageConfig> = emptyList()
) = LunchConfig(slackWebhookUrl, syncInterval, pages,)

fun someClientConfig(
  userAgent: String = "Some User Agent",
  timeout: Duration = Duration.ofSeconds(5)
) = LunchClientConfig(userAgent, timeout)

fun somePageConfig(
  pageId: PageId = PageId("PID1"),
  url: URL = URL("http://localhost:4321/posts")
) = LunchPageConfig(pageId, url)

fun somePostConfig(
  locale: Locale = Locale("pl", "PL"),
  keywords: List<Keyword> = listOf(Keyword("lunch", 1), Keyword("lunchowa", 2)),
) = LunchPostConfig(locale, keywords)

// Domain
fun somePost(
  externalId: ExternalId = ExternalId("FBID1"),
  link: URL = URL("https://facebook/post"),
  publishedAt: Instant = Instant.now(),
  content: String = "some post content",
) = Post(externalId, link, publishedAt, content)

fun somePageName(name: String = "some name") = PageName(name)
