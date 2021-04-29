package dev.pankowski.garcon.api

import dev.pankowski.garcon.domain.Attachment
import dev.pankowski.garcon.domain.ResponseType
import dev.pankowski.garcon.domain.SlackMessage
import io.kotest.core.spec.style.FreeSpec
import io.kotest.extensions.spring.SpringExtension
import org.assertj.core.api.AssertionsForInterfaceTypes.assertThat
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.json.JsonTest
import org.springframework.boot.test.json.JacksonTester

@JsonTest
class SlackMessageSerializationTest : FreeSpec() {

  override fun extensions() = listOf(SpringExtension)

  @Autowired
  lateinit var tester: JacksonTester<SlackMessage>

  init {
    "serialized form should be compliant with API documentation" {
      // given
      val message = SlackMessage(
        "some message text",
        ResponseType.EPHEMERAL,
        listOf(Attachment("some attachment text"))
      )

      // expect
      assertThat(tester.write(message)).isEqualToJson(
        """
        |{
        |  "text": "some message text",
        |  "response_type": "ephemeral",
        |  "attachments": [{
        |    "text": "some attachment text"
        |  }]
        |}
        """.trimMargin()
      )
    }
  }
}
