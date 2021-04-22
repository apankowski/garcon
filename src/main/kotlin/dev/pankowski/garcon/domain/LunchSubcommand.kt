package dev.pankowski.garcon.domain

sealed class LunchSubcommand {

  object Help : LunchSubcommand()
  object Log : LunchSubcommand()
  object CheckForLunchPost : LunchSubcommand()
  object Unrecognized : LunchSubcommand()
}
