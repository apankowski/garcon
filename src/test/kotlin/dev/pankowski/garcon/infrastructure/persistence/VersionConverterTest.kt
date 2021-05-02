package dev.pankowski.garcon.infrastructure.persistence

import dev.pankowski.garcon.WithTestName
import dev.pankowski.garcon.domain.Version
import dev.pankowski.garcon.forAll
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe

class VersionConverterTest : FreeSpec({

  data class TestCase(val versionNumber: Int?, val version: Version?) : WithTestName {
    override fun testName() = "converts $versionNumber to $version and back"
  }

  "converts version number to version and back" - {
    forAll(
      TestCase(null, null),
      TestCase(1, Version(1)),
      TestCase(8, Version(8)),
      TestCase(0, Version(0)),
      TestCase(-27, Version(-27)),
    ) { (versionNumber, version) ->
      // given
      val converter = VersionConverter()

      // expect
      converter.from(versionNumber) shouldBe version

      // and
      converter.to(version) shouldBe versionNumber
    }
  }
})
