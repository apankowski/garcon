package dev.pankowski.garcon.domain

import java.net.URI

fun toURL(string: String) = URI(string).toURL()
