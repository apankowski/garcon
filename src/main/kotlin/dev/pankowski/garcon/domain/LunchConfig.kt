package dev.pankowski.garcon.domain

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.ConstructorBinding
import java.net.URL
import java.time.Duration
import java.util.*

/**
 * Configuration of lunch feature of the bot.
 */
@ConstructorBinding
@ConfigurationProperties("lunch")
data class LunchConfig(

  /**
   * URL of Slack's Incoming Webhook that will be used to send lunch messages.
   */
  val slackWebhookUrl: URL,

  /**
   * Interval between consecutive checks for lunch posts.
   */
  val syncInterval: Duration?,

  /**
   * Lunch pages.
   */
  val pages: List<LunchPageConfig> = emptyList(),
)

/**
 * Configuration of web client used to fetch lunch pages.
 */
@ConstructorBinding
@ConfigurationProperties("lunch.client")
data class LunchClientConfig(

  /**
   * User agent by which the client identifies itself when fetching lunch pages.
   */
  val userAgent: String = "Mozilla/5.0 (X11; Ubuntu; Linux x86_64; rv:80.0) Gecko/20100101 Firefox/80.0",

  /**
   * Max time to wait for the lunch page to be fetched.
   */
  val timeout: Duration = Duration.ofSeconds(10),
)

/**
 * Configuration of a single lunch page to synchronize.
 */
data class LunchPageConfig(

  /**
   * ID of Facebook post page containing lunch posts.
   */
  val id: LunchPageId,

  /**
   * URL of Facebook post page containing lunch posts.
   */
  val url: URL,
)

data class LunchPageId(val value: String)

/**
 * Configuration related to lunch post classification.
 */
@ConstructorBinding
@ConfigurationProperties("lunch.post")
data class LunchPostConfig(

  val locale: Locale = Locale.ENGLISH,
)
