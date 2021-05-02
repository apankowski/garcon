package dev.pankowski.garcon

import io.kotest.core.datatest.forAll
import io.kotest.core.spec.style.scopes.ContainerScope
import kotlin.reflect.KFunction
import kotlin.reflect.KParameter
import kotlin.reflect.KVisibility
import kotlin.reflect.full.declaredFunctions

interface WithTestName {
  fun testName(): String
}

suspend fun <T : WithTestName> ContainerScope.forAll(vararg ts: T, test: suspend (T) -> Unit) =
  forAll(ts.map { it.testName() to it }, test)
