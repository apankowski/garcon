package dev.pankowski.garcon.architecture

import com.tngtech.archunit.base.DescribedPredicate.and
import com.tngtech.archunit.base.DescribedPredicate.not
import com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAPackage
import com.tngtech.archunit.core.domain.JavaClass.Predicates.type
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses
import dev.pankowski.garcon.architecture.Packages.API
import dev.pankowski.garcon.architecture.Packages.CONFIGURATION
import dev.pankowski.garcon.architecture.Packages.DOMAIN
import dev.pankowski.garcon.architecture.Packages.INFRASTRUCTURE
import dev.pankowski.garcon.architecture.Packages.SPRING_FRAMEWORK
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
      .that().resideInAPackage(DOMAIN)
      .should().dependOnClassesThat().resideInAnyPackage(API, INFRASTRUCTURE, CONFIGURATION)

    // expect
    rule.check(classes)
  }

  "domain does not depend on framework (with some exceptions)" {
    // given
    val rule = noClasses()
      .that().resideInAPackage(DOMAIN)
      .should().dependOnClassesThat(
        and(
          resideInAPackage(SPRING_FRAMEWORK),
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
      .that().resideInAPackage(API)
      .should().dependOnClassesThat().resideInAnyPackage(INFRASTRUCTURE, CONFIGURATION)

    // expect
    rule.check(classes)
  }

  "infrastructure does not depend on API or configuration" {
    // given
    val rule = noClasses()
      .that().resideInAPackage(INFRASTRUCTURE)
      .should().dependOnClassesThat().resideInAnyPackage(API, CONFIGURATION)

    // expect
    rule.check(classes)
  }
})
