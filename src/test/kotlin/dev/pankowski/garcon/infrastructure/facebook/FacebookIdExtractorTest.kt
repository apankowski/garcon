package dev.pankowski.garcon.infrastructure.facebook

import dev.pankowski.garcon.domain.FacebookPostId
import dev.pankowski.garcon.domain.toURL
import io.kotest.core.spec.style.FreeSpec
import io.kotest.datatest.withData
import io.kotest.matchers.shouldBe
import java.net.URL

class FacebookIdExtractorTest : FreeSpec({

  val extractor = FacebookIdExtractor

  data class FacebookIdTestCase(
    val kind: String,
    val url: URL,
    val id: FacebookPostId,
  )

  "extracts ID from given URL" - {
    withData<FacebookIdTestCase>(
      { "extracts ${it.kind} ID from '${it.url}'" },
      FacebookIdTestCase("post", toURL("https://www.facebook.com/permalink.php?story_fbid=12345&other=value"), FacebookPostId("12345")),
      FacebookIdTestCase("post", toURL("https://www.facebook.com/permalink.php?story_fbid=pfbid027azZ-_&other=value"), FacebookPostId("pfbid027azZ-_")),
      FacebookIdTestCase("post", toURL("https://www.facebook.com/some.profile/posts/12345?&other=value"), FacebookPostId("12345")),
      FacebookIdTestCase("post", toURL("https://www.facebook.com/some_profile/posts/pfbid031WXB5z5v-abc?&other=value"), FacebookPostId("pfbid031WXB5z5v-abc")),
      FacebookIdTestCase("photo", toURL("https://www.facebook.com/photo/?fbid=12345&set=a.123456789&other=value"), FacebookPostId("12345")),
      FacebookIdTestCase("photo", toURL("https://www.facebook.com/some-profile/photos/a.123456789/12345/?other=value"), FacebookPostId("12345")),
      FacebookIdTestCase("video", toURL("https://www.facebook.com/watch?v=12345&other=value"), FacebookPostId("12345")),
      FacebookIdTestCase("reel", toURL("https://www.facebook.com/reel/12345/?other=value"), FacebookPostId("12345")),
    ) { testCase ->

      // given
      val result = extractor.extractFacebookId(testCase.url)

      // expect
      result shouldBe testCase.id
    }
  }
})
