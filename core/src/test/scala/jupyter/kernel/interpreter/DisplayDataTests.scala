package jupyter.kernel.interpreter

import argonaut.{Json, Parse}
import utest._

object DisplayDataTests extends TestSuite {
  val tests = TestSuite {

    'textplain - {
      val data = DisplayData("text/plain", "foo")
      val expectedField = "text/plain" -> Json.jString("foo")
      val field = data.jsonField

      assert(field == expectedField)
    }

    'applicationjson - {

      val someJson = Json.obj(
        "a" -> Json.jNumber(2),
        "b" -> Json.jBool(true),
        "c" -> Json.obj(
          "list" -> Json.array((1 to 3).map(Json.jNumber): _*)
        )
      )
      val someJsonStr = someJson.nospaces

      'default - {
        val mimeType = "application/json"

        val data = DisplayData(mimeType, someJsonStr)
        val expectedField = mimeType -> someJson
        val field = data.jsonField

        assert(field == expectedField)
      }

      'suffix - {
        val mimeType = "application/foo.bar+json"

        val data = DisplayData(mimeType, someJsonStr)
        val expectedField = mimeType -> someJson
        val field = data.jsonField

        assert(field == expectedField)
      }

      'stringFallback - {
        val mimeType = "application/json"

        val value = someJsonStr.dropRight(2)
        assert(Parse.parse(value).isLeft) // value should be invalid JSON

        val data = DisplayData(mimeType, value)
        val expectedField = mimeType -> Json.jString(value)
        val field = data.jsonField

        assert(field == expectedField)
      }
    }

  }
}
