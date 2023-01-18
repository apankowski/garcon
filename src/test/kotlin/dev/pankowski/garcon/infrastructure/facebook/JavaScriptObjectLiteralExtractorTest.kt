package dev.pankowski.garcon.infrastructure.facebook

import com.fasterxml.jackson.databind.json.JsonMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import io.kotest.core.spec.style.FreeSpec
import io.kotest.datatest.withData
import io.kotest.matchers.shouldBe

class JavaScriptObjectLiteralExtractorTest : FreeSpec({

  val extractor = JavaScriptObjectLiteralExtractor
  val mapper = JsonMapper()

  data class TestCase(
    val script: String,
    val outputs: List<ObjectNode>,
  ) {
    constructor(script: String, vararg objects: String) :
      this(script, objects.toList().map { mapper.readValue(it, ObjectNode::class.java) })

    val scriptPreview = script.replace("\n+[\t ]*".toRegex(), "").take(80)
  }

  "test case preview removes indents and newlines" {
    val testCase = TestCase(
      """
      a
        b
          c

          d
        e
        f
      g
      """.trimIndent(), emptyList()
    )

    testCase.scriptPreview shouldBe "abcdefg"
  }

  "extracts object literals from JavaScript" - {
    withData<TestCase>(
      { "extracts object literals from '${it.scriptPreview}'" },
      TestCase(
        """{"some":"object"}""",
        """{"some":"object"}""",
      ),
      TestCase(
        """[{"some":"object"},{"another":"one"}]""",
        """{"some":"object"}""",
        """{"another":"one"}""",
      ),
      TestCase(
        """object.call(1,{"some":"object"},true,[another.call({"another":"one"})])""",
        """{"a":"b"}""",
        """{"another":"one"}""",
      ),
      TestCase(
        """
        call(["a"],function(m){m.handle({"some":{"object":true}})});
        call(["b"],function(c){c.register(["a","b"])});
        call(["c"],function(b){
          b.load(["x"],{
            on:function(){
              require(["a","b","c"],function(a,b,c){
                a.run(3,function(){
                  (new b()).handle(c,{"another":[["one",[],{"x":"y"}]]});
                });
              });
            }
          })
        });
        """.trimIndent(),
        """{"some":{"object":true}}""",
        """{"another":[["one",[],{"x":"y"}]]}""",
      ),
      TestCase(
        """abc""",
      ),
      TestCase(
        """call(["a"],function(m){m.handle({"some":{"object":true""",
      ),
    ) { testCase ->

      // given
      val result = extractor.extract(testCase.script)

      // expect
      result shouldBe testCase.outputs
    }
  }
})
