package dev.pankowski.garcon.domain

data class Version(val number: Int) {

  companion object {
    @JvmStatic
    fun first() = Version(1)
  }

  fun next(): Version = Version(number + 1)
}
