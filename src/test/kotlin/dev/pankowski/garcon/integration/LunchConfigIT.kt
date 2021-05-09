package dev.pankowski.garcon.integration

import dev.pankowski.garcon.domain.*
import io.kotest.assertions.assertSoftly
import io.kotest.matchers.nulls.beNull
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import java.net.URL
import java.time.Duration

class LunchConfigIT(
  config: LunchConfig,
  syncConfig: LunchSyncConfig,
  clientConfig: LunchClientConfig,
  postConfig: LunchPostConfig,
  slackConfig: SlackConfig,
) : CommonIT({

  "lunch config is set based on configuration properties" {
    // expect
    config.pages shouldBe listOf(
      LunchPageConfig(PageId("PŻPS"), URL("http://localhost:9876/lunch/facebook/pzps/posts")),
      LunchPageConfig(PageId("WegeGuru"), URL("http://localhost:9876/lunch/facebook/wegeguru/posts"))
    )
  }

  "sync config is set based on configuration properties" {
    // expect
    syncConfig.interval should beNull()
  }

  "client config is set based on configuration properties" {
    // expect
    assertSoftly(clientConfig) {
      userAgent shouldBe "Some user agent"
      timeout shouldBe Duration.parse("PT100S")
    }
  }

  "post config is set based on configuration properties" {
    // expect
    assertSoftly(postConfig) {
      locale shouldBe PolishLocale
      keywords shouldBe listOf(
        Keyword("lunch", 1),
        Keyword("lunchowa", 2),
      )
    }
  }

  "slack config is set based on configuration properties" {
    // expect
    slackConfig.webhookUrl shouldBe URL("http://localhost:9876/lunch/slack/webhook")
  }
})
