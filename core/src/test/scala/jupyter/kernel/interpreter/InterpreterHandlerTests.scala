package jupyter.kernel.interpreter

import argonaut.Json

import jupyter.kernel.Message
import jupyter.kernel.protocol._
import jupyter.kernel.protocol.Formats._

import scalaz.-\/
import utest._

object InterpreterHandlerTests extends TestSuite {
  import Helpers._

  val connectReply = randomConnectReply()
  def session(msgs: (Req[_], Seq[Reply])*) = Helpers.session(echoInterpreter(), Some("5.0"), connectReply)(msgs: _*)

  val tests = TestSuite{
    'malformedHeader{
      val response = InterpreterHandler(echoInterpreter(), randomConnectReply(), (_, _) => (), Message(Nil, "{", "", "{}", "{}"))
      assertMatch(response) {
        case -\/(_) =>
      }
    }

    'emptyRequest{
      // FIXME Provide a *real* header
      val response = InterpreterHandler(echoInterpreter(), randomConnectReply(), (_, _) => (), Message(Nil, "{}", "", "{}", "{}"))
      assertMatch(response) {
        case -\/(_) =>
      }
    }

    'connectReply{
      session(
        Req("connect_request", ShellRequest.Connect) -> Seq(
          ReqReply("connect_reply", connectReply)
        )
      )
    }

    'kernelInfo{
      session(
        Req("kernel_info_request", ShellRequest.KernelInfo) -> Seq(
          ReqReply("kernel_info_reply", ShellReply.KernelInfo("5.0", "", "", echoInterpreter().languageInfo, ""))
        )
      )
    }

    'execute{
      session(
        Req("execute_request", ShellRequest.Execute("meh", Map.empty, silent = Some(false), None, allow_stdin = Some(false), None)) -> Seq(
          PubReply(List("execute_input"), "execute_input", Publish.ExecuteInput("meh", 1)),
          PubReply(List("status"), "status", Publish.Status(Publish.ExecutionState0.Busy)),
          PubReply(List("execute_result"), "execute_result", Publish.ExecuteResult(1, Map("text/plain" -> Json.jString("meh")), Map.empty)),
          ReqReply("execute_reply", ShellReply.Execute(1, Map.empty)),
          PubReply(List("status"), "status", Publish.Status(Publish.ExecutionState0.Idle))
        )
      )
    }
  }

}
