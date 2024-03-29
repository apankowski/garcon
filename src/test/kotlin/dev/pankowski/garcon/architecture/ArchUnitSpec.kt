package dev.pankowski.garcon.architecture

import com.tngtech.archunit.core.importer.ClassFileImporter
import com.tngtech.archunit.core.importer.ImportOption.Predefined.DO_NOT_INCLUDE_JARS
import com.tngtech.archunit.core.importer.ImportOption.Predefined.DO_NOT_INCLUDE_TESTS
import io.kotest.core.spec.style.FreeSpec

abstract class ArchUnitSpec(body: ArchUnitSpec.() -> Unit = {}) : FreeSpec() {

  companion object {

    val classes = ClassFileImporter()
      .withImportOption(DO_NOT_INCLUDE_JARS)
      .withImportOption(DO_NOT_INCLUDE_TESTS)
      .importPackages("dev.pankowski.garcon")!!
  }

  init {
    this.body()
  }
}

object Packages {

  const val DOMAIN = "..garcon.domain.."
  const val API = "..garcon.api.."
  const val INFRASTRUCTURE = "..garcon.infrastructure.."
  const val CONFIGURATION = "..garcon.configuration.."
  const val SPRING_FRAMEWORK = "..springframework.."
}
