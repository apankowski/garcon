package dev.pankowski.garcon.architecture

import com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices

class ClassDependencyTest : ArchUnitSpec({

  "there are no cyclic dependencies in top packages" {
    // given
    val rule = slices()
      .matching("dev.pankowski.garcon.(*)..")
      .should().beFreeOfCycles()

    // expect
    rule.check(classes)
  }
})
