package dev.pankowski.garcon.integration

import dev.pankowski.garcon.domain.Keyword
import dev.pankowski.garcon.domain.LunchClientConfig
import dev.pankowski.garcon.domain.LunchConfig
import dev.pankowski.garcon.domain.LunchPageConfig
import dev.pankowski.garcon.domain.PageId
import dev.pankowski.garcon.domain.LunchPostConfig
import org.springframework.beans.factory.annotation.Autowired

import java.time.Duration

class LunchConfigIT extends CommonIT {

  @Autowired
  LunchConfig config

  @Autowired
  LunchClientConfig clientConfig

  @Autowired
  LunchPostConfig postConfig

  def "lunch config should be set based on configuration properties"() {
    expect:
    config.slackWebhookUrl == new URL('http://localhost:9876/lunch/slack/webhook')
    config.syncInterval == null
    config.pages == [
      new LunchPageConfig(new PageId("PŻPS"), new URL("http://localhost:9876/lunch/facebook/pzps/posts")),
      new LunchPageConfig(new PageId("WegeGuru"), new URL("http://localhost:9876/lunch/facebook/wegeguru/posts"))
    ]
  }

  def "client config should be set based on configuration properties"() {
    expect:
    clientConfig.userAgent == "Some user agent"
    clientConfig.timeout == Duration.parse('PT100S')
  }

  def "post config should be set based on configuration properties"() {
    expect:
    postConfig.locale == new Locale("pl", "PL")
    postConfig.keywords == [
      new Keyword("lunch", 1),
      new Keyword("lunchowa", 2),
    ]
  }
}
