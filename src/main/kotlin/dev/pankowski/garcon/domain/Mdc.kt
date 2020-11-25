package dev.pankowski.garcon.domain

import org.slf4j.MDC
import dev.pankowski.garcon.domain.LunchPageId as PageIdValue

sealed class Mdc(private val key: String) {

  fun <T> having(value: String, f: () -> T): T =
    try {
      MDC.put(key, value)
      f()
    } finally {
      MDC.remove(key)
    }

  object PageId : Mdc("pageId") {
    fun <T> having(pageId: PageIdValue, f: () -> T): T =
      having(pageId.value, f)
  }
}
