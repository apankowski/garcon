package dev.pankowski.garcon.api

import dev.pankowski.garcon.domain.Attachment
import dev.pankowski.garcon.domain.ResponseType
import dev.pankowski.garcon.domain.SlackMessage
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.json.JsonTest
import org.springframework.boot.test.json.JacksonTester
import spock.lang.Specification

import static org.assertj.core.api.Assertions.assertThat

@JsonTest
class SlackMessageSerializationTest extends Specification {

  @Autowired
  JacksonTester<SlackMessage> tester

  def "serialized form should be compliant with API documentation"() {
    given:
    def message = new SlackMessage(
      "some message text",
      ResponseType.EPHEMERAL,
      [new Attachment("some attachment text")]
    )

    expect:
    assertThat(tester.write(message)).isEqualToJson(
      """\
      |{
      |  "text": "some message text",
      |  "response_type": "ephemeral",
      |  "attachments": [{
      |    "text": "some attachment text"
      |  }]
      |}""".stripMargin()
    )
  }
}
