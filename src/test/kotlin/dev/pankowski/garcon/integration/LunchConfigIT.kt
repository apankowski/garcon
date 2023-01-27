package dev.pankowski.garcon.integration

import dev.pankowski.garcon.domain.*
import io.kotest.matchers.shouldBe
import java.net.URL
import java.time.Duration

class LunchConfigIT(
  config: LunchConfig,
  syncConfig: SyncConfig,
  clientConfig: ClientConfig,
  postConfig: PostConfig,
  slackConfig: SlackConfig,
  repostRetryConfig: RepostRetryConfig,
) : CommonIT({

  "lunch config is set based on configuration properties" {
    // expect
    config shouldBe LunchConfig(
      pages = listOf(
        PageConfig(PageId("PŻPS"), URL("http://localhost:9876/lunch/facebook/pzps/posts")),
        PageConfig(PageId("WegeGuru"), URL("http://localhost:9876/lunch/facebook/wegeguru/posts"))
      ),
    )
  }

  "sync config is set based on configuration properties" {
    // expect
    syncConfig shouldBe SyncConfig(
      interval = null,
    )
  }

  "client config is set based on configuration properties" {
    // expect
    clientConfig shouldBe ClientConfig(
      userAgent = "Some user agent",
      timeout = Duration.parse("PT100S"),
      retryCount = 1,
      retryMinJitter = Duration.ofSeconds(1),
      retryMaxJitter = Duration.ofSeconds(4),
    )
  }

  "post config is set based on configuration properties" {
    // expect
    postConfig shouldBe PostConfig(
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
      signingSecret = "some-signing-secret",
    )
  }

  "retry config is set based on configuration properties" {
    // expect
    repostRetryConfig shouldBe RepostRetryConfig(
      interval = null,
      baseDelay = Duration.ofSeconds(10),
      maxAttempts = 5,
    )
  }
})
