package dev.pankowski.garcon.api.rest

import dev.pankowski.garcon.domain.ChannelId
import dev.pankowski.garcon.domain.EnterpriseId
import dev.pankowski.garcon.domain.SlashCommand
import dev.pankowski.garcon.domain.TeamId
import dev.pankowski.garcon.domain.TriggerId
import dev.pankowski.garcon.domain.UserId
import org.springframework.core.MethodParameter
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.web.bind.MissingServletRequestParameterException
import org.springframework.web.bind.ServletRequestBindingException
import org.springframework.web.context.request.ServletWebRequest
import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Unroll

class SlashCommandMethodArgumentResolverTest extends Specification {

  @Subject
  SlashCommandMethodArgumentResolver resolver = new SlashCommandMethodArgumentResolver()

  // This method is here so that Spring can introspect its parameters.
  @SuppressWarnings('unused')
  def introspectionTarget(SlashCommand p0, String p1, Integer p2, Object p3, ArrayList p4) {
  }

  def testMethodParameter(int index) {
    def method = getClass().getDeclaredMethods().find { it.name == 'introspectionTarget' }
    assert method != null

    return new MethodParameter(method, index)
  }

  @Unroll
  def "should answer #answer to supporting argument of type #clazz"() {
    given:
    def parameter = testMethodParameter(index)

    expect:
    resolver.supportsParameter(parameter) == answer

    where:
    index | clazz          | answer
    0     | 'SlashCommand' | true
    1     | 'String'       | false
    2     | 'Integer'      | false
    3     | 'Object'       | false
    4     | 'ArrayList'    | false
  }

  def someMockRequestWithAllParameters() {
    def mockRequest = new MockHttpServletRequest()
    mockRequest.setParameter('command', '/command')
    mockRequest.setParameter('text', 'text')
    mockRequest.setParameter('response_url', 'https://www.slack.com/response')
    mockRequest.setParameter('trigger_id', 'T1234')
    mockRequest.setParameter('user_id', 'U1234')
    mockRequest.setParameter('channel_id', 'C1234')
    mockRequest.setParameter('team_id', 'T1234')
    mockRequest.setParameter('enterprise_id', 'E1234')
    return mockRequest
  }

  @Unroll
  def "should require '#param' request parameter of type #type"() {
    given:
    def methodParam = testMethodParameter(0)

    and:
    def mockRequest = someMockRequestWithAllParameters()
    mockRequest.removeParameter(param)

    def request = new ServletWebRequest(mockRequest)

    when:
    resolver.resolveArgument(methodParam, null, request, null)

    then:
    thrown MissingServletRequestParameterException

    where:
    param        | type
    'command'    | 'string'
    'text'       | 'string'
    'user_id'    | 'user ID'
    'channel_id' | 'channel ID'
  }

  @Unroll
  def "should not require '#param' request parameter"() {
    given:
    def methodParam = testMethodParameter(0)

    and:
    def mockRequest = someMockRequestWithAllParameters()
    mockRequest.removeParameter(param)

    def request = new ServletWebRequest(mockRequest)

    when:
    resolver.resolveArgument(methodParam, null, request, null)

    then:
    notThrown MissingServletRequestParameterException

    where:
    param << ['response_url', 'trigger_id', 'team_id', 'enterprise_id']
  }

  @Unroll
  def "should fail on '#param' request parameter having value '#value'"() {
    given:
    def parameter = testMethodParameter(0)

    and:
    def mockRequest = someMockRequestWithAllParameters()
    mockRequest.setParameter(param, value)

    def request = new ServletWebRequest(mockRequest)

    when:
    resolver.resolveArgument(parameter, null, request, null)

    then:
    thrown ServletRequestBindingException

    where:
    param          | value
    'response_url' | 'not a url'
    'response_url' | '1'
  }

  def 'should transform request params into slash command'() {
    given:
    def parameter = testMethodParameter(0)

    and:
    def mockRequest = someMockRequestWithAllParameters()
    def request = new ServletWebRequest(mockRequest)

    when:
    def command = resolver.resolveArgument(parameter, null, request, null)

    then:
    command == new SlashCommand(
      '/command',
      'text',
      new URL('https://www.slack.com/response'),
      new TriggerId('T1234'),
      new UserId('U1234'),
      new ChannelId('C1234'),
      new TeamId('T1234'),
      new EnterpriseId('E1234')
    )
  }
}
