package dev.pankowski.garcon.architecture

import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Configuration
import org.springframework.web.bind.annotation.RestController

class ComponentNamingTest : ArchUnitSpec({

  "controllers have 'Controller' suffix" {
    // given
    val rule = classes()
      .that().areAnnotatedWith(RestController::class.java)
      .should().haveSimpleNameEndingWith("Controller")

    // expect
    rule.check(classes)
  }

  "framework configuration classes have 'Configuration' suffix" {
    // given
    val rule = classes()
      .that().areAnnotatedWith(Configuration::class.java)
      .should().haveSimpleNameEndingWith("Configuration")

    // expect
    rule.check(classes)
  }

  "application configuration classes have 'Config' suffix" {
    // given
    val rule = classes()
      .that().areAnnotatedWith(ConfigurationProperties::class.java)
      .should().haveSimpleNameEndingWith("Config")

    // expect
    rule.check(classes)
  }
})
