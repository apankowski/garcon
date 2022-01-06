package dev.pankowski.garcon.architecture

import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses
import dev.pankowski.garcon.architecture.Packages.Api
import dev.pankowski.garcon.architecture.Packages.Configuration
import dev.pankowski.garcon.architecture.Packages.Domain
import dev.pankowski.garcon.architecture.Packages.Infrastructure
import dev.pankowski.garcon.architecture.Packages.Spring

class PackageDependencyTest : ArchUnitSpec({

  "domain does not access other layers" {
    // given
    val rule = noClasses()
      .that().resideInAPackage(Domain)
      .should().accessClassesThat().resideInAnyPackage(Api, Infrastructure, Configuration, Spring)

    // expect
    rule.check(classes)
  }

  "API does not depend on infrastructure or configuration" {
    // given
    val rule = noClasses()
      .that().resideInAPackage(Api)
      .should().dependOnClassesThat().resideInAnyPackage(Infrastructure, Configuration)

    // expect
    rule.check(classes)
  }

  "infrastructure does not depend on API or configuration" {
    // given
    val rule = noClasses()
      .that().resideInAPackage(Infrastructure)
      .should().dependOnClassesThat().resideInAnyPackage(Api, Configuration)

    // expect
    rule.check(classes)
  }
})
