package dev.pankowski.garcon.domain

import org.slf4j.MDC
import dev.pankowski.garcon.domain.PageId as PageIdData
import dev.pankowski.garcon.domain.SynchronizedPostId as SynchronizedPostIdData

class Mdc {

  companion object {
    private fun <T> extendedWith(key: String, value: String, f: () -> T): T =
      try {
        MDC.put(key, value)
        f()
      } finally {
        MDC.remove(key)
      }

    fun <T> extendedWith(pageId: PageIdData, f: () -> T): T =
      extendedWith("pageId", pageId.value, f)

    fun <T> extendedWith(synchronizedPostId: SynchronizedPostIdData, f: () -> T): T =
      extendedWith("synchronizedPostId", synchronizedPostId.value, f)
  }
}
