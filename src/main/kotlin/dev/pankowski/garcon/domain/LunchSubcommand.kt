package dev.pankowski.garcon.domain

sealed class LunchSubcommand {

  object Help : LunchSubcommand() {
    override fun toString() = "Help"
  }

  object Log : LunchSubcommand() {
    override fun toString() = "Log"
  }

  object CheckForLunchPost : LunchSubcommand() {
    override fun toString() = "CheckForLunchPost"
  }

  object Unrecognized : LunchSubcommand() {
    override fun toString() = "Unrecognized"
  }
}
