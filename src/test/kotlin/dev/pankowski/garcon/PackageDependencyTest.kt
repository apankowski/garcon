package dev.pankowski.garcon

import com.tngtech.archunit.core.importer.ClassFileImporter
import com.tngtech.archunit.core.importer.ImportOption.Predefined.DO_NOT_INCLUDE_TESTS
import com.tngtech.archunit.lang.ArchRule
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses
import io.kotest.core.spec.style.FreeSpec

class PackageDependencyTest : FreeSpec({

  val domainPackage = "..domain.."
  val apiPackage = "..api.."
  val infrastructurePackage = "..infrastructure.."
  val configurationPackage = "..configuration.."
  val springPackage = "..springframework.."

  val classes = ClassFileImporter()
    .withImportOption(DO_NOT_INCLUDE_TESTS)
    .importPackages("dev.pankowski.garcon")

  "domain does not access other layers" {
    // given
    val rule: ArchRule = noClasses()
      .that()
      .resideInAPackage(domainPackage)
      .should()
      .accessClassesThat()
      .resideInAnyPackage(apiPackage, infrastructurePackage, configurationPackage, springPackage)

    // expect
    rule.check(classes)
  }

  "API does not depend on infrastructure or configuration" {
    // given
    val rule: ArchRule = noClasses()
      .that()
      .resideInAPackage(apiPackage)
      .should()
      .dependOnClassesThat()
      .resideInAnyPackage(infrastructurePackage, configurationPackage)

    // expect
    rule.check(classes)
  }

  "infrastructure does not depend on API or configuration" {
    // given
    val rule: ArchRule = noClasses()
      .that()
      .resideInAPackage(infrastructurePackage)
      .should()
      .dependOnClassesThat()
      .resideInAnyPackage(apiPackage, configurationPackage)

    // expect
    rule.check(classes)
  }
})
