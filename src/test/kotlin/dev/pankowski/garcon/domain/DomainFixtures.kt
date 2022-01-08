package dev.pankowski.garcon.domain

import java.net.URL
import java.time.Duration
import java.time.Instant
import java.time.temporal.ChronoUnit.MICROS
import java.util.*

// Idiom to be used instead of Instant.now(). Postgres supports timestamps with 6-digit second precision.
// The goal is to make JVM-generated Instants compatible with Postgres so we can compare them by equality.
fun now() = Instant.now().truncatedTo(MICROS)!!

val PolishLocale = Locale("pl", "PL")

// Configs
fun someLunchConfig(
  pages: List<LunchPageConfig> = emptyList()
) = LunchConfig(pages)

fun somePageConfig(
  pageId: PageId = PageId("PID1"),
  url: URL = URL("http://localhost:4321/posts")
) = LunchPageConfig(pageId, url)

fun someSyncConfig(
  interval: Duration? = Duration.ofMinutes(5),
) = LunchSyncConfig(interval)

fun someClientConfig(
  userAgent: String = "Some User Agent",
  timeout: Duration = Duration.ofSeconds(5),
  retries: Int = 1,
  retryMinJitter: Duration = Duration.ofMillis(50),
  retryMaxJitter: Duration = Duration.ofMillis(500),
) = LunchClientConfig(userAgent, timeout, retries, retryMinJitter, retryMaxJitter)

fun somePostConfig(
  locale: Locale = PolishLocale,
  keywords: List<Keyword> = listOf(Keyword("lunch", 1), Keyword("lunchowa", 2)),
) = LunchPostConfig(locale, keywords)

fun someSlackConfig(
  webhookUrl: URL = URL("https://slack/webhook"),
) = SlackConfig(webhookUrl)

fun someRetryConfig(
  interval: Duration? = Duration.ofMinutes(10),
  baseDelay: Duration = Duration.ofMinutes(1),
  maxAttempts: Int = 10,
) = RetryConfig(interval, baseDelay, maxAttempts)

// Domain
fun somePost(
  externalId: ExternalId = ExternalId("FBID1"),
  link: URL = URL("https://facebook/post"),
  publishedAt: Instant = now(),
  content: String = "some post content",
) = Post(externalId, link, publishedAt, content)

fun somePageName(name: String = "some name") = PageName(name)

fun someSynchronizedPost(
  id: SynchronizedPostId = SynchronizedPostId("Some Synchronized Post ID"),
  version: Version = Version.first(),
  createdAt: Instant = now(),
  updatedAt: Instant = now(),
  pageId: PageId = PageId("Some Page ID"),
  pageName: PageName? = PageName("Some Page Name"),
  post: Post = somePost(),
  classification: Classification = Classification.LunchPost,
  repost: Repost = Repost.Skip,
) = SynchronizedPost(id, version, createdAt, updatedAt, pageId, pageName, post, classification, repost)
