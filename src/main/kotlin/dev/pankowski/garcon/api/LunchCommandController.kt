package dev.pankowski.garcon.api

import dev.pankowski.garcon.domain.*
import org.slf4j.LoggerFactory
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestController
import java.net.URI
import java.time.Instant
import java.util.concurrent.Executor
import javax.servlet.http.HttpServletResponse
import javax.servlet.http.HttpServletResponse.SC_BAD_REQUEST

@RestController
class LunchCommandController(
  private val subcommandParser: LunchSubcommandParser,
  private val taskScheduler: Executor,
  private val synchronizer: LunchSynchronizer,
) {

  private val log = LoggerFactory.getLogger(javaClass)

  @SlashCommandMapping("/commands/lunch")
  fun handle(command: SlashCommand): MessagePayload {
    log.info("Received command {}", command)
    return when (val subcommand = subcommandParser.parse(command)) {
      is LunchSubcommand.Help -> handleHelp()
      is LunchSubcommand.Unrecognized -> handleUnrecognized(subcommand)
      is LunchSubcommand.CheckForLunchPost -> handleCheckForLunchPost()
      is LunchSubcommand.Log -> handleLog()
    }
  }

  @ExceptionHandler(WrongCommandException::class)
  fun wrongCommand(response: HttpServletResponse) {
    response.sendError(SC_BAD_REQUEST)
  }

  private fun handleHelp() =
    MessagePayload(
      """
      |Recognized subcommands are:
      |• `/lunch` or `/lunch check` - manually triggers checking for lunch post
      |• `/lunch status` - displays status of lunch feature
      |• `/lunch help` - displays this message
      """.trimMargin(),
      ResponseType.EPHEMERAL
    )

  private fun handleUnrecognized(unrecognized: LunchSubcommand.Unrecognized) =
    MessagePayload(
      """
      |Unrecognized subcommand: `/lunch ${unrecognized.words.joinToString(separator = " ")}`
      |
      |${handleHelp().text}
      """.trimMargin(),
      ResponseType.EPHEMERAL
    )

  private fun handleCheckForLunchPost() =
    try {
      // TODO: Reply with responseId from command?
      taskScheduler.execute(synchronizer::synchronizeAll)
      MessagePayload( "Checking...", ResponseType.EPHEMERAL)
    } catch (e: Exception) {
      log.error("Failed to schedule checking for lunch posts", e)
      MessagePayload("Error while scheduling synchronization :frowning:", ResponseType.EPHEMERAL)
    }

  private fun handleLog(): MessagePayload {
    fun contentPreview(content: String, ellipsizeAt: Int): String {
      val oneLine = content.split("\\s+".toRegex()).joinToString(" ")
      return when {
        oneLine.length <= ellipsizeAt -> oneLine
        else -> oneLine.substring(0, ellipsizeAt) + Typography.ellipsis
      }
    }

    fun Instant.toSlackDate(link: URI? = null) =
      if (link == null) "<!date^${epochSecond}^{date_num} {time}|${this}>"
      else "<!date^${epochSecond}^{date_num} {time}^${link}|${this}>"

    fun classificationInfo(c: Classification) =
      when (c) {
        is Classification.MissingKeywords -> ":heavy_minus_sign:"
        is Classification.LunchPost -> ":heavy_check_mark:"
      }

    fun repostInfo(r: Repost) =
      when (r) {
        is Repost.Skip -> ":heavy_minus_sign:"
        is Repost.Pending -> ":heavy_plus_sign:"
        is Repost.Success -> ":heavy_check_mark: ${r.repostedAt.toSlackDate()}"
        is Repost.Error -> "${r.errorCount}:heavy_multiplication_x: last attempt at ${r.lastAttemptAt.toSlackDate()}"
      }

    fun buildItem(p: SynchronizedPost) =
      """
      |• *${p.post.publishedAt.toSlackDate(link = p.post.link)}*
      |Preview: ${contentPreview(p.post.content, 120)}
      |Lunch post: ${classificationInfo(p.classification)}
      |Repost: ${repostInfo(p.repost)}
      |
      """.trimMargin()

    fun buildLog(logItems: SynchronizationLog): String {
      return when (val lastSeenPublishedAt = logItems.firstOrNull()?.post?.publishedAt) {
        null -> "No posts seen so far"
        else ->
          """
          |Last post seen *${lastSeenPublishedAt.toSlackDate()}* (timestamp `${lastSeenPublishedAt.epochSecond}`)
          |
          |Last synchronized posts:
          |
          |${logItems.joinToString(separator = "\n", transform = ::buildItem)}
          """.trimMargin()
      }
    }

    // Note there is a 40k char limit on the message text, see:
    // https://api.slack.com/changelog/2018-04-truncating-really-long-messages
    // but we shouldn't hit it in any case.
    return MessagePayload(
      buildLog(synchronizer.getLog()),
      ResponseType.EPHEMERAL
    )
  }
}
