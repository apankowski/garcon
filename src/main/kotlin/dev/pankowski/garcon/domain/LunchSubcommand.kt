package dev.pankowski.garcon.domain

sealed class LunchSubcommand {

  data object Help : LunchSubcommand()

  data object Log : LunchSubcommand()

  data object CheckForLunchPost : LunchSubcommand()

  data object Unrecognized : LunchSubcommand()
}
