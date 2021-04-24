package dev.pankowski.garcon.domain

import org.slf4j.MDC
import dev.pankowski.garcon.domain.PageId as PageIdData
import dev.pankowski.garcon.domain.SynchronizedPostId as SynchronizedPostIdData

sealed class Mdc(private val key: String) {

  fun <T> having(value: String, f: () -> T): T =
    try {
      MDC.put(key, value)
      f()
    } finally {
      MDC.remove(key)
    }

  object PageId : Mdc("pageId") {
    fun <T> having(pageId: PageIdData, f: () -> T): T =
      having(pageId.value, f)
  }

  object SynchronizedPostId : Mdc("synchronizedPostId") {
    fun <T> having(synchronizedPostId: SynchronizedPostIdData, f: () -> T): T =
      having(synchronizedPostId.value, f)
  }
}
