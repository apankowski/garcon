package dev.pankowski.garcon.domain

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.ConstructorBinding
import java.net.URL
import java.time.Duration
import java.util.*

/** Configuration of lunch feature of the bot. */
@ConstructorBinding
@ConfigurationProperties("lunch")
data class LunchConfig(

  /** Lunch pages. */
  val pages: List<LunchPageConfig> = emptyList(),
)

/** Configuration of a single lunch page to synchronize. */
data class LunchPageConfig(

  /** ID of Facebook post page containing lunch posts. */
  val id: PageId,

  /** URL of Facebook post page containing lunch posts. */
  val url: URL,
)

/** Configuration of synchronization of lunch posts. */
@ConstructorBinding
@ConfigurationProperties("lunch.sync")
data class SyncConfig(

  /** Interval between consecutive synchronizations of lunch posts. */
  val interval: Duration?,
)

/** Configuration of web client used to fetch lunch pages. */
@ConstructorBinding
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
@ConstructorBinding
@ConfigurationProperties("lunch.post")
data class PostConfig(

  /** Locale of text of posts used while extracting their keywords. */
  val locale: Locale = Locale.ENGLISH,

  /** Lunch post keywords. */
  val keywords: List<Keyword> = listOf(Keyword("lunch", 1)),
)

/** Configuration of Slack reposter. */
@ConstructorBinding
@ConfigurationProperties("lunch.repost.slack")
data class SlackConfig(

  /** URL of Slack's Incoming Webhook that will be used to send lunch messages. */
  val webhookUrl: URL,
)

/** Configuration of retrying failed reposts. */
@ConstructorBinding
@ConfigurationProperties("lunch.repost.retry")
data class RepostRetryConfig(

  /** Interval between consecutive attempts to retry failed reposts. */
  val interval: Duration?,

  /** Base delay in the exponential backoff between consecutive retries of a failed repost. */
  val baseDelay: Duration = Duration.ofMinutes(1),

  /** Max retry attempts for a failed repost. */
  val maxAttempts: Int = 10,
)
