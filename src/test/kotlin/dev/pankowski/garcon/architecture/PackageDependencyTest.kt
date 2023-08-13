package dev.pankowski.garcon.architecture

import com.tngtech.archunit.base.DescribedPredicate.and
import com.tngtech.archunit.base.DescribedPredicate.not
import com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAPackage
import com.tngtech.archunit.core.domain.JavaClass.Predicates.type
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses
import dev.pankowski.garcon.architecture.Packages.Api
import dev.pankowski.garcon.architecture.Packages.Configuration
import dev.pankowski.garcon.architecture.Packages.Domain
import dev.pankowski.garcon.architecture.Packages.Infrastructure
import dev.pankowski.garcon.architecture.Packages.SpringFramework
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.ApplicationEventPublisher
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Isolation
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

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
          not(type(Transactional::class.java)),
          not(type(Isolation::class.java)),
          not(type(Propagation::class.java)),
          not(type(ApplicationEventPublisher::class.java)),
          not(type(EventListener::class.java)),
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
