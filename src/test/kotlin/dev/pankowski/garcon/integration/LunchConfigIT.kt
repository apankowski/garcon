package dev.pankowski.garcon.integration

import dev.pankowski.garcon.domain.*
import io.kotest.matchers.shouldBe
import java.net.URL
import java.time.Duration

class LunchConfigIT(
  config: LunchConfig,
  syncConfig: LunchSyncConfig,
  clientConfig: LunchClientConfig,
  postConfig: LunchPostConfig,
  slackConfig: SlackConfig,
  retryConfig: RetryConfig,
) : CommonIT({

  "lunch config is set based on configuration properties" {
    // expect
    config shouldBe LunchConfig(
      pages = listOf(
        LunchPageConfig(PageId("PŻPS"), URL("http://localhost:9876/lunch/facebook/pzps/posts")),
        LunchPageConfig(PageId("WegeGuru"), URL("http://localhost:9876/lunch/facebook/wegeguru/posts"))
      ),
    )
  }

  "sync config is set based on configuration properties" {
    // expect
    syncConfig shouldBe LunchSyncConfig(
      interval = null,
    )
  }

  "client config is set based on configuration properties" {
    // expect
    clientConfig shouldBe LunchClientConfig(
      userAgent = "Some user agent",
      timeout = Duration.parse("PT100S"),
      retries = 1,
      retryMinJitter = Duration.ofSeconds(1),
      retryMaxJitter = Duration.ofSeconds(4),
    )
  }

  "post config is set based on configuration properties" {
    // expect
    postConfig shouldBe LunchPostConfig(
      locale = PolishLocale,
      keywords = listOf(
        Keyword("lunch", 1),
        Keyword("lunchowa", 2),
      ),
    )
  }

  "slack config is set based on configuration properties" {
    // expect
    slackConfig shouldBe SlackConfig(
      webhookUrl = URL("http://localhost:9876/lunch/slack/webhook"),
    )
  }

  "retry config is set based on configuration properties" {
    // expect
    retryConfig shouldBe RetryConfig(
      interval = null,
      baseDelay = Duration.ofSeconds(10),
      maxAttempts = 5,
    )
  }
})
