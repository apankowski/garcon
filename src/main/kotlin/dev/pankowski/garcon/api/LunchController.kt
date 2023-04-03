package dev.pankowski.garcon.api

import dev.pankowski.garcon.domain.*
import org.slf4j.LoggerFactory
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestController
import java.net.URL
import java.time.Instant
import java.util.concurrent.Executor
import jakarta.servlet.http.HttpServletResponse
import jakarta.servlet.http.HttpServletResponse.SC_BAD_REQUEST

@RestController
class LunchController(
  private val subcommandParser: LunchSubcommandParser,
  private val taskScheduler: Executor,
  private val service: LunchService,
) {

  private val log = LoggerFactory.getLogger(javaClass)

  @SlashCommandMapping("/commands/lunch")
  fun handle(command: SlashCommand): SlackMessage {
    log.info("Received command {}", command)
    return when (subcommandParser.parse(command)) {
      is LunchSubcommand.Help -> handleHelp()
      is LunchSubcommand.Unrecognized -> handleUnrecognized(command)
      is LunchSubcommand.CheckForLunchPost -> handleCheckForLunchPost()
      is LunchSubcommand.Log -> handleLog()
    }
  }

  @ExceptionHandler(WrongCommandException::class)
  fun wrongCommand(response: HttpServletResponse) {
    response.sendError(SC_BAD_REQUEST)
  }

  private fun handleHelp() =
    SlackMessage(
      """
      |Recognized subcommands are:
      |• `/lunch` or `/lunch check` - manually triggers checking for lunch post
      |• `/lunch status` - displays status of lunch feature
      |• `/lunch help` - displays this message
      """.trimMargin(),
      ResponseType.EPHEMERAL
    )

  private fun handleUnrecognized(command: SlashCommand) =
    SlackMessage(
      """
      |Unrecognized subcommand: `/lunch ${command.text}`
      |
      |${handleHelp().text}
      """.trimMargin(),
      ResponseType.EPHEMERAL
    )

  private fun handleCheckForLunchPost() =
    try {
      // TODO: Post summary of synchronization?
      taskScheduler.execute(service::synchronizeAll)
      SlackMessage("Checking...", ResponseType.EPHEMERAL)
    } catch (e: Exception) {
      log.error("Failed to schedule checking for lunch posts", e)
      SlackMessage("Error while scheduling synchronization :frowning:", ResponseType.EPHEMERAL)
    }

  private fun handleLog(): SlackMessage {

    fun Instant.toSlackDate(linkUrl: URL? = null) =
      if (linkUrl == null) "<!date^${epochSecond}^{date_num} {time}|${this}>"
      else "<!date^${epochSecond}^{date_num} {time}^${linkUrl}|${this}>"

    fun classificationInfo(c: Classification) =
      when (c) {
        Classification.REGULAR_POST -> ":heavy_minus_sign:"
        Classification.LUNCH_POST -> ":heavy_check_mark:"
      }

    fun repostInfo(r: Repost) =
      when (r) {
        is Repost.Skip -> ":heavy_minus_sign:"
        is Repost.Pending -> ":heavy_plus_sign:"
        is Repost.Success -> ":heavy_check_mark: ${r.repostedAt.toSlackDate()}"
        is Repost.Failed -> "${r.attempts}:heavy_multiplication_x: last attempt at ${r.lastAttemptAt.toSlackDate()}"
      }

    fun buildItem(p: SynchronizedPost) =
      """
      |• *${p.post.publishedAt.toSlackDate(linkUrl = p.post.url)}* from *${p.pageName.value}*
      |Preview: ${p.post.content.oneLinePreview(120)}
      |Lunch post: ${classificationInfo(p.classification)}
      |Repost: ${repostInfo(p.repost)}
      |
      """.trimMargin()

    fun buildLog(posts: SynchronizedPosts): String {
      return when (val lastSeenPublishedAt = posts.firstOrNull()?.post?.publishedAt) {
        null -> "No posts seen so far"
        else ->
          """
          |Last post seen *${lastSeenPublishedAt.toSlackDate()}* (timestamp `${lastSeenPublishedAt.epochSecond}`)
          |
          |Last synchronized posts:
          |
          |${posts.joinToString(separator = "\n", transform = ::buildItem)}
          """.trimMargin()
      }
    }

    // Note there is a 40k char limit on the message text, see:
    // https://api.slack.com/changelog/2018-04-truncating-really-long-messages
    // but we shouldn't hit it in any case.
    return SlackMessage(
      buildLog(service.getLog()),
      ResponseType.EPHEMERAL
    )
  }
}
