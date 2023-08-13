package dev.pankowski.garcon.domain

import io.kotest.core.spec.style.FreeSpec
import io.mockk.mockk
import io.mockk.verify

class RepostingSynchronizationEventHandlerTest : FreeSpec({

  "reposts new lunch post" {
    // given
    val post = someSynchronizedPost(classification = Classification.LUNCH_POST)
    val event = SynchronizedPostCreatedEvent(post)

    val reposter = mockk<Reposter>(relaxUnitFun = true)
    val handler = RepostingSynchronizationEventHandler(reposter)

    // when
    handler.onSynchronizedPostCreated(event)

    // then
    verify { reposter.repost(post) }
  }

  "reposts post that became a lunch post" {
    // given
    val oldPost = someSynchronizedPost(classification = Classification.REGULAR_POST)
    val newPost = someSynchronizedPost(classification = Classification.LUNCH_POST)
    val event = SynchronizedPostUpdatedEvent(oldPost, newPost)

    val reposter = mockk<Reposter>(relaxUnitFun = true)
    val handler = RepostingSynchronizationEventHandler(reposter)

    // when
    handler.onSynchronizedPostUpdated(event)

    // then
    verify { reposter.repost(newPost) }
  }
})
