package dev.pankowski.garcon.configuration

import dev.pankowski.garcon.domain.ExternalId
import dev.pankowski.garcon.domain.LunchClientConfig
import dev.pankowski.garcon.domain.LunchConfig
import dev.pankowski.garcon.domain.LunchPageId
import dev.pankowski.garcon.domain.Post
import org.springframework.boot.web.client.RestTemplateBuilder
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.test.web.client.MockRestServiceServer
import spock.lang.Specification
import spock.lang.Subject

import java.time.Duration
import java.time.Instant

import static org.springframework.test.web.client.match.MockRestRequestMatchers.content
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus

// TODO: Use Kotlin & Junit 5 (JUnit 4 doesn't seem to be compatible with Kotlin)?
// https://spring.io/guides/tutorials/spring-boot-kotlin/
class RestTemplateSlackReposterTest extends Specification {

  def lunchConfig = new LunchConfig(
    new URL("https://slack/webhook"),
    Duration.ofMinutes(5),
    new LunchClientConfig("Some User Agent", Duration.ofSeconds(5)),
    [],
  )

  @Subject
  def repostingClient = new RestTemplateSlackReposter(lunchConfig, new RestTemplateBuilder())

  def mockServer = MockRestServiceServer.createServer(repostingClient.restTemplate)

  def "should repost using incoming webhook"() {
    given:
    def post = new Post(
      new ExternalId("SomeId"),
      URI.create("https://www.facebook.com/post"),
      Instant.now(),
      "Some content"
    )

    def pageId = new LunchPageId("SomePageId")

    mockServer.expect(requestTo(lunchConfig.slackWebhookUrl.toURI()))
      .andExpect(method(HttpMethod.POST))
      .andExpect(content().contentType(MediaType.APPLICATION_JSON))
      .andExpect(content().json(
        """\
        |{
        |"text":"New <https://www.facebook.com/post|lunch post> from SomePageId :tada:\\n\\n>>>Some content"
        |}""".stripMargin(),
        false
      ))
      .andRespond(withStatus(HttpStatus.OK))

    when:
    repostingClient.repost(post, pageId)

    then:
    mockServer.verify()
  }
}
