package dev.pankowski.garcon.infrastructure

import dev.pankowski.garcon.domain.PageName
import dev.pankowski.garcon.domain.somePost
import dev.pankowski.garcon.domain.someSlackConfig
import io.kotest.core.spec.style.FreeSpec
import org.springframework.boot.web.client.RestTemplateBuilder
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.*
import org.springframework.test.web.client.response.MockRestResponseCreators.withStatus
import java.net.URL

class RestTemplateSlackReposterTest : FreeSpec({

  "synchronized post is reposted via incoming webhook" {
    // given
    val pageName = PageName("Some page name")
    val post = somePost(
      url = URL("https://www.facebook.com/post"),
      content = "Some post content",
    )

    val slackConfig = someSlackConfig(webhookUrl = URL("https://slack/webhook"))

    val repostingClient = RestTemplateSlackReposter(slackConfig, RestTemplateBuilder())
    val mockServer = MockRestServiceServer.createServer(repostingClient.restTemplate)

    mockServer.expect(requestTo(slackConfig.webhookUrl.toURI()))
      .andExpect(method(HttpMethod.POST))
      .andExpect(content().contentType(MediaType.APPLICATION_JSON))
      .andExpect(
        content().json(
          """
          |{
          |"text":"New <https://www.facebook.com/post|lunch post> from *Some page name* :tada:\n\n>>>Some post content"
          |}
          """.trimMargin()
        )
      )
      .andRespond(withStatus(HttpStatus.OK))

    // when
    repostingClient.repost(post, pageName)

    // then
    mockServer.verify()
  }
})
