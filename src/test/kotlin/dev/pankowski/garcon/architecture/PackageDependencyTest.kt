package dev.pankowski.garcon.architecture

import com.tngtech.archunit.base.DescribedPredicate.*
import com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAPackage
import com.tngtech.archunit.core.domain.JavaClass.Predicates.type
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses
import dev.pankowski.garcon.architecture.Packages.Api
import dev.pankowski.garcon.architecture.Packages.Configuration
import dev.pankowski.garcon.architecture.Packages.Domain
import dev.pankowski.garcon.architecture.Packages.Infrastructure
import dev.pankowski.garcon.architecture.Packages.SpringFramework
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.stereotype.Component

class PackageDependencyTest : ArchUnitSpec({

  "domain does not depend on other layers" {
    // given
    val rule = noClasses()
      .that().resideInAPackage(Domain)
      .should().dependOnClassesThat().resideInAnyPackage(Api, Infrastructure, Configuration)

    // expect
    rule.check(classes)
  }

  "domain does not depend on framework (with two exceptions)" {
    // given
    val rule = noClasses()
      .that().resideInAPackage(Domain)
      .should().dependOnClassesThat(
        and(
          resideInAPackage(SpringFramework),
          not(type(Component::class.java)),
          not(type(ConfigurationProperties::class.java)),
        )
      )

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
