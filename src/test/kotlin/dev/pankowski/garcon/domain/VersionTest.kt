package dev.pankowski.garcon.domain

import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe

class VersionTest : FreeSpec({

  "first version should be 1" {
    // given
    val version = Version.first()

    // expect
    version.value shouldBe 1
  }

  "next version should increment by 1" {
    // given
    val version = Version(2)

    // expect
    version.next().value shouldBe 3
  }
})
