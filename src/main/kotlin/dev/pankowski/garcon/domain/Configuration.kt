package dev.pankowski.garcon.domain

import org.springframework.boot.context.properties.ConfigurationProperties
import java.net.URL
import java.time.Duration
import java.util.*

/** Configuration of lunch feature of the bot. */
@ConfigurationProperties("lunch")
data class LunchConfig(

  /** Configuration of lunch pages to synchronize. */
  val pages: List<PageConfig> = emptyList(),
)

data class PageKey(val value: String)

/** Configuration of a single lunch page to synchronize. */
data class PageConfig(

  /** Key of Facebook post page containing lunch posts. */
  val key: PageKey,

  /** URL of Facebook post page containing lunch posts. */
  val url: URL,
)

/** Configuration of synchronization of lunch posts. */
@ConfigurationProperties("lunch.sync")
data class SyncConfig(

  /** Interval between consecutive synchronizations of lunch posts. */
  val interval: Duration?,
)

/** Configuration of web client used to load lunch pages. */
@ConfigurationProperties("lunch.client")
data class ClientConfig(

  /** User agent by which the client identifies itself when fetching lunch pages. */
  val userAgent: String = "Mozilla/5.0 (X11; Ubuntu; Linux x86_64; rv:80.0) Gecko/20100101 Firefox/80.0",

  /** Max time to wait for the lunch page to be fetched. */
  val timeout: Duration = Duration.ofSeconds(10),

  /** Number of retries in case of failure. */
  val retryCount: Int = 2,

  /** Min wait time between retries. */
  val retryMinJitter: Duration = Duration.ofMillis(50),

  /** Max wait time between retries. */
  val retryMaxJitter: Duration = Duration.ofSeconds(3),
)

/** Configuration related to lunch post classification. */
@ConfigurationProperties("lunch.post")
data class PostConfig(

  /** Locale of text of posts used while extracting their keywords. */
  val locale: Locale = Locale.ENGLISH,

  /** Lunch post keywords. */
  val keywords: List<Keyword> = listOf(Keyword("lunch", 1)),
)

/** Slack-related configuration. */
@ConfigurationProperties("lunch.slack")
data class SlackConfig(

  /** Signing secret of the Slack app used for request verification. */
  val signingSecret: String?,

  /** Token of the Slack app privileged to send and update reposts. Starts with `xoxb-`. */
  val token: String,

  /** Channel ID (`C1234567`) or name (`#random`) to send reposts to. */
  val channel: String,

  /** Base URL of Slack's Methods API. Used for testing purposes. */
  val methodsApiBaseUrl: String? = null,
)

/** Configuration of retrying failed reposts. */
@ConfigurationProperties("lunch.repost.retry")
data class RepostRetryConfig(

  /** Interval between consecutive attempts to retry failed reposts. */
  val interval: Duration?,

  /** Base delay in the exponential backoff between consecutive retries of a failed repost. */
  val baseDelay: Duration = Duration.ofMinutes(1),

  /** Max retry attempts for a failed repost. */
  val maxAttempts: Int = 10,
)
