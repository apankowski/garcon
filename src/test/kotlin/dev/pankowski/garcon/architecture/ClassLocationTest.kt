package dev.pankowski.garcon.architecture

import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes
import dev.pankowski.garcon.architecture.Packages.API
import dev.pankowski.garcon.architecture.Packages.CONFIGURATION
import org.springframework.context.annotation.Configuration
import org.springframework.web.bind.annotation.RestController

class ClassLocationTest : ArchUnitSpec({

  "controllers are in API package" {
    // given
    val rule = classes()
      .that().areAnnotatedWith(RestController::class.java)
      .should().resideInAPackage(API)

    // expect
    rule.check(classes)
  }

  "configuration classes are in configuration or API package" {
    // given
    val rule = classes()
      .that().areAnnotatedWith(Configuration::class.java)
      .should().resideInAnyPackage(CONFIGURATION, API)

    // expect
    rule.check(classes)
  }
})
