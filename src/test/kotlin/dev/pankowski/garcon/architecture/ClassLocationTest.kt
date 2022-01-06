package dev.pankowski.garcon.architecture

import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes
import org.springframework.context.annotation.Configuration
import org.springframework.web.bind.annotation.RestController

class ClassLocationTest : ArchUnitSpec({

  "controllers are in API package" {
    // given
    val rule = classes()
      .that().areAnnotatedWith(RestController::class.java)
      .should().resideInAPackage(Packages.Api)

    // expect
    rule.check(classes)
  }

  "configuration classes are in configuration package" {
    // given
    val rule = classes()
      .that().areAnnotatedWith(Configuration::class.java)
      .should().resideInAPackage(Packages.Configuration)

    // expect
    rule.check(classes)
  }
})
