package dev.pankowski.garcon.infrastructure.facebook

import dev.pankowski.garcon.domain.ExternalId
import io.kotest.core.spec.style.FreeSpec
import io.kotest.datatest.withData
import io.kotest.matchers.shouldBe
import java.net.URI

class FacebookIdExtractorTest : FreeSpec({

  val extractor = FacebookIdExtractor

  data class FacebookIdTestCase(
    val kind: String,
    val uri: URI,
    val id: ExternalId,
  )

  "extracts ID from given URL" - {
    withData<FacebookIdTestCase>(
      { "extracts ${it.kind} ID from '${it.uri}'" },
      FacebookIdTestCase("post", URI("https://www.facebook.com/permalink.php?story_fbid=12345&other=value"), ExternalId("12345")),
      FacebookIdTestCase("post", URI("https://www.facebook.com/permalink.php?story_fbid=pfbid027azZ-_&other=value"), ExternalId("pfbid027azZ-_")),
      FacebookIdTestCase("post", URI("https://www.facebook.com/some.profile/posts/12345?&other=value"), ExternalId("12345")),
      FacebookIdTestCase("post", URI("https://www.facebook.com/some_profile/posts/pfbid031WXB5z5v-abc?&other=value"), ExternalId("pfbid031WXB5z5v-abc")),
      FacebookIdTestCase("photo", URI("https://www.facebook.com/photo/?fbid=12345&set=a.123456789&other=value"), ExternalId("12345")),
      FacebookIdTestCase("photo", URI("https://www.facebook.com/some-profile/photos/a.123456789/12345/?other=value"), ExternalId("12345")),
      FacebookIdTestCase("video", URI("https://www.facebook.com/watch?v=12345&other=value"), ExternalId("12345")),
      FacebookIdTestCase("reel", URI("https://www.facebook.com/reel/12345/?other=value"), ExternalId("12345")),
    ) { testCase ->

      // given
      val result = extractor.extractFacebookId(testCase.uri)

      // expect
      result shouldBe testCase.id
    }
  }
})
