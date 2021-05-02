package dev.pankowski.garcon

import io.kotest.core.datatest.forAll
import io.kotest.core.spec.style.scopes.ContainerScope

interface WithTestName {
  fun testName(): String
}

suspend fun <T : WithTestName> ContainerScope.forAll(vararg ts: T, test: suspend (T) -> Unit) =
  forAll(ts.map { it.testName() to it }, test)
