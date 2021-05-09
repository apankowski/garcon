package dev.pankowski.garcon.domain

import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe

class VersionTest : FreeSpec({

  "first version is 1" {
    // given
    val version = Version.first()

    // expect
    version.number shouldBe 1
  }

  "next version is incremented by 1" {
    // given
    val version = Version(2)

    // expect
    version.next().number shouldBe 3
  }
})
