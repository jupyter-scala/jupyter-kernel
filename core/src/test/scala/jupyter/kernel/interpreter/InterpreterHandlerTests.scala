package jupyter.kernel.interpreter

import jupyter.kernel.Message
import jupyter.kernel.protocol.Input.{ ExecuteRequest, KernelInfoRequest, ConnectRequest }
import jupyter.kernel.protocol.Output.{ Error => _, _ }
import jupyter.kernel.protocol._
import jupyter.kernel.protocol.Formats.{ inputConnectRequestEncodeJson, inputKernelInfoRequestEncodeJson, inputExecuteRequestEncodeJson }

import scalaz.-\/

import utest._

object InterpreterHandlerTests extends TestSuite {
  import Helpers._

  val connectReply = randomConnectReply()
  def session(msgs: (Req[_], Seq[Reply])*) = Helpers.session(echoInterpreter(), Some("5.0"), connectReply)(msgs: _*)

  val tests = TestSuite{
    'malformedHeader{
      val response = InterpreterHandler(echoInterpreter(), randomConnectReply(), (_, _) => (), Message(Nil, "{", "", "{}", "{}")).runLog.run.map(parse(_))
      assertMatch(response) {
        case IndexedSeq(-\/(_)) =>
      }
    }

    'emptyRequest{
      // FIXME Provide a *real* header
      val response = InterpreterHandler(echoInterpreter(), randomConnectReply(), (_, _) => (), Message(Nil, "{}", "", "{}", "{}")).runLog.run.map(parse(_))
      assertMatch(response) {
        case IndexedSeq(-\/(_)) =>
      }
    }

    'connectReply{
      session(
        Req("connect_request", ConnectRequest()) -> Seq(
          ReqReply("connect_reply", connectReply)
        )
      )
    }

    'kernelInfo{
      session(
        Req("kernel_info_request", KernelInfoRequest()) -> Seq(
          ReqReply("kernel_info_reply", KernelInfoReply("5.0", echoInterpreter().languageInfo))
        )
      )
    }

    'execute{
      session(
        Req("execute_request", ExecuteRequest("meh", silent = false, None, Map.empty, allow_stdin = false)) -> Seq(
          PubReply(List("execute_input"), "execute_input", ExecuteInput("meh", 1)),
          PubReply(List("status"), "status", Output.Status(ExecutionState.busy)),
          PubReply(List("execute_result"), "execute_result", ExecuteResult(1, Map("text/plain" -> "meh"))),
          ReqReply("execute_reply", ExecuteOkReply(1)),
          PubReply(List("status"), "status", Output.Status(ExecutionState.idle))
        )
      )
    }
  }

}
