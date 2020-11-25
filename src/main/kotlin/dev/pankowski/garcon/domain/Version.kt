package dev.pankowski.garcon.domain

data class Version(val value: Int) {
  companion object {
    @JvmStatic
    fun first() = Version(1)
  }

  fun next(): Version = Version(value + 1)
}
