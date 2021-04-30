package dev.pankowski.garcon.api

import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.ints.beGreaterThanOrEqualTo
import io.kotest.matchers.shouldBe
import org.springframework.core.MethodParameter
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.web.bind.MissingServletRequestParameterException
import org.springframework.web.bind.ServletRequestBindingException
import org.springframework.web.context.request.ServletWebRequest
import java.net.URL
import kotlin.reflect.KClass
import kotlin.reflect.jvm.javaMethod

// This method is here so that Spring can introspect its parameters.
// It can't be inside FreeSpec as Kotlin doesn't yet handle reflection on local functions.
@Suppress("unused", "unused_parameter")
fun introspectionTarget(p0: SlashCommand, p1: String, p2: Int, p3: Any, p4: ArrayList<String>) = Unit

class SlashCommandMethodArgumentResolverTest : FreeSpec({

  val resolver = SlashCommandMethodArgumentResolver()

  fun someMethodParameter(clazz: KClass<*>): MethodParameter {
    val targetMethod = ::introspectionTarget.javaMethod!!
    val parameterIndex = targetMethod.parameters.indexOfFirst { it.type == clazz.java }
    withClue("Introspection method doesn't have a parameter of type ${clazz.simpleName}") {
      parameterIndex shouldBe beGreaterThanOrEqualTo(0)
    }
    return MethodParameter(targetMethod, parameterIndex)
  }

  "supported types" - {
    listOf(
      SlashCommand::class to true,
      String::class to false,
      Int::class to false,
      Any::class to false,
      ArrayList::class to false,
    ).forEach { (type, isSupported) ->
      "${if (isSupported) "supports" else "doesn't support"} argument of type ${type.simpleName}" {
        // given
        val parameter = someMethodParameter(type)

        // expect
        resolver.supportsParameter(parameter) shouldBe isSupported
      }
    }
  }

  fun someMockRequestWithAllParameters(
    command: String = "/command",
    text: String = "text",
    responseUrl: String = "https://www.slack.com/response",
    triggerId: String = "T1234",
    userId: String = "U1234",
    channelId: String = "C1234",
    teamId: String = "T1234",
    enterpriseId: String = "E1234",
  ) =
    MockHttpServletRequest().apply {
      setParameter("command", command)
      setParameter("text", text)
      setParameter("response_url", responseUrl)
      setParameter("trigger_id", triggerId)
      setParameter("user_id", userId)
      setParameter("channel_id", channelId)
      setParameter("team_id", teamId)
      setParameter("enterprise_id", enterpriseId)
    }

  "required request params" - {
    listOf("command", "user_id", "channel_id").forEach { requestParam ->
      "request param '$requestParam' is required" {
        // given
        val methodParameter = someMethodParameter(SlashCommand::class)
        val mockRequest = someMockRequestWithAllParameters()
        mockRequest.removeParameter(requestParam)

        // expect
        shouldThrow<MissingServletRequestParameterException> {
          resolver.resolveArgument(methodParameter, null, ServletWebRequest(mockRequest), null)
        }
      }
    }
  }

  "non-required request params" - {
    listOf("text", "response_url", "trigger_id", "team_id", "enterprise_id").forEach { requestParam ->
      "request param '$requestParam' is not required" {
        // given
        val methodParameter = someMethodParameter(SlashCommand::class)
        val mockRequest = someMockRequestWithAllParameters()
        mockRequest.removeParameter(requestParam)

        // expect
        shouldNotThrowAny {
          resolver.resolveArgument(methodParameter, null, ServletWebRequest(mockRequest), null)
        }
      }
    }
  }

  "invalid request param values" - {
    listOf(
      "response_url" to "not a url",
      "response_url" to "1",
    ).forEach { (requestParam, value) ->
      "request param '$requestParam' = '$value' is invalid" {
        // given
        val methodParameter = someMethodParameter(SlashCommand::class)
        val mockRequest = someMockRequestWithAllParameters()
        mockRequest.setParameter(requestParam, value)

        // expect
        shouldThrow<ServletRequestBindingException> {
          resolver.resolveArgument(methodParameter, null, ServletWebRequest(mockRequest), null)
        }
      }
    }
  }

  "transforms request params into slash command" {
    // given
    val methodParameter = someMethodParameter(SlashCommand::class)
    val mockRequest = someMockRequestWithAllParameters(
      command = "/command",
      text = "some text",
      responseUrl = "https://www.slack.com/some-response-link",
      triggerId = "some trigger id",
      userId = "some user id",
      channelId = "some channel id",
      teamId = "some team id",
      enterpriseId = "some enterprise id",
    )

    // when
    val command = resolver.resolveArgument(methodParameter, null, ServletWebRequest(mockRequest), null)

    // then
    command shouldBe SlashCommand(
      "/command",
      "some text",
      URL("https://www.slack.com/some-response-link"),
      TriggerId("some trigger id"),
      UserId("some user id"),
      ChannelId("some channel id"),
      TeamId("some team id"),
      EnterpriseId("some enterprise id"),
    )
  }
})
