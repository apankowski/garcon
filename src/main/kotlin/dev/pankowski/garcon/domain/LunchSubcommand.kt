package dev.pankowski.garcon.domain

sealed class LunchSubcommand {

  object Help : LunchSubcommand()
  object Log : LunchSubcommand()
  object CheckForLunchPost : LunchSubcommand()
  data class Unrecognized(val words: List<String>) : LunchSubcommand()
}
