package dev.pankowski.garcon.api

import dev.pankowski.garcon.domain.LunchSubcommand
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FreeSpec
import io.kotest.core.spec.style.scopes.ContainerScope
import io.kotest.datatest.withData
import io.kotest.matchers.shouldBe

class LunchSubcommandParserTest : FreeSpec({

  val parser = LunchSubcommandParser()

  suspend fun ContainerScope.verifyParsing(subcommand: LunchSubcommand, vararg texts: String) =
    withData<String>({ "text '$it' is parsed as $subcommand" }, texts.toList()) {
      // given
      val command = someLunchCommand(text = it)

      // expect
      parser.parse(command) shouldBe subcommand
    }

  "fails for wrong command" {
    // given
    val command = someLunchCommand(command = "/wrong-command")

    // expect
    shouldThrow<WrongCommandException> {
      parser.parse(command)
    }
  }

  "handles commands with no words" - {
    verifyParsing(LunchSubcommand.CheckForLunchPost, "", " ", "\t", "\n", ".", "  ,@% ")
  }

  "handles 'help' commands" - {
    verifyParsing(LunchSubcommand.Help, "help", "  Help  ", "help!", "Hlep", "hlp.")
  }

  "handles 'log' commands" - {
    verifyParsing(LunchSubcommand.Log, "log", "  Log  ", "log!", "lgo", ".og")
  }

  "handles 'check' commands" - {
    verifyParsing(LunchSubcommand.CheckForLunchPost, "check", "  Check  ", "check!", "Chcek", "chk.")
  }

  "handles unrecognized commands" - {
    verifyParsing(
      LunchSubcommand.Unrecognized,
      "l",
      "He",
      "hle",
      "no go!",
      "What?",
      "log something",
      "check something",
      "help something"
    )
  }
})
