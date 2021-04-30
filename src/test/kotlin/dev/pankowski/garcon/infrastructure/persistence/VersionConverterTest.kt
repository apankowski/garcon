package dev.pankowski.garcon.infrastructure.persistence

import dev.pankowski.garcon.domain.Version
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.nulls.beNull
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe

class VersionConverterTest : FreeSpec({

  val converter = VersionConverter()

  "converts null to null and back" {
    // expect
    converter.from(null) should beNull()

    // and
    converter.to(null) should beNull()
  }

  "converts from number to version and back" - {
    listOf(
      1 to Version(1),
      8 to Version(8),
      0 to Version(0),
      -27 to Version(-27),
    ).forEach { (versionNumber, version) ->
      "$versionNumber is converted to $version and back" {
        // expect
        converter.from(versionNumber) shouldBe version

        // and
        converter.to(version) shouldBe versionNumber
      }
    }
  }
})
