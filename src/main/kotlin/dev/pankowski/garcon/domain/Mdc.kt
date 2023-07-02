package dev.pankowski.garcon.domain

import org.slf4j.MDC

class Mdc {

  companion object {
    private fun <T> extendedWith(key: String, value: String, f: () -> T): T =
      try {
        MDC.put(key, value)
        f()
      } finally {
        MDC.remove(key)
      }

    fun <T> extendedWith(pageKey: PageKey, f: () -> T): T =
      extendedWith("pageKey", pageKey.value, f)

    fun <T> extendedWith(synchronizedPostId: SynchronizedPostId, f: () -> T): T =
      extendedWith("synchronizedPostId", synchronizedPostId.value, f)
  }
}
