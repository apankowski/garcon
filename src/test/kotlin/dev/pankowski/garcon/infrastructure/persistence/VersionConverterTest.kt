package dev.pankowski.garcon.infrastructure.persistence

import dev.pankowski.garcon.domain.Version
import io.kotest.core.spec.style.FreeSpec
import io.kotest.datatest.WithDataTestName
import io.kotest.datatest.withData
import io.kotest.matchers.shouldBe

class VersionConverterTest : FreeSpec({

  data class TestCase(val versionNumber: Int?, val version: Version?) : WithDataTestName {
    override fun dataTestName() = "converts $versionNumber to $version and back"
  }

  "converts version number to version and back" - {
    withData(
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
