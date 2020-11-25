package dev.pankowski.garcon.integration

import dev.pankowski.garcon.domain.LunchClientConfig
import dev.pankowski.garcon.domain.LunchConfig
import dev.pankowski.garcon.domain.LunchPageConfig
import dev.pankowski.garcon.domain.LunchPageId
import org.springframework.beans.factory.annotation.Autowired
import spock.lang.Subject

import java.time.Duration

class LunchConfigIT extends CommonIT {

  @Autowired
  @Subject
  LunchConfig config

  def "should return expected values based on configuration properties"() {
    expect:
    config.slackWebhookUrl == new URL('http://localhost:9876/lunch/slack/webhook')
    config.syncInterval == null
    config.client == new LunchClientConfig("Some user agent", Duration.parse('PT100S'))
    config.pages == [
      new LunchPageConfig(new LunchPageId("PŻPS"), new URL("http://localhost:9876/lunch/facebook/pzps/posts")),
      new LunchPageConfig(new LunchPageId("WegeGuru"), new URL("http://localhost:9876/lunch/facebook/wegeguru/posts"))
    ]
  }
}
