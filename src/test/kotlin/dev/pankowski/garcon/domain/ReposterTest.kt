package dev.pankowski.garcon.domain

import dev.pankowski.garcon.infrastructure.persistence.InMemorySynchronizedPostRepository
import dev.pankowski.garcon.infrastructure.persistence.someFailedRepost
import dev.pankowski.garcon.infrastructure.persistence.someSuccessRepost
import io.kotest.core.spec.style.FreeSpec
import io.kotest.datatest.withData
import io.kotest.matchers.date.between
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.*

class ReposterTest : FreeSpec({

  "reposts pending appearing lunch posts" {
    // given
    val synchronizedPost = someSynchronizedPost(classification = Classification.LUNCH_POST, repost = Repost.Pending)

    val repository = spyk(InMemorySynchronizedPostRepository()).apply { put(synchronizedPost) }
    val slack = mockk<Slack> { every { repost(synchronizedPost.post, synchronizedPost.pageName) } returns mockk() }
    val reposter = Reposter(mockk(), repository, slack)

    // when
    val before = now()
    reposter.repost(synchronizedPost)
    val after = now()

    // then
    val repostSlot = slot<Repost>()
    verify { repository.updateExisting(any(), any(), capture(repostSlot)) }

    with(repostSlot.captured) {
      shouldBeInstanceOf<Repost.Success>()
      repostedAt shouldBe between(before, after)
    }
  }

  "retries failed reposts" - {

    withData<Repost>(
      { "doesn't retry ${it::class.simpleName} repost" },
      Repost.Skip,
      someSuccessRepost(),
    ) { r ->

      // given
      val post = someSynchronizedPost(repost = r)
      val retryConfig = someRepostRetryConfig()

      val repository = mockk<SynchronizedPostRepository>()
      val slack = mockk<Slack>()
      val reposter = Reposter(retryConfig, repository, slack)

      // when
      reposter.repost(post)

      // then
      verify {
        repository wasNot Called
        slack wasNot Called
      }
    }

    withData<Repost>(
      { "retries ${it::class.simpleName} repost" },
      Repost.Pending,
      someFailedRepost(),
    ) { r ->

      // given
      val post = someSynchronizedPost(repost = r)
      val retryConfig = someRepostRetryConfig()

      val repository = mockk<SynchronizedPostRepository> {
        every { updateExisting(post.id, post.version, any()) } returns Unit
      }

      val slack = mockk<Slack> { every { repost(post.post, any()) } returns mockk() }
      val reposter = Reposter(retryConfig, repository, slack)

      // when
      val before = now()
      reposter.repost(post)
      val after = now()

      // then
      val repostSlot = slot<Repost>()
      verify {
        repository.updateExisting(any(), any(), capture(repostSlot))
        slack.repost(post.post, post.pageName)
      }

      with(repostSlot.captured) {
        shouldBeInstanceOf<Repost.Success>()
        repostedAt shouldBe between(before, after)
      }
    }

    data class FailedRetryTestCase(val repost: Repost, val newAttempts: Int)

    withData<FailedRetryTestCase>(
      { "increments number of attempts after failed retry of ${it.repost.javaClass.simpleName} repost" },
      FailedRetryTestCase(Repost.Pending, 1),
      FailedRetryTestCase(someFailedRepost(attempts = 2), 3),
    ) { (r, newAttempts) ->

      // given
      val post = someSynchronizedPost(repost = r)
      val retryConfig = someRepostRetryConfig()

      val repository = mockk<SynchronizedPostRepository> {
        every { updateExisting(post.id, post.version, any()) } returns Unit
      }
      val slack = mockk<Slack> {
        every { repost(post.post, any()) } throws RuntimeException("something went wrong")
      }
      val reposter = Reposter(retryConfig, repository, slack)

      // when
      val before = now()
      reposter.repost(post)
      val after = now()

      // then
      val repostSlot = slot<Repost>()
      verify { repository.updateExisting(any(), any(), capture(repostSlot)) }

      with(repostSlot.captured) {
        shouldBeInstanceOf<Repost.Failed>()
        attempts shouldBe newAttempts
        lastAttemptAt shouldBe between(before, after)
        nextAttemptAt shouldBe lastAttemptAt + Repost.exponentialBackoff(retryConfig.baseDelay, newAttempts)
      }
    }
  }
})
