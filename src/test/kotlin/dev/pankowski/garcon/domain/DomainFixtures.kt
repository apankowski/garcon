package dev.pankowski.garcon.domain

import java.net.URL
import java.time.Duration
import java.time.Instant
import java.time.temporal.ChronoUnit.MICROS
import java.util.*
import java.util.concurrent.atomic.AtomicInteger

/**
 * Idiom to be used instead of `LocalDateTime.now()` in persistence tests.
 *
 * Java generates date-times with 9-digit second precision, but Postgres supports timestamps with 6-digit
 * second precision and truncates further digits. To simplify writing persistence tests, this idiom does
 * the truncation on Java side, so that values returned by it, persisted to and subsequently read from the
 * database can be compared by simple equality.
 */
fun now() = Instant.now().truncatedTo(MICROS)!!

val PolishLocale = Locale.of("pl", "PL")!!

private val FacebookPostIdSequence = AtomicInteger(1)
private fun generatedFacebookPostId() =
  FacebookPostIdSequence.getAndIncrement()
    .toString()
    .padStart(4, '0')
    .let { FacebookPostId(it) }

// Configs
fun someLunchConfig(
  pages: List<PageConfig> = emptyList(),
) = LunchConfig(pages)

fun somePageConfig(
  pageKey: PageKey = PageKey("PageKey"),
  url: URL = toURL("http://localhost:4321/posts"),
) = PageConfig(pageKey, url)

fun someSyncConfig(
  interval: Duration? = Duration.ofMinutes(5),
) = SyncConfig(interval)

fun someClientConfig(
  userAgent: String = "Some User Agent",
  timeout: Duration = Duration.ofSeconds(5),
  retries: Int = 1,
  retryMinJitter: Duration = Duration.ofMillis(50),
  retryMaxJitter: Duration = Duration.ofMillis(500),
) = ClientConfig(userAgent, timeout, retries, retryMinJitter, retryMaxJitter)

fun somePostConfig(
  locale: Locale = PolishLocale,
  keywords: List<Keyword> = listOf(Keyword("lunch", 1), Keyword("lunchowa", 2)),
) = PostConfig(locale, keywords)

fun someSlackConfig(
  signingSecret: String? = null,
  token: String = "xoxb-some-token",
  channel: String = "#random",
  methodsApiBaseUrl: String? = null,
) = SlackConfig(signingSecret, token, channel, methodsApiBaseUrl)

fun someRepostRetryConfig(
  interval: Duration? = Duration.ofMinutes(10),
  baseDelay: Duration = Duration.ofMinutes(1),
  maxAttempts: Int = 10,
) = RepostRetryConfig(interval, baseDelay, maxAttempts)

// Domain
fun somePost(
  externalId: ExternalPostId = generatedFacebookPostId(),
  url: URL = toURL("https://facebook/post"),
  publishedAt: Instant = now(),
  content: String = "some post content",
) = Post(externalId, url, publishedAt, content)

fun somePageName(name: String = "some name") = PageName(name)

fun somePage(
  name: PageName = somePageName(),
  posts: Posts = emptyList(),
) = Page(name, posts)

fun someSynchronizedPost(
  id: SynchronizedPostId = SynchronizedPostId("Some Synchronized Post ID"),
  version: Version = Version.first(),
  createdAt: Instant = now(),
  updatedAt: Instant = now(),
  pageKey: PageKey = PageKey("SomePageKey"),
  pageName: PageName = PageName("Some Page Name"),
  post: Post = somePost(),
  classification: Classification = Classification.LUNCH_POST,
  repost: Repost = Repost.Skip,
) = SynchronizedPost(id, version, createdAt, updatedAt, pageKey, pageName, post, classification, repost)
