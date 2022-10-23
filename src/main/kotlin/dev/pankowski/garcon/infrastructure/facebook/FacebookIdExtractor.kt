package dev.pankowski.garcon.infrastructure.facebook

import com.google.common.annotations.VisibleForTesting
import dev.pankowski.garcon.domain.ExternalId
import org.springframework.web.util.UriComponentsBuilder
import java.net.URI

object FacebookIdExtractor {

  @VisibleForTesting
  fun extractFacebookId(uri: URI): ExternalId? {
    // Regular post
    val postPathRegex = "^/?permalink\\.php".toRegex()
    postPathRegex.find(uri.path)?.let {
      return UriComponentsBuilder.fromUri(uri).build()
        .queryParams["story_fbid"]
        ?.firstOrNull()
        ?.let(::ExternalId)
    }

    // Regular post - alternative version
    val altPostPathRegex = "^/?[^/]+/posts/([0-9a-zA-Z_-]+)/?".toRegex()
    altPostPathRegex.find(uri.path)?.let {
      return ExternalId(it.groupValues[1])
    }

    // Photo
    val photoPathRegex = "^/?[^/]+/photos/[0-9a-zA-Z._-]+/([0-9a-zA-Z_-]+)/?".toRegex()
    photoPathRegex.find(uri.path)?.let {
      return ExternalId(it.groupValues[1])
    }

    // Photo - alternative version
    val altPhotoPathRegex = "^/?photo/?".toRegex()
    altPhotoPathRegex.find(uri.path)?.let {
      return UriComponentsBuilder.fromUri(uri).build()
        .queryParams["fbid"]
        ?.firstOrNull()
        ?.let(::ExternalId)
    }

    // Video
    val videoPathRegex = "^/?watch/?".toRegex()
    videoPathRegex.find(uri.path)?.let {
      return UriComponentsBuilder.fromUri(uri).build()
        .queryParams["v"]
        ?.firstOrNull()
        ?.let(::ExternalId)
    }

    // Reel
    val reelPathRegex = "^/?reel/([0-9a-zA-Z_-]+)/?".toRegex()
    reelPathRegex.find(uri.path)?.let {
      return ExternalId(it.groupValues[1])
    }

    // Dunno
    return null
  }
}
