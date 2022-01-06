package dev.pankowski.garcon.architecture

import com.tngtech.archunit.base.DescribedPredicate
import com.tngtech.archunit.core.domain.JavaClass
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.fields
import com.tngtech.archunit.library.GeneralCodingRules
import org.slf4j.Logger

class CodingRulesTest : ArchUnitSpec({

  fun withSimpleNameEqual(simpleName: String) =
    object : DescribedPredicate<JavaClass>("simple name equal '%s'".format(simpleName)) {
      override fun apply(input: JavaClass) = input.simpleName == simpleName
    }

  "loggers should be private, final, have 'log' name and be from SLF4J" {
    // given
    val rule = fields()
      // Matches loggers from SLF4J, Log4J, JUL, Logback, etc.
      .that().haveRawType(withSimpleNameEqual("Logger"))
      // Matches log, logger, etc.
      .or().haveNameStartingWith("log")
      .should().bePrivate()
      .andShould().beFinal()
      .andShould().haveName("log")
      .andShould().haveRawType(Logger::class.java)

    // expect
    rule.check(classes)
  }

  "general coding rules" {
    // expect
    GeneralCodingRules.NO_CLASSES_SHOULD_ACCESS_STANDARD_STREAMS.check(classes)
    GeneralCodingRules.NO_CLASSES_SHOULD_THROW_GENERIC_EXCEPTIONS.check(classes)
    GeneralCodingRules.NO_CLASSES_SHOULD_USE_JODATIME.check(classes)
    GeneralCodingRules.NO_CLASSES_SHOULD_USE_FIELD_INJECTION.check(classes)
  }
})
