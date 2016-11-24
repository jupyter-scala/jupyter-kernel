package jupyter.kernel.interpreter

import jupyter.kernel.protocol.Formats._
import argonaut._, Argonaut._, Shapeless._
import jupyter.kernel.protocol.ShellReply
import utest._

object JsonTests extends TestSuite {

  val tests = TestSuite {
    'reply - {
      'statusField - {
        val okReply = ShellReply.Execute(3, Map.empty)
        val errorReply = ShellReply.Error("name", "value", List("t1", "t2"))
        val abortReply = ShellReply.Abort()

        def statusOf(json: Json): Option[String] = {
          case class WithStatus(status: String)
          json.asJson.as[WithStatus].toOption.map(_.status)
        }

        val okStatusOpt = statusOf(okReply.asJson)
        val errorStatusOpt = statusOf(errorReply.asJson)
        val abortStatusOpt = statusOf(abortReply.asJson)

        assert(okStatusOpt == Some("ok"))
        assert(errorStatusOpt == Some("error"))
        assert(abortStatusOpt == Some("abort"))
      }
    }
  }

}
