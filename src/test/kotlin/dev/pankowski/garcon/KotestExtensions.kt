package dev.pankowski.garcon

import io.kotest.core.datatest.forAll
import io.kotest.core.spec.style.scopes.ContainerContext

interface WithTestName {
  fun testName(): String
}

suspend fun <T : WithTestName> ContainerContext.forAll(vararg ts: T, test: suspend (T) -> Unit) =
  forAll(ts.map { it.testName() to it }, test)
